package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.BrushEffect
import com.wallpaperfit.app.model.BrushStroke
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CurvePoint
import com.wallpaperfit.app.model.Curves
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Efectos de foto al estilo Snapseed que necesitan algo más que una curva por píxel: Curvas,
 * Quitar neblina, HDR, Drama, Glamour y Pincel. Trabajan sobre IntArray ARGB y los radios son
 * relativos al lado corto, así que la vista previa y la exportación se ven igual.
 */
class PhotoEffects(private val blurEngine: BlurEngine) {

    // region Curvas

    fun applyCurves(pixels: IntArray, curves: Curves) {
        if (curves.isNone) return
        val master = lut(curves[CurveChannel.RGB])
        val red = lut(curves[CurveChannel.RED])
        val green = lut(curves[CurveChannel.GREEN])
        val blue = lut(curves[CurveChannel.BLUE])
        // El canal RGB actúa después del de cada color, como en los editores habituales.
        val lutR = IntArray(256) { master[red[it]] }
        val lutG = IntArray(256) { master[green[it]] }
        val lutB = IntArray(256) { master[blue[it]] }
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = (p and ALPHA) or (lutR[(p shr 16) and 0xFF] shl 16) or (lutG[(p shr 8) and 0xFF] shl 8) or lutB[p and 0xFF]
        }
    }

    // endregion

    // region Efectos de tono local

    /**
     * Quitar neblina (positivo) o añadirla (negativo). Versión ligera del "dark channel prior":
     * la neblina se estima con el mínimo RGB desenfocado y se resta hacia el color del aire.
     */
    fun dehaze(pixels: IntArray, width: Int, height: Int, amount: Float) {
        if (amount == 0f) return
        val dark = IntArray(pixels.size) { i ->
            val p = pixels[i]
            val m = min((p shr 16) and 0xFF, min((p shr 8) and 0xFF, p and 0xFF))
            ALPHA or (m shl 16) or (m shl 8) or m
        }
        val haze = blurPixels(dark, width, height, shortSide(width, height) * 0.03f)
        val air = 245f
        for (i in pixels.indices) {
            val p = pixels[i]
            val h = (haze[i] and 0xFF) / air
            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()
            if (amount > 0f) {
                val t = max(0.3f, 1f - 0.9f * amount * h)
                r = (r - air) / t + air
                g = (g - air) / t + air
                b = (b - air) / t + air
            } else {
                val k = -amount * 0.55f
                r += (air - r) * k
                g += (air - g) * k
                b += (air - b) * k
            }
            pixels[i] = rgb(r, g, b)
        }
    }

    /**
     * HDR: mucho contraste local (detalle respecto a una versión muy desenfocada) y compresión
     * del tono general hacia los medios, que abre sombras y recupera luces.
     */
    fun hdr(pixels: IntArray, width: Int, height: Int, adjustments: Adjustments) {
        val strength = adjustments[AdjustParam.HDR_STRENGTH] / 100f
        val brightness = adjustments[AdjustParam.HDR_BRIGHTNESS] / 100f
        val saturation = adjustments[AdjustParam.HDR_SATURATION] / 100f
        if (strength <= 0f && brightness == 0f && saturation == 0f) return
        val blurred = blurPixels(pixels, width, height, shortSide(width, height) * 0.04f)
        localContrast(
            pixels, blurred,
            detail = strength * 1.6f,
            compression = strength * 0.5f,
            offset = brightness * 45f,
            saturation = 1f + saturation + strength * 0.15f,
        )
    }

    /** Drama: contraste local de radio medio, más áspero que HDR y algo desaturado. */
    fun drama(pixels: IntArray, width: Int, height: Int, adjustments: Adjustments) {
        val strength = adjustments[AdjustParam.DRAMA_STRENGTH] / 100f
        val saturation = adjustments[AdjustParam.DRAMA_SATURATION] / 100f
        if (strength <= 0f && saturation == 0f) return
        val blurred = blurPixels(pixels, width, height, shortSide(width, height) * 0.015f)
        localContrast(
            pixels, blurred,
            detail = strength * 2.6f,
            compression = strength * 0.25f,
            offset = 0f,
            saturation = (1f + saturation - strength * 0.35f).coerceAtLeast(0f),
        )
    }

    private fun localContrast(
        pixels: IntArray,
        blurred: IntArray,
        detail: Float,
        compression: Float,
        offset: Float,
        saturation: Float,
    ) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val q = blurred[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            val br = ((q shr 16) and 0xFF).toFloat()
            val bg = ((q shr 8) and 0xFF).toFloat()
            val bb = (q and 0xFF).toFloat()
            val baseLuma = br * 0.299f + bg * 0.587f + bb * 0.114f
            val shift = (128f - baseLuma) * compression + offset
            var nr = r + (r - br) * detail + shift
            var ng = g + (g - bg) * detail + shift
            var nb = b + (b - bb) * detail + shift
            if (saturation != 1f) {
                val l = nr * 0.299f + ng * 0.587f + nb * 0.114f
                nr = l + (nr - l) * saturation
                ng = l + (ng - l) * saturation
                nb = l + (nb - l) * saturation
            }
            pixels[i] = (p and ALPHA) or (clamp(nr) shl 16) or (clamp(ng) shl 8) or clamp(nb)
        }
    }

    /** Glamour: resplandor suave (trama de la versión desenfocada), saturación y calidez. */
    fun glamour(pixels: IntArray, width: Int, height: Int, adjustments: Adjustments) {
        val glow = adjustments[AdjustParam.GLOW] / 100f
        val saturation = adjustments[AdjustParam.GLOW_SATURATION] / 100f
        val warmth = adjustments[AdjustParam.GLOW_WARMTH] / 100f
        if (glow <= 0f && saturation == 0f && warmth == 0f) return
        val blurred = if (glow > 0f) blurPixels(pixels, width, height, shortSide(width, height) * 0.025f) else null
        val satFactor = 1f + saturation
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()
            if (blurred != null) {
                val q = blurred[i]
                val gr = ((q shr 16) and 0xFF) * glow * 0.85f
                val gg = ((q shr 8) and 0xFF) * glow * 0.85f
                val gb = (q and 0xFF) * glow * 0.85f
                // Trama: aclara sin quemar; luego se suaviza un poco hacia el resplandor.
                r = 255f - (255f - r) * (255f - gr) / 255f
                g = 255f - (255f - g) * (255f - gg) / 255f
                b = 255f - (255f - b) * (255f - gb) / 255f
                val soft = glow * 0.25f
                r += (((q shr 16) and 0xFF) - r) * soft
                g += (((q shr 8) and 0xFF) - g) * soft
                b += ((q and 0xFF) - b) * soft
            }
            if (warmth != 0f) {
                r += warmth * 22f
                b -= warmth * 22f
            }
            if (satFactor != 1f) {
                val l = r * 0.299f + g * 0.587f + b * 0.114f
                r = l + (r - l) * satFactor
                g = l + (g - l) * satFactor
                b = l + (b - l) * satFactor
            }
            pixels[i] = (p and ALPHA) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
    }

    // endregion

    // region Pincel

    /**
     * Correspondencia entre las coordenadas de la foto original (las de los trazos) y los píxeles
     * de la imagen que se está procesando: x' = x * [scale] + [offsetX].
     */
    class Mapping(val scale: Float, val offsetX: Float, val offsetY: Float)

    fun applyBrush(pixels: IntArray, width: Int, height: Int, strokes: List<BrushStroke>, mapping: Mapping) {
        if (strokes.isEmpty()) return
        for (effect in BrushEffect.entries) {
            val own = strokes.filter { it.effect == effect }
            if (own.isEmpty()) continue
            val mask = brushMask(width, height, own, mapping)
            val blurred = if (effect == BrushEffect.STRUCTURE) {
                blurPixels(pixels, width, height, shortSide(width, height) * 0.012f)
            } else {
                null
            }
            for (i in pixels.indices) {
                val v = ((mask[i] and 0xFF) - 128) / 127f
                if (v > -0.01f && v < 0.01f) continue
                val p = pixels[i]
                var r = ((p shr 16) and 0xFF).toFloat()
                var g = ((p shr 8) and 0xFF).toFloat()
                var b = (p and 0xFF).toFloat()
                when (effect) {
                    BrushEffect.EXPOSURE -> {
                        val f = 2f.pow(v)
                        r *= f; g *= f; b *= f
                    }
                    BrushEffect.TEMPERATURE -> {
                        r += v * 32f
                        g += v * 6f
                        b -= v * 36f
                    }
                    BrushEffect.SATURATION -> {
                        val l = r * 0.299f + g * 0.587f + b * 0.114f
                        val f = 1f + v
                        r = l + (r - l) * f
                        g = l + (g - l) * f
                        b = l + (b - l) * f
                    }
                    BrushEffect.STRUCTURE -> {
                        val q = blurred!![i]
                        val k = v * 2f
                        r += (r - ((q shr 16) and 0xFF)) * k
                        g += (g - ((q shr 8) and 0xFF)) * k
                        b += (b - (q and 0xFF)) * k
                    }
                }
                pixels[i] = (p and ALPHA) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
    }

    /**
     * Mapa del efecto: gris 128 = sin cambio; cada trazo pinta su valor encima de los anteriores
     * con bordes suaves (pintar con 0 borra).
     */
    private fun brushMask(width: Int, height: Int, strokes: List<BrushStroke>, mapping: Mapping): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(128, 128, 128))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val radius = max(1f, stroke.radius * mapping.scale)
            val gray = (128 + stroke.value / 100f * 127f).roundToInt().coerceIn(1, 255)
            paint.color = Color.rgb(gray, gray, gray)
            paint.strokeWidth = radius * 1.6f
            paint.maskFilter = BlurMaskFilter(radius * 0.4f, BlurMaskFilter.Blur.NORMAL)
            val path = Path()
            val x0 = stroke.points[0] * mapping.scale + mapping.offsetX
            val y0 = stroke.points[1] * mapping.scale + mapping.offsetY
            path.moveTo(x0, y0)
            if (stroke.points.size == 2) {
                path.lineTo(x0 + 0.1f, y0)
            } else {
                for (i in 1 until stroke.points.size / 2) {
                    path.lineTo(stroke.points[i * 2] * mapping.scale + mapping.offsetX, stroke.points[i * 2 + 1] * mapping.scale + mapping.offsetY)
                }
            }
            canvas.drawPath(path, paint)
        }
        val out = IntArray(width * height)
        bitmap.getPixels(out, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return out
    }

    // endregion

    private fun blurPixels(pixels: IntArray, width: Int, height: Int, radius: Float): IntArray {
        val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val blurred = blurEngine.blur(source, max(1f, radius))
        source.recycle()
        val out = IntArray(width * height)
        blurred.getPixels(out, 0, width, 0, 0, width, height)
        blurred.recycle()
        return out
    }

    companion object {
        private const val ALPHA = -0x1000000

        private fun shortSide(width: Int, height: Int): Float = min(width, height).toFloat()

        private fun clamp(v: Float): Int = if (v <= 0f) 0 else if (v >= 255f) 255 else v.roundToInt()

        private fun rgb(r: Float, g: Float, b: Float): Int = ALPHA or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

        /**
         * Tabla de 256 valores de la curva interpolada con Hermite monótona (Fritsch–Carlson):
         * pasa por todos los puntos sin oscilaciones ni inversiones de tono.
         */
        fun lut(points: List<CurvePoint>): IntArray {
            val pts = points.sortedBy { it.x }
            if (Curves.isIdentity(pts) || pts.size < 2) return IntArray(256) { it }
            val n = pts.size
            val xs = FloatArray(n) { pts[it].x }
            val ys = FloatArray(n) { pts[it].y }
            val slopes = FloatArray(n - 1) { i ->
                val dx = xs[i + 1] - xs[i]
                if (dx <= 1e-6f) 0f else (ys[i + 1] - ys[i]) / dx
            }
            val tangents = FloatArray(n)
            tangents[0] = slopes[0]
            tangents[n - 1] = slopes[n - 2]
            for (i in 1 until n - 1) {
                tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
            }
            for (i in 0 until n - 1) {
                if (slopes[i] == 0f) {
                    tangents[i] = 0f
                    tangents[i + 1] = 0f
                    continue
                }
                val a = tangents[i] / slopes[i]
                val b = tangents[i + 1] / slopes[i]
                val s = a * a + b * b
                if (s > 9f) {
                    val t = 3f / kotlin.math.sqrt(s)
                    tangents[i] = t * a * slopes[i]
                    tangents[i + 1] = t * b * slopes[i]
                }
            }
            return IntArray(256) { v ->
                val x = v / 255f
                val y = when {
                    x <= xs[0] -> ys[0]
                    x >= xs[n - 1] -> ys[n - 1]
                    else -> {
                        var k = 0
                        while (k < n - 2 && x > xs[k + 1]) k++
                        val h = xs[k + 1] - xs[k]
                        val t = if (h <= 1e-6f) 0f else (x - xs[k]) / h
                        val t2 = t * t
                        val t3 = t2 * t
                        (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * tangents[k] +
                            (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * tangents[k + 1]
                    }
                }
                (y * 255f).roundToInt().coerceIn(0, 255)
            }
        }
    }
}
