package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepara una foto para la pantalla: se decodifica ya reducida (nunca a tamaño completo, para no
 * gastar memoria ni batería) y se ajusta al tamaño exacto rellenando o encajando sobre un fondo
 * desenfocado.
 */
object WallpaperRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun render(context: Context, uri: Uri, width: Int, height: Int, fit: WallpaperFit): Bitmap {
        val photo = decode(context, uri, width, height, fit)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val cover = max(width.toFloat() / photo.width, height.toFloat() / photo.height)
        if (fit == WallpaperFit.FIT_BLUR) {
            // Fondo: la propia foto cubriendo la pantalla, muy reducida y ampliada (desenfoque barato).
            val small = Bitmap.createScaledBitmap(photo, max(1, photo.width / BLUR_FACTOR), max(1, photo.height / BLUR_FACTOR), true)
            canvas.drawBitmap(small, null, centered(small.width * cover * BLUR_FACTOR, small.height * cover * BLUR_FACTOR, width, height), paint)
            small.recycle()
            canvas.drawColor(0x55000000)
            val contain = min(width.toFloat() / photo.width, height.toFloat() / photo.height)
            canvas.drawBitmap(photo, null, centered(photo.width * contain, photo.height * contain, width, height), paint)
        } else {
            canvas.drawBitmap(photo, null, centered(photo.width * cover, photo.height * cover, width, height), paint)
        }
        photo.recycle()
        return out
    }

    private fun centered(w: Float, h: Float, width: Int, height: Int) =
        RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)

    /** Decodifica con el tamaño justo para cubrir (o encajar en) la pantalla. */
    private fun decode(context: Context, uri: Uri, width: Int, height: Int, fit: WallpaperFit): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = if (fit == WallpaperFit.FILL) max(width.toFloat() / w, height.toFloat() / h) else min(width.toFloat() / w, height.toFloat() / h)
                if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalStateException("No se pudo leer $uri")
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (rotation == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }

    /**
     * Colores para el reloj a partir del fondo:
     * - [AdaptiveColors.contrast]: blanco u oscuro según lo clara que sea la zona del reloj.
     * - [AdaptiveColors.accent]: un color que está en la propia foto, elegido para que destaque
     *   sobre la zona del reloj (un tono distinto al de esa zona, con preferencia por los más
     *   vivos y presentes) y ajustado de luminosidad hasta que se lea bien (contraste ≥ 3,5:1).
     */
    fun adaptiveColors(bitmap: Bitmap): AdaptiveColors {
        val w = 36
        val h = 72
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        // Zona del reloj: el tercio superior, donde suele ir el widget.
        val clockArea = pixels.copyOfRange(0, w * h / 3)
        var r = 0L; var g = 0L; var b = 0L
        for (p in clockArea) { r += Color.red(p); g += Color.green(p); b += Color.blue(p) }
        val n = clockArea.size
        val background = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        val backgroundLuminance = ColorUtils.calculateLuminance(background)
        val dark = backgroundLuminance > 0.4
        val contrast = if (dark) 0xFF141418.toInt() else 0xFFFFFFFF.toInt()
        val backgroundHsl = FloatArray(3).also { ColorUtils.colorToHSL(background, it) }
        val backgroundIsGray = backgroundHsl[1] < 0.18f

        // Tonos de toda la foto: 24 grupos, pesados por saturación y presencia.
        val bins = 24
        val weight = FloatArray(bins)
        val hueX = FloatArray(bins)
        val hueY = FloatArray(bins)
        val satSum = FloatArray(bins)
        val hsl = FloatArray(3)
        for (p in pixels) {
            ColorUtils.colorToHSL(p, hsl)
            if (hsl[1] < 0.22f || hsl[2] < 0.12f || hsl[2] > 0.92f) continue
            val bin = ((hsl[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            val wgt = hsl[1] * (1f - abs(hsl[2] - 0.5f))
            weight[bin] += wgt
            val rad = Math.toRadians(hsl[0].toDouble())
            hueX[bin] += (kotlin.math.cos(rad) * wgt).toFloat()
            hueY[bin] += (kotlin.math.sin(rad) * wgt).toFloat()
            satSum[bin] += hsl[1] * wgt
        }
        val total = weight.sum()
        val vivo = vivoLikeColor(bitmap, dark)
        if (total <= 0f) return AdaptiveColors(contrast, contrast, vivo)

        var best = -1
        var bestScore = 0f
        var bestDistance = 1f
        for (i in 0 until bins) {
            if (weight[i] <= 0f) continue
            val presence = weight[i] / total
            if (presence < 0.02f) continue
            val hue = ((Math.toDegrees(kotlin.math.atan2(hueY[i], hueX[i]).toDouble()) + 360.0) % 360.0).toFloat()
            val saturation = satSum[i] / weight[i]
            // Que destaque: lejos del tono de la zona del reloj (si esa zona tiene color).
            val distance = if (backgroundIsGray) 1f else hueDistance(hue, backgroundHsl[0]) / 180f
            val score = kotlin.math.sqrt(presence) * (0.35f + saturation) * (0.25f + distance)
            if (score > bestScore) {
                bestScore = score
                bestDistance = distance
                best = i
                hsl[0] = hue
                hsl[1] = saturation
            }
        }
        if (best < 0) return AdaptiveColors(contrast, contrast, vivo)

        // Color vivo con ese tono, aclarado u oscurecido hasta que se lea.
        val accentHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.55f, 0.9f), if (dark) 0.42f else 0.62f)
        var accent = ColorUtils.HSLToColor(accentHsl)
        var steps = 0
        // Si el tono es el mismo que el de la zona del reloj (azul sobre cielo azul), solo destaca
        // por luminosidad: se exige más contraste.
        val required = if (bestDistance < 0.25f) SAME_HUE_CONTRAST else MIN_CONTRAST
        while (ColorUtils.calculateContrast(accent, background) < required && steps < 30) {
            accentHsl[2] = (accentHsl[2] + if (dark) -0.03f else 0.03f).coerceIn(0.08f, 0.95f)
            accent = ColorUtils.HSLToColor(accentHsl)
            steps++
        }
        return AdaptiveColors(contrast, accent, vivo)
    }

    /**
     * Color como el del reloj de bloqueo de vivo en automático: el color principal del fondo
     * según Android (WallpaperColors, el mismo que da el tono a Material You) llevado a un tono
     * muy claro (L* 92) si la zona del reloj es oscura o muy oscuro (L* 22) si es clara, con un
     * croma moderado. Así sale, por ejemplo, el cian muy claro que vivo pone sobre fondos oscuros.
     */
    private fun vivoLikeColor(bitmap: Bitmap, darkText: Boolean): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 112, 248, true)
        val seed = try {
            android.app.WallpaperColors.fromBitmap(small).primaryColor.toArgb()
        } catch (e: Exception) {
            Color.GRAY
        } finally {
            small.recycle()
        }
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(seed, lab)
        val chroma = kotlin.math.hypot(lab[1], lab[2])
        val hue = kotlin.math.atan2(lab[2], lab[1])
        val targetChroma = chroma.coerceAtMost(VIVO_MAX_CHROMA)
        val l = if (darkText) VIVO_DARK_TONE else VIVO_LIGHT_TONE
        return ColorUtils.LABToColor(l, targetChroma * kotlin.math.cos(hue), targetChroma * kotlin.math.sin(hue))
    }

    private const val VIVO_LIGHT_TONE = 92.0
    private const val VIVO_DARK_TONE = 22.0
    private const val VIVO_MAX_CHROMA = 36.0

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private const val MIN_CONTRAST = 3.5
    private const val SAME_HUE_CONTRAST = 5.5

    private fun luma(c: Int): Float = Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f

    private const val BLUR_FACTOR = 24
}
