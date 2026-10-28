package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.wallpaperfit.app.model.ContentEdits
import com.wallpaperfit.app.model.CropRect
import com.wallpaperfit.app.model.ExpandFill
import com.wallpaperfit.app.model.Expansion
import com.wallpaperfit.app.model.FillDetail
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.HealStroke
import com.wallpaperfit.app.model.Side
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Relación entre la foto original y la imagen base (foto recortada y expandida, a escala [scale]).
 * Un punto (x, y) de la foto original está en (x * scale + offsetX, y * scale + offsetY) de la base.
 */
data class BaseGeometry(
    val width: Int,
    val height: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    companion object {
        fun of(originalWidth: Int, originalHeight: Int, crop: CropRect, expansion: Expansion, maxSide: Int): BaseGeometry {
            val croppedWidth = croppedSize(originalWidth, crop.left, crop.right)
            val croppedHeight = croppedSize(originalHeight, crop.top, crop.bottom)
            val expandedWidth = expansion.expandedWidth(croppedWidth)
            val expandedHeight = expansion.expandedHeight(croppedHeight)
            val scale = min(1f, maxSide / max(expandedWidth, expandedHeight))
            return BaseGeometry(
                width = (expandedWidth * scale).roundToInt().coerceAtLeast(1),
                height = (expandedHeight * scale).roundToInt().coerceAtLeast(1),
                scale = scale,
                offsetX = (expansion.left * croppedWidth - cropStart(originalWidth, crop.left)) * scale,
                offsetY = (expansion.top * croppedHeight - cropStart(originalHeight, crop.top)) * scale,
            )
        }

        /** Primer píxel del recorte y tamaño en píxeles, redondeados igual que al recortar. */
        fun cropStart(size: Int, start: Float): Int = (start * size).roundToInt().coerceIn(0, size - 1)

        fun croppedSize(size: Int, start: Float, end: Float): Int {
            val first = cropStart(size, start)
            return ((end * size).roundToInt() - first).coerceIn(1, size - first)
        }
    }
}

/**
 * Construye la imagen base: foto original → Expandir → Corrector.
 * Cachea el resultado de la expansión y de los trazos ya aplicados, de modo que añadir un trazo
 * solo cuesta corregir esa zona. El Corrector puede usar la IA local ([AiInpainter]).
 */
class ContentProcessor(ai: AiInpainter) {

    /**
     * Expansión de una foto y, encima, los últimos resultados del Corrector sobre ella (uno por paso,
     * el más reciente primero): deshacer o rehacer un trazo reciente es inmediato, sin recalcular.
     */
    private class ExpandedEntry(val original: Bitmap, val crop: CropRect, val expansion: Expansion, val bitmap: Bitmap) {
        val healed = ArrayDeque<HealedEntry>()
    }

    private class HealedEntry(val heals: List<HealStroke>, val bitmap: Bitmap)

    private val aiFill = AiFill(ai)

    // Un resultado por imagen de partida (la foto y, como mucho, otra con capas combinadas): así
    // deshacer o rehacer una combinación no repite el relleno inteligente ni los retoques.
    private val caches = ArrayDeque<ExpandedEntry>()

    fun geometry(original: Bitmap, content: ContentEdits): BaseGeometry =
        BaseGeometry.of(original.width, original.height, content.crop, content.expansion, MAX_BASE_SIDE)

    @Synchronized
    fun buildBase(original: Bitmap, content: ContentEdits, checkCancelled: () -> Unit): Bitmap {
        val expanded = caches.firstOrNull {
            it.original === original && it.crop == content.crop && it.expansion == content.expansion
        }
            ?: ExpandedEntry(
                original,
                content.crop,
                content.expansion,
                expand(original, content.crop, content.expansion, MAX_BASE_SIDE, SMART_FILL_WORK_SIDE, checkCancelled),
            ).also { entry ->
                // Sustituye al de la misma foto; se guarda como mucho uno por foto.
                caches.removeAll { it.original === original }
                caches.addFirst(entry)
                while (caches.size > MAX_CACHED_PHOTOS) caches.removeLast()
            }
        if (content.heals.isEmpty()) return expanded.bitmap

        // El paso guardado más avanzado que coincide con el principio de los trazos pedidos.
        val cached = expanded.healed
            .filter { it.heals.size <= content.heals.size && content.heals.subList(0, it.heals.size) == it.heals }
            .maxByOrNull { it.heals.size }
        if (cached != null && cached.heals.size == content.heals.size) {
            expanded.healed.remove(cached)
            expanded.healed.addFirst(cached)
            return cached.bitmap
        }

        val start = cached?.heals?.size ?: 0
        val base = (cached?.bitmap ?: expanded.bitmap).copy(Bitmap.Config.ARGB_8888, true)
        val geometry = geometry(original, content)
        for (i in start until content.heals.size) {
            checkCancelled()
            heal(base, content.heals[i], geometry, checkCancelled)
        }
        expanded.healed.removeAll { it.heals == content.heals }
        expanded.healed.addFirst(HealedEntry(content.heals, base))
        trimHealSteps(expanded.healed)
        return base
    }

    /** Pasos guardados del Corrector: unos cuantos, sin pasarse de memoria. */
    private fun trimHealSteps(steps: ArrayDeque<HealedEntry>) {
        var bytes = 0L
        var kept = 0
        val iterator = steps.iterator()
        while (iterator.hasNext()) {
            val step = iterator.next()
            bytes += step.bitmap.byteCount
            kept++
            if (kept > MAX_HEAL_STEPS || (kept > 1 && bytes > MAX_HEAL_STEPS_BYTES)) iterator.remove()
        }
    }

    /** Vista previa rápida de Expandir sobre una versión reducida de la foto. */
    fun expandDraft(originalDraft: Bitmap, crop: CropRect, expansion: Expansion, checkCancelled: () -> Unit): Bitmap =
        expand(originalDraft, crop, expansion, DRAFT_SIDE, DRAFT_SIDE, checkCancelled)

    /** La parte de [photo] que deja el recorte (la propia foto si no hay recorte). */
    private fun cropped(photo: Bitmap, crop: CropRect): Bitmap {
        if (crop.isFull) return photo
        val x = BaseGeometry.cropStart(photo.width, crop.left)
        val y = BaseGeometry.cropStart(photo.height, crop.top)
        val w = BaseGeometry.croppedSize(photo.width, crop.left, crop.right)
        val h = BaseGeometry.croppedSize(photo.height, crop.top, crop.bottom)
        return Bitmap.createBitmap(photo, x, y, w, h)
    }

    @Synchronized
    fun clear() {
        caches.clear()
    }

    // region Expandir

    private fun expand(
        photo: Bitmap,
        crop: CropRect,
        expansion: Expansion,
        maxSide: Int,
        smartWorkSide: Int,
        checkCancelled: () -> Unit,
    ): Bitmap {
        val original = cropped(photo, crop)
        val geometry = BaseGeometry.of(original.width, original.height, CropRect(), expansion, maxSide)
        val base = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(base)
        val photoRect = RectF(
            geometry.offsetX,
            geometry.offsetY,
            geometry.offsetX + original.width * geometry.scale,
            geometry.offsetY + original.height * geometry.scale,
        )
        if (expansion.isNone) {
            BitmapScaler.drawScaled(canvas, original, RectF(0f, 0f, geometry.width.toFloat(), geometry.height.toFloat()), paint)
            return base
        }

        when (expansion.fill) {
            ExpandFill.WHITE -> canvas.drawColor(Color.WHITE)
            ExpandFill.BLACK -> canvas.drawColor(Color.BLACK)
            ExpandFill.SMART -> Unit
        }
        BitmapScaler.drawScaled(canvas, original, photoRect, paint)
        if (expansion.fill != ExpandFill.SMART) return base

        // Relleno inteligente: estructura calculada a resolución de trabajo, detalle copiado de
        // la foto a resolución completa. Los píxeles mezclados con el área vacía también se regeneran.
        val pixels = IntArray(geometry.width * geometry.height)
        base.getPixels(pixels, 0, geometry.width, 0, 0, geometry.width, geometry.height)
        val hole = BooleanArray(pixels.size) { (pixels[it] ushr 24) < 255 }
        // La foto como fracción del lienzo: el relleno prefiere continuar la parte más cercana y,
        // lejos de la foto, fondo en vez de formas repetidas.
        val prior = Inpainter.ExpandPrior(
            photoRect.left / geometry.width,
            photoRect.top / geometry.height,
            photoRect.right / geometry.width,
            photoRect.bottom / geometry.height,
        )
        val filled = if (expansion.detail == FillDetail.TEXTURE) {
            val box = TextureSynth.Box(
                ceil(photoRect.left).toInt().coerceIn(0, geometry.width),
                ceil(photoRect.top).toInt().coerceIn(0, geometry.height),
                floor(photoRect.right).toInt().coerceIn(0, geometry.width),
                floor(photoRect.bottom).toInt().coerceIn(0, geometry.height),
            )
            // Ya sale del borde de la foto: no hay unión que armonizar.
            val feather = max(2f, min(photoRect.width(), photoRect.height()) * FEATHER_FRACTION * TEXTURE_FEATHER).toInt()
            TextureSynth.fill(pixels, geometry.width, geometry.height, hole, box, feather, checkCancelled)
        } else {
            DetailFill.fill(pixels, geometry.width, geometry.height, hole, smartWorkSide, DetailFill.Mode.CLEAN, prior, checkCancelled)
                .also { SeamHarmonizer.harmonize(it, geometry.width, geometry.height, hole) }
        }

        base.recycle()
        val result = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        result.setPixels(filled, 0, geometry.width, 0, 0, geometry.width, geometry.height)
        // Con textura la foto y el relleno no comparten detalle: necesitan una unión más ancha.
        val feather = if (expansion.detail == FillDetail.TEXTURE) FEATHER_FRACTION * TEXTURE_FEATHER else FEATHER_FRACTION
        drawFeathered(Canvas(result), original, photoRect, expansion, feather)
        result.setHasAlpha(false)
        return result
    }

    /**
     * Dibuja la foto con sus bordes expandidos fundidos hacia el relleno, para que no se note la
     * unión. Los lados que no se han expandido quedan nítidos.
     */
    private fun drawFeathered(
        canvas: Canvas,
        original: Bitmap,
        photoRect: RectF,
        expansion: Expansion,
        featherFraction: Float = FEATHER_FRACTION,
    ) {
        val feather = max(2f, min(photoRect.width(), photoRect.height()) * featherFraction)
        val layer = canvas.saveLayer(photoRect, null)
        BitmapScaler.drawScaled(canvas, original, photoRect, paint)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        for (side in Side.entries) {
            if (expansion[side] <= 0f) continue
            val (x0, y0, x1, y1) = when (side) {
                Side.LEFT -> floatArrayOf(photoRect.left, 0f, photoRect.left + feather, 0f)
                Side.RIGHT -> floatArrayOf(photoRect.right, 0f, photoRect.right - feather, 0f)
                Side.TOP -> floatArrayOf(0f, photoRect.top, 0f, photoRect.top + feather)
                Side.BOTTOM -> floatArrayOf(0f, photoRect.bottom, 0f, photoRect.bottom - feather)
            }
            mask.shader = LinearGradient(x0, y0, x1, y1, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            canvas.drawRect(photoRect, mask)
        }
        canvas.restoreToCount(layer)
    }

    // endregion

    // region Corrector

    /** Rellena la zona pintada por [stroke] usando la textura de su entorno. Modifica [base]. */
    private fun heal(base: Bitmap, stroke: HealStroke, geometry: BaseGeometry, checkCancelled: () -> Unit) {
        if (stroke.points.size < 2) return
        val radius = max(1f, stroke.radius * geometry.scale)
        val xs = FloatArray(stroke.points.size / 2) { stroke.points[it * 2] * geometry.scale + geometry.offsetX }
        val ys = FloatArray(stroke.points.size / 2) { stroke.points[it * 2 + 1] * geometry.scale + geometry.offsetY }

        // Ventana de trabajo: el trazo más un margen amplio del que tomar textura.
        // La IA necesita ver alrededor algo más que el propio tamaño de la zona para entender la escena.
        val extent = max(xs.max() - xs.min(), ys.max() - ys.min()) + 2 * radius
        val aiContext = if (stroke.engine == HealEngine.AI) extent * AI_HEAL_CONTEXT else 0f
        val margin = maxOf(radius * HEAL_MARGIN_FACTOR, MIN_HEAL_MARGIN, aiContext)
        val left = floor(xs.min() - radius - margin).toInt().coerceIn(0, base.width - 1)
        val top = floor(ys.min() - radius - margin).toInt().coerceIn(0, base.height - 1)
        val right = ceil(xs.max() + radius + margin).toInt().coerceIn(left + 1, base.width)
        val bottom = ceil(ys.max() + radius + margin).toInt().coerceIn(top + 1, base.height)
        val windowWidth = right - left
        val windowHeight = bottom - top

        val window = IntArray(windowWidth * windowHeight)
        base.getPixels(window, 0, windowWidth, left, top, windowWidth, windowHeight)

        // Máscara antialias a resolución completa: se usa para fundir el resultado sin bordes duros.
        // Para un texto detectado, solo sus trazos (si se distinguen del fondo).
        val textMask = if (stroke.shape == HealShape.TEXT && xs.size >= 2) {
            TextMask.build(
                window, windowWidth, windowHeight,
                (min(xs[0], xs[1]) - left).toInt(), (min(ys[0], ys[1]) - top).toInt(),
                ceil(max(xs[0], xs[1]) - left).toInt(), ceil(max(ys[0], ys[1]) - top).toInt(),
                radius.roundToInt(),
                stroke.textColor.takeIf { it != 0 },
            )
        } else {
            null
        }
        val maskAlpha = if (textMask != null) {
            IntArray(textMask.size) { if (textMask[it]) -1 else 0 }
        } else {
            val shape = if (stroke.shape == HealShape.TEXT) HealShape.RECT else stroke.shape
            val fullMask = strokeMask(windowWidth, windowHeight, xs, ys, if (stroke.shape == HealShape.TEXT) 0f else radius, shape, left.toFloat(), top.toFloat())
            IntArray(windowWidth * windowHeight).also {
                fullMask.getPixels(it, 0, windowWidth, 0, 0, windowWidth, windowHeight)
                fullMask.recycle()
            }
        }
        if (maskAlpha.none { (it ushr 24) > 0 }) return

        if (stroke.engine == HealEngine.MIX || stroke.engine == HealEngine.SHUFFLE) {
            val alpha = BlendBrush.soften(IntArray(window.size) { maskAlpha[it] ushr 24 }, windowWidth, windowHeight, radius)
            val mixed = if (stroke.engine == HealEngine.MIX) {
                BlendBrush.mix(window, windowWidth, windowHeight, radius)
            } else {
                BlendBrush.shuffle(window, windowWidth, windowHeight, radius, stroke.points.hashCode())
            }
            for (i in window.indices) window[i] = blend(window[i], mixed[i], alpha[i])
            base.setPixels(window, 0, windowWidth, left, top, windowWidth, windowHeight)
            return
        }
        val hole = BooleanArray(window.size) { (maskAlpha[it] ushr 24) > 0 }
        val filledWindow = when (stroke.engine) {
            HealEngine.AI -> aiFill.fillHoles(window, windowWidth, windowHeight, hole, checkCancelled)
            HealEngine.CLASSIC ->
                DetailFill.fill(window, windowWidth, windowHeight, hole, HEAL_WORK_SIDE, DetailFill.Mode.CLEAN, checkCancelled = checkCancelled)
            else -> return
        }

        for (i in window.indices) {
            val alpha = maskAlpha[i] ushr 24
            if (alpha == 0) continue
            window[i] = blend(window[i], filledWindow[i], alpha)
        }
        base.setPixels(window, 0, windowWidth, left, top, windowWidth, windowHeight)
    }

    /**
     * Rasteriza la zona (círculos unidos o rectángulo con margen) en un bitmap cuyo canal alfa es
     * la máscara.
     */
    private fun strokeMask(
        width: Int,
        height: Int,
        xs: FloatArray,
        ys: FloatArray,
        radius: Float,
        shape: HealShape,
        originX: Float,
        originY: Float,
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = radius * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when {
            shape == HealShape.RECT && xs.size >= 2 -> {
                strokePaint.style = Paint.Style.FILL
                canvas.drawRect(
                    min(xs[0], xs[1]) - originX - radius,
                    min(ys[0], ys[1]) - originY - radius,
                    max(xs[0], xs[1]) - originX + radius,
                    max(ys[0], ys[1]) - originY + radius,
                    strokePaint,
                )
            }
            xs.size == 1 -> {
                strokePaint.style = Paint.Style.FILL
                canvas.drawCircle(xs[0] - originX, ys[0] - originY, radius, strokePaint)
            }
            else -> {
                val path = Path()
                path.moveTo(xs[0] - originX, ys[0] - originY)
                for (i in 1 until xs.size) path.lineTo(xs[i] - originX, ys[i] - originY)
                canvas.drawPath(path, strokePaint)
            }
        }
        return mask
    }

    private fun blend(original: Int, filled: Int, alpha: Int): Int {
        if (alpha >= 255) return filled or (0xFF shl 24)
        val inv = 255 - alpha
        val r = (((original shr 16) and 0xFF) * inv + ((filled shr 16) and 0xFF) * alpha) / 255
        val g = (((original shr 8) and 0xFF) * inv + ((filled shr 8) and 0xFF) * alpha) / 255
        val b = ((original and 0xFF) * inv + (filled and 0xFF) * alpha) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // endregion

    companion object {
        /** Imágenes de partida con resultado guardado (foto abierta + una combinada). */
        private const val MAX_CACHED_PHOTOS = 2

        /** Pasos del Corrector que se guardan para deshacer y rehacer al momento. */
        private const val MAX_HEAL_STEPS = 10
        private const val MAX_HEAL_STEPS_BYTES = 160L * 1024 * 1024

        /** Lado mayor de la imagen base; de sobra para pantallas de 2400 px. */
        const val MAX_BASE_SIDE = 3200
        /** Resolución de trabajo del relleno inteligente de Expandir. */
        private const val SMART_FILL_WORK_SIDE = 800
        private const val TEXTURE_FEATHER = 1.5f
        private const val DRAFT_SIDE = 340
        private const val HEAL_WORK_SIDE = 700
        /** Ancho del fundido entre foto y relleno, relativo al lado corto de la foto. */
        private const val FEATHER_FRACTION = 0.04f
        private const val HEAL_MARGIN_FACTOR = 4f
        private const val MIN_HEAL_MARGIN = 40f
        private const val AI_HEAL_CONTEXT = 0.9f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }
}
