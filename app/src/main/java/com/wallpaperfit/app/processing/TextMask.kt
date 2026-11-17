package com.wallpaperfit.app.processing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Separa las letras de su fondo dentro de un rectángulo: el texto es la minoría de píxeles más
 * alejada de la luminancia típica (letras claras sobre fondo oscuro o al revés). Borrar solo
 * los trazos, y no el rectángulo entero, deja mucho más fondo real alrededor para rellenar.
 */
internal object TextMask {

    /**
     * Tinta del texto: su color y la luminancia típica del fondo. Un píxel es "tinta" si está
     * cerca del texto en el camino entre fondo y texto (más claro si las letras son claras) y tiene
     * una saturación parecida: así entran el blanco puro de un título y el gris claro antialias
     * de un subtítulo, pero no el fuego naranja de alrededor.
     */
    class Ink(val color: Int, background: Int) {
        private val light = luma(color) > background
        private val threshold = background + ((luma(color) - background) * INK_FACTOR).toInt()
        private val chroma = chroma(color)

        fun matches(pixel: Int): Boolean {
            val l = luma(pixel)
            val beyond = if (light) l >= threshold else l <= threshold
            return beyond && abs(chroma(pixel) - chroma) <= CHROMA_TOLERANCE
        }
    }

    /** Tinta del texto dentro de [left]..[right) × [top]..[bottom), o null si no destaca del fondo. */
    fun ink(pixels: IntArray, width: Int, left: Int, top: Int, right: Int, bottom: Int, knownColor: Int? = null): Ink? {
        val lumas = ArrayList<Int>((right - left) * (bottom - top))
        for (y in top until bottom) for (x in left until right) lumas += luma(pixels[y * width + x])
        if (lumas.size < MIN_PIXELS) return null
        lumas.sort()
        val median = lumas[lumas.size / 2]
        val color = knownColor ?: extremeColor(pixels, width, left, top, right, bottom, lumas, median) ?: return null
        return if (abs(luma(color) - median) >= MIN_CONTRAST) Ink(color, median) else null
    }

    /** Color medio de la minoría de píxeles más alejada de la mediana (las letras). */
    private fun extremeColor(
        pixels: IntArray, width: Int, left: Int, top: Int, right: Int, bottom: Int,
        sortedLumas: List<Int>, median: Int,
    ): Int? {
        val light = sortedLumas.last() - median >= median - sortedLumas.first()
        val threshold = if (light) sortedLumas[(sortedLumas.size * (1f - EXTREME)).toInt()] else sortedLumas[(sortedLumas.size * EXTREME).toInt()]
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var n = 0
        for (y in top until bottom) for (x in left until right) {
            val c = pixels[y * width + x]
            val l = luma(c)
            if (if (light) l >= threshold else l <= threshold) {
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF; n++
            }
        }
        if (n == 0) return null
        return (0xFF shl 24) or ((sr / n).toInt() shl 16) or ((sg / n).toInt() shl 8) or (sb / n).toInt()
    }

    /**
     * Máscara de los trazos del texto dentro del rectángulo, ensanchada [grow] píxeles para
     * cubrir el antialias, contornos y sombras. [knownColor] es el color de las letras si ya se
     * conoce. Null si el texto no se distingue del fondo.
     */
    fun build(
        pixels: IntArray, width: Int, height: Int,
        left: Int, top: Int, right: Int, bottom: Int,
        grow: Int, knownColor: Int?,
    ): BooleanArray? {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        val ink = ink(pixels, width, l, t, r, b, knownColor) ?: return null
        val mask = BooleanArray(width * height)
        var any = false
        for (y in t until b) for (x in l until r) {
            if (ink.matches(pixels[y * width + x])) {
                mask[y * width + x] = true
                any = true
            }
        }
        if (!any) return null
        return dilate(mask, width, height, max(1, grow), l, t, r, b)
    }

    /** Dilatación cuadrada separable, limitada al rectángulo ampliado en [radius]. */
    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int, l: Int, t: Int, r: Int, b: Int): BooleanArray {
        val x0 = max(0, l - radius)
        val x1 = min(width, r + radius)
        val y0 = max(0, t - radius)
        val y1 = min(height, b + radius)
        val horizontal = BooleanArray(mask.size)
        for (y in y0 until y1) for (x in x0 until x1) {
            if (!mask[y * width + x]) continue
            for (xx in max(x0, x - radius) until min(x1, x + radius + 1)) horizontal[y * width + xx] = true
        }
        val out = BooleanArray(mask.size)
        for (y in y0 until y1) for (x in x0 until x1) {
            if (!horizontal[y * width + x]) continue
            for (yy in max(y0, y - radius) until min(y1, y + radius + 1)) out[yy * width + x] = true
        }
        return out
    }

    fun luma(c: Int): Int = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    private fun chroma(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return max(r, max(g, b)) - min(r, min(g, b))
    }

    private const val MIN_PIXELS = 8
    /** Fracción de píxeles más extremos que se toma como muestra del color del texto. */
    private const val EXTREME = 0.06f
    /** Diferencia mínima de luminancia entre texto y fondo. */
    private const val MIN_CONTRAST = 40
    /** Diferencia de saturación admitida entre un píxel y el color del texto. */
    private const val CHROMA_TOLERANCE = 40
    /** Fracción del camino de la luminancia del fondo a la del texto a partir de la cual hay tinta. */
    private const val INK_FACTOR = 0.65f
}
