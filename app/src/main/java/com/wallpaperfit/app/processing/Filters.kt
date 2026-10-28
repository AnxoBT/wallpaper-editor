package com.wallpaperfit.app.processing

import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FilterSettings
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Filtros de un toque. Cada uno es una función de color por píxel; la intensidad mezcla el
 * resultado con el color de partida.
 */
internal object Filters {

    fun apply(pixels: IntArray, settings: FilterSettings) {
        val amount = (settings.intensity / 100f).coerceIn(0f, 1f)
        if (settings.preset == FilterPreset.NONE || amount <= 0f) return
        val out = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            transform(settings.preset, r, g, b, out)
            val nr = r + (out[0] - r) * amount
            val ng = g + (out[1] - g) * amount
            val nb = b + (out[2] - b) * amount
            pixels[i] = (p and ALPHA_MASK) or (to8(nr) shl 16) or (to8(ng) shl 8) or to8(nb)
        }
    }

    private fun transform(preset: FilterPreset, r: Float, g: Float, b: Float, out: FloatArray) {
        val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        when (preset) {
            FilterPreset.NONE -> set(out, r, g, b)
            FilterPreset.BW -> {
                val v = curve(l, 0.2f)
                set(out, v, v, v)
            }
            FilterPreset.NOIR -> {
                val v = curve(curve(l, 1f), 0.6f).pow(1.15f)
                set(out, v, v, v)
            }
            FilterPreset.SEPIA -> set(
                out,
                0.393f * r + 0.769f * g + 0.189f * b,
                0.349f * r + 0.686f * g + 0.168f * b,
                0.272f * r + 0.534f * g + 0.131f * b,
            )
            FilterPreset.INVERT -> set(out, 1f - r, 1f - g, 1f - b)
            FilterPreset.VINTAGE -> {
                val sr = l + (r - l) * 0.6f
                val sg = l + (g - l) * 0.6f
                val sb = l + (b - l) * 0.6f
                set(
                    out,
                    (0.08f + sr * 0.86f) * 1.06f,
                    0.08f + sg * 0.86f + 0.03f * (1f - l),
                    (0.08f + sb * 0.86f) * 0.88f,
                )
            }
            FilterPreset.WARM -> set(out, r * 1.1f + 0.02f, g * 1.03f, b * 0.85f)
            FilterPreset.COOL -> set(out, r * 0.88f, g, b * 1.12f + 0.02f)
            FilterPreset.DRAMA -> {
                val cr = curve(r, 0.9f)
                val cg = curve(g, 0.9f)
                val cb = curve(b, 0.9f)
                val cl = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                set(
                    out,
                    (cl + (cr - cl) * 0.75f) * 0.92f,
                    (cl + (cg - cl) * 0.75f) * 0.92f,
                    (cl + (cb - cl) * 0.75f) * 0.92f,
                )
            }
            FilterPreset.VIVID -> {
                val cr = curve(r, 0.35f)
                val cg = curve(g, 0.35f)
                val cb = curve(b, 0.35f)
                val cl = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                set(out, cl + (cr - cl) * 1.5f, cl + (cg - cl) * 1.5f, cl + (cb - cl) * 1.5f)
            }
            FilterPreset.MATTE -> {
                val sr = l + (r - l) * 0.85f
                val sg = l + (g - l) * 0.85f
                val sb = l + (b - l) * 0.85f
                set(out, 0.12f + sr * 0.78f, 0.12f + sg * 0.78f, 0.13f + sb * 0.78f)
            }
            FilterPreset.CINEMA -> {
                // Sombras hacia el turquesa y luces hacia el naranja.
                val shadow = 1f - l
                val cr = curve(r, 0.3f) - 0.08f * shadow + 0.08f * l
                val cg = curve(g, 0.3f) + 0.02f * shadow + 0.03f * l
                val cb = curve(b, 0.3f) + 0.06f * shadow - 0.07f * l
                val cl = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                set(out, cl + (cr - cl) * 0.9f, cl + (cg - cl) * 0.9f, cl + (cb - cl) * 0.9f)
            }
        }
    }

    /** Curva en S: [amount] = 0 no cambia nada, 1 equivale a smoothstep completo. */
    private fun curve(x: Float, amount: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c + (c * c * (3f - 2f * c) - c) * amount
    }

    private fun set(out: FloatArray, r: Float, g: Float, b: Float) {
        out[0] = r
        out[1] = g
        out[2] = b
    }

    private fun to8(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)

    private const val ALPHA_MASK = -0x1000000
}
