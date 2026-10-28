package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.FilterSettings
import com.wallpaperfit.app.model.LensBlur
import com.wallpaperfit.app.model.LensShape
import com.wallpaperfit.app.model.PhotoEdits
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ajustes al estilo Snapseed implementados con operaciones de píxel sobre IntArray.
 * Los radios y tamaños son relativos a la imagen, así que la vista previa reducida y la
 * exportación a resolución completa producen el mismo aspecto.
 */
class AdjustmentProcessor(private val blurEngine: BlurEngine) {

    /**
     * Tono, color y detalle sobre la foto original (antes del encaje).
     * Devuelve [source] sin copiar si no hay ajustes que aplicar.
     */
    fun applyPhoto(source: Bitmap, adjustments: Adjustments, filter: FilterSettings): Bitmap =
        applyPhoto(source, PhotoEdits(adjustments, filter), null)

    /**
     * Todas las ediciones de foto. [mapping] sitúa los trazos del Pincel sobre [source]; sin él
     * el Pincel no se aplica (miniaturas).
     */
    fun applyPhoto(source: Bitmap, photo: PhotoEdits, mapping: PhotoEffects.Mapping?): Bitmap {
        if (photo.isNone) return source
        val adjustments = photo.adjustments
        val filter = photo.filter
        val structure = adjustments[AdjustParam.STRUCTURE] / 100f
        val sharpen = adjustments[AdjustParam.SHARPEN] / 100f
        val tonal = TONAL_PARAMS.any { adjustments[it] != it.default }

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Como en Snapseed, el filtro va primero y los ajustes se aplican sobre su resultado.
        if (!filter.isNone) Filters.apply(pixels, filter)
        if (tonal) applyTonal(pixels, adjustments)
        effects.applyCurves(pixels, photo.curves)
        effects.dehaze(pixels, width, height, adjustments[AdjustParam.DEHAZE] / 100f)
        effects.hdr(pixels, width, height, adjustments)
        effects.drama(pixels, width, height, adjustments)
        effects.glamour(pixels, width, height, adjustments)

        val shortSide = min(width, height).toFloat()
        if (structure > 0f) {
            unsharpMask(pixels, width, height, max(2f, shortSide * STRUCTURE_RADIUS), structure * STRUCTURE_AMOUNT)
        }
        if (sharpen > 0f) {
            unsharpMask(pixels, width, height, max(1f, shortSide * SHARPEN_RADIUS), sharpen * SHARPEN_AMOUNT)
        }
        if (mapping != null) effects.applyBrush(pixels, width, height, photo.brush, mapping)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private val effects = PhotoEffects(blurEngine)

    /**
     * Desenfoque de lente, viñeta y grano sobre el lienzo final ya encajado. Modifica [output],
     * que debe ser mutable.
     */
    fun applyCanvasEffects(output: Bitmap, adjustments: Adjustments, lens: LensBlur) {
        val lensStrength = adjustments[AdjustParam.LENS_STRENGTH] / 100f
        if (lensStrength > 0f) {
            applyLensBlur(
                output, lens, lensStrength,
                adjustments[AdjustParam.LENS_TRANSITION] / 100f,
                adjustments[AdjustParam.LENS_VIGNETTE] / 100f,
            )
        }
        val vignette = adjustments[AdjustParam.VIGNETTE] / 100f
        if (vignette != 0f) drawVignette(output, vignette, adjustments[AdjustParam.VIGNETTE_SIZE] / 100f)

        val grain = adjustments[AdjustParam.GRAIN] / 100f
        if (grain > 0f) applyGrain(output, grain, adjustments[AdjustParam.GRAIN_SIZE] / 100f)
    }

    // region Tono y color

    private fun applyTonal(pixels: IntArray, adjustments: Adjustments) {
        val brightness = adjustments[AdjustParam.BRIGHTNESS] / 100f
        val contrast = adjustments[AdjustParam.CONTRAST] / 100f
        val ambiance = adjustments[AdjustParam.AMBIANCE] / 100f
        // Ambiente: aclara sombras, contiene luces y da algo de saturación, como en Snapseed.
        val highlights = adjustments[AdjustParam.HIGHLIGHTS] / 100f - ambiance * 0.3f
        val shadows = adjustments[AdjustParam.SHADOWS] / 100f + ambiance * 0.45f
        val saturation = adjustments[AdjustParam.SATURATION] / 100f + ambiance * 0.25f
        val warmth = adjustments[AdjustParam.WARMTH] / 100f
        // Tinte: verde (negativo) ↔ magenta (positivo), como en el balance de blancos de Snapseed.
        val tint = adjustments[AdjustParam.TINT] / 100f

        val tone = FloatArray(256) { toneCurve(it / 255f, brightness, contrast, highlights, shadows) }
        val lutR = IntArray(256) { to8Bit(tone[it] * (1f + warmth * 0.10f + tint * 0.05f)) }
        val lutG = IntArray(256) { to8Bit(tone[it] * (1f + warmth * 0.02f - tint * 0.10f)) }
        val lutB = IntArray(256) { to8Bit(tone[it] * (1f - warmth * 0.12f + tint * 0.05f)) }

        val saturationFactor = 1f + if (saturation > 0f) saturation * 0.9f else saturation.coerceAtLeast(-1f)
        val satFixed = (saturationFactor * 256f).roundToInt()
        val applySaturation = satFixed != 256

        for (i in pixels.indices) {
            val p = pixels[i]
            var r = lutR[(p shr 16) and 0xFF]
            var g = lutG[(p shr 8) and 0xFF]
            var b = lutB[p and 0xFF]
            if (applySaturation) {
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                r = clamp8(lum + (((r - lum) * satFixed) shr 8))
                g = clamp8(lum + (((g - lum) * satFixed) shr 8))
                b = clamp8(lum + (((b - lum) * satFixed) shr 8))
            }
            pixels[i] = (p and ALPHA_MASK) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun toneCurve(value: Float, brightness: Float, contrast: Float, highlights: Float, shadows: Float): Float {
        var x = value
        // Brillo como gamma: aclara u oscurece los medios tonos conservando negro y blanco puros.
        if (brightness != 0f) x = x.pow(2f.pow(-brightness * 1.1f))
        x += shadows * 0.3f * (1f - smoothstep(0f, 0.55f, x))
        x += highlights * 0.25f * smoothstep(0.45f, 1f, x)
        x = x.coerceIn(0f, 1f)
        x = if (contrast > 0f) {
            // Curva en S: más contraste sin recortar bruscamente los extremos.
            x + (smoothstep(0f, 1f, x) - x) * contrast * 1.6f
        } else {
            0.5f + (x - 0.5f) * (1f + contrast * 0.7f)
        }
        return x.coerceIn(0f, 1f)
    }

    // endregion

    // region Detalle

    /** Máscara de enfoque: realza la diferencia entre la imagen y su versión desenfocada. */
    private fun unsharpMask(pixels: IntArray, width: Int, height: Int, radius: Float, amount: Float) {
        val current = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val blurred = blurEngine.blur(current, radius)
        current.recycle()
        val blurredPixels = IntArray(width * height)
        blurred.getPixels(blurredPixels, 0, width, 0, 0, width, height)
        blurred.recycle()

        val k = (amount * 256f).roundToInt()
        for (i in pixels.indices) {
            val p = pixels[i]
            val q = blurredPixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val nr = clamp8(r + (((r - ((q shr 16) and 0xFF)) * k) shr 8))
            val ng = clamp8(g + (((g - ((q shr 8) and 0xFF)) * k) shr 8))
            val nb = clamp8(b + (((b - (q and 0xFF)) * k) shr 8))
            pixels[i] = (p and ALPHA_MASK) or (nr shl 16) or (ng shl 8) or nb
        }
    }

    // endregion

    // region Efectos del lienzo

    /**
     * Desenfoque de lente: fuera de la zona enfocada (elipse o franja) se mezcla una versión
     * desenfocada, con una transición suave y un oscurecimiento opcional de lo desenfocado.
     */
    private fun applyLensBlur(output: Bitmap, lens: LensBlur, strength: Float, transition: Float, vignette: Float) {
        val width = output.width
        val height = output.height
        val shortSide = min(width, height).toFloat()
        val blurred = blurEngine.blur(output, max(1.5f, shortSide * 0.035f * strength))
        val blurredPixels = IntArray(width * height)
        blurred.getPixels(blurredPixels, 0, width, 0, 0, width, height)
        blurred.recycle()
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)

        val cx = lens.centerX * width
        val cy = lens.centerY * height
        val inner = max(1f, lens.size * shortSide / 2f)
        val fade = max(1f, inner * (0.15f + transition * 1.6f))
        val radians = Math.toRadians(lens.angle.toDouble())
        // Normal de la franja lineal: el ángulo es el de la franja.
        val nx = -kotlin.math.sin(radians).toFloat()
        val ny = kotlin.math.cos(radians).toFloat()
        for (y in 0 until height) {
            val dy = y - cy
            for (x in 0 until width) {
                val dx = x - cx
                val distance = when (lens.shape) {
                    LensShape.RADIAL -> kotlin.math.sqrt(dx * dx + dy * dy)
                    LensShape.LINEAR -> abs(dx * nx + dy * ny)
                }
                val m = smoothstep(inner, inner + fade, distance)
                if (m <= 0f) continue
                val i = y * width + x
                val p = pixels[i]
                val q = blurredPixels[i]
                val dark = 1f - vignette * 0.6f * m
                val r = (((p shr 16) and 0xFF) * (1f - m) + ((q shr 16) and 0xFF) * m) * dark
                val g = (((p shr 8) and 0xFF) * (1f - m) + ((q shr 8) and 0xFF) * m) * dark
                val b = ((p and 0xFF) * (1f - m) + (q and 0xFF) * m) * dark
                pixels[i] = (p and ALPHA_MASK) or (clamp8(r.roundToInt()) shl 16) or (clamp8(g.roundToInt()) shl 8) or clamp8(b.roundToInt())
            }
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun drawVignette(output: Bitmap, strength: Float, size: Float) {
        val width = output.width.toFloat()
        val height = output.height.toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val alpha = (abs(strength) * if (strength > 0f) 230f else 190f).roundToInt()
        val edge = if (strength > 0f) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
        val middle = if (strength > 0f) Color.argb(alpha * 2 / 5, 0, 0, 0) else Color.argb(alpha * 2 / 5, 255, 255, 255)
        val inner = 0.25f + size * 0.55f

        // Gradiente circular escalado horizontalmente para que siga la forma del lienzo.
        val shader = RadialGradient(
            cx, cy, height / 2f * SQRT_2,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, middle, edge),
            floatArrayOf(0f, inner, (inner + 1f) / 2f, 1f),
            Shader.TileMode.CLAMP,
        )
        shader.setLocalMatrix(Matrix().apply { setScale(width / height, 1f, cx, cy) })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { this.shader = shader }
        Canvas(output).drawRect(0f, 0f, width, height, paint)
    }

    /** Grano de película: ruido monocromo con semilla fija, con tamaño de grano relativo al lienzo. */
    private fun applyGrain(output: Bitmap, amount: Float, size: Float) {
        val width = output.width
        val height = output.height
        val cell = max(1f, min(width, height) / REFERENCE_SHORT_SIDE * (1f + size * 2.5f))
        val noiseWidth = max(1, (width / cell).roundToInt())
        val noiseHeight = max(1, (height / cell).roundToInt())

        val random = Random(GRAIN_SEED)
        val noise = IntArray(noiseWidth * noiseHeight) {
            // Suma de tres uniformes: distribución aproximadamente normal.
            val n = random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f
            val gray = clamp8(128 + (n * 85f).roundToInt())
            ALPHA_MASK or (gray shl 16) or (gray shl 8) or gray
        }
        val noiseBitmap = Bitmap.createBitmap(noise, noiseWidth, noiseHeight, Bitmap.Config.ARGB_8888)
        val scaled = if (noiseWidth == width && noiseHeight == height) {
            noiseBitmap
        } else {
            Bitmap.createScaledBitmap(noiseBitmap, width, height, true).also { noiseBitmap.recycle() }
        }
        val grain = IntArray(width * height)
        scaled.getPixels(grain, 0, width, 0, 0, width, height)
        scaled.recycle()

        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        val k = (amount * 0.7f * 256f).roundToInt()
        for (i in pixels.indices) {
            val p = pixels[i]
            val d = (((grain[i] and 0xFF) - 128) * k) shr 8
            val r = clamp8(((p shr 16) and 0xFF) + d)
            val g = clamp8(((p shr 8) and 0xFF) + d)
            val b = clamp8((p and 0xFF) + d)
            pixels[i] = (p and ALPHA_MASK) or (r shl 16) or (g shl 8) or b
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    // endregion

    private companion object {
        val TONAL_PARAMS = listOf(
            AdjustParam.BRIGHTNESS,
            AdjustParam.CONTRAST,
            AdjustParam.SATURATION,
            AdjustParam.AMBIANCE,
            AdjustParam.HIGHLIGHTS,
            AdjustParam.SHADOWS,
            AdjustParam.WARMTH,
            AdjustParam.TINT,
        )
        const val ALPHA_MASK = -0x1000000 // 0xFF000000
        const val SQRT_2 = 1.4142135f
        const val REFERENCE_SHORT_SIDE = 1080f
        const val GRAIN_SEED = 0x5EED

        const val STRUCTURE_RADIUS = 0.012f
        const val STRUCTURE_AMOUNT = 0.9f
        const val SHARPEN_RADIUS = 0.0012f
        const val SHARPEN_AMOUNT = 1.6f

        fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun to8Bit(value: Float): Int = clamp8((value * 255f).roundToInt())

        fun clamp8(value: Int): Int = if (value < 0) 0 else if (value > 255) 255 else value
    }
}
