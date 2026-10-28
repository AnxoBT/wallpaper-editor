package com.wallpaperfit.app.processing

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Corrige la diferencia de tono general entre la foto y el relleno de Expandir.
 *
 * El relleno copia trozos de otras zonas de la foto, que pueden ser algo más claros u oscuros que
 * el borde junto al que acaban (por ejemplo, en un degradado de cielo). Para cada lado expandido
 * se compara el color medio de una franja de la foto con el de la franja de relleno contigua y la
 * diferencia se suma al relleno con un peso que decae al alejarse de la foto.
 *
 * Solo se corrige el tono de baja frecuencia: las franjas son anchas, la diferencia se suaviza
 * mucho a lo largo del borde y se limita, para que estrellas, nubes o bordes que toquen la unión
 * no generen rayas.
 */
internal object SeamHarmonizer {

    private const val MIN_BAND = 6
    private const val MAX_BAND = 40
    private const val BAND_FRACTION = 0.05f
    private const val MIN_SMOOTH_RADIUS = 16
    private const val SMOOTH_FRACTION = 0.06f
    private const val MAX_DELTA = 40f
    private const val DECAY_FRACTION = 0.5f
    private const val MIN_DECAY = 8f

    fun harmonize(pixels: IntArray, width: Int, height: Int, hole: BooleanArray) {
        // Rectángulo de la foto = caja de los píxeles conocidos.
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!hole[y * width + x]) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return

        val photoWidth = right - left + 1
        val photoHeight = bottom - top + 1
        if (top > 0) {
            correct(pixels, width, length = width, gap = top, photoDepth = photoHeight, horizontalEdge = true, edge = top, outward = -1, spanStart = left, spanEnd = right)
        }
        if (bottom < height - 1) {
            correct(pixels, width, length = width, gap = height - 1 - bottom, photoDepth = photoHeight, horizontalEdge = true, edge = bottom, outward = 1, spanStart = left, spanEnd = right)
        }
        if (left > 0) {
            correct(pixels, width, length = height, gap = left, photoDepth = photoWidth, horizontalEdge = false, edge = left, outward = -1, spanStart = top, spanEnd = bottom)
        }
        if (right < width - 1) {
            correct(pixels, width, length = height, gap = width - 1 - right, photoDepth = photoWidth, horizontalEdge = false, edge = right, outward = 1, spanStart = top, spanEnd = bottom)
        }
    }

    /**
     * [edge] es la última fila/columna de la foto en ese lado; [outward] es la dirección hacia el
     * relleno. [length] recorre el borde; [spanStart]..[spanEnd] es la parte del borde con foto.
     */
    private fun correct(
        pixels: IntArray,
        width: Int,
        length: Int,
        gap: Int,
        photoDepth: Int,
        horizontalEdge: Boolean,
        edge: Int,
        outward: Int,
        spanStart: Int,
        spanEnd: Int,
    ) {
        fun index(along: Int, across: Int): Int = if (horizontalEdge) across * width + along else along * width + across

        val band = (min(gap, photoDepth) * BAND_FRACTION).toInt().coerceIn(MIN_BAND, MAX_BAND)
            .coerceAtMost(min(gap, photoDepth))
        val deltas = Array(3) { FloatArray(length) }
        for (a in spanStart..spanEnd) {
            for (c in 0..2) {
                var photo = 0f
                var fill = 0f
                for (k in 0 until band) {
                    photo += channel(pixels[index(a, edge - outward * k)], c)
                    fill += channel(pixels[index(a, edge + outward * (k + 1))], c)
                }
                deltas[c][a] = (photo - fill) / band
            }
        }
        val radius = max(MIN_SMOOTH_RADIUS, (length * SMOOTH_FRACTION).toInt())
        for (c in 0..2) {
            val d = deltas[c]
            for (a in 0 until spanStart) d[a] = d[spanStart]
            for (a in spanEnd + 1 until length) d[a] = d[spanEnd]
            // Dos pasadas de media móvil ≈ suavizado gaussiano.
            smooth(d, radius)
            smooth(d, radius)
            for (a in d.indices) d[a] = d[a].coerceIn(-MAX_DELTA, MAX_DELTA)
        }

        val decay = max(MIN_DECAY, gap * DECAY_FRACTION)
        for (step in 1..gap) {
            val across = edge + outward * step
            val weight = exp(-(step - 1) / decay)
            for (a in 0 until length) {
                val p = index(a, across)
                pixels[p] = shift(pixels[p], deltas, a, weight)
            }
        }
    }

    private fun channel(color: Int, c: Int): Float = ((color shr (16 - 8 * c)) and 0xFF).toFloat()

    private fun shift(color: Int, deltas: Array<FloatArray>, index: Int, weight: Float): Int {
        val r = (((color shr 16) and 0xFF) + deltas[0][index] * weight).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) + deltas[1][index] * weight).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) + deltas[2][index] * weight).toInt().coerceIn(0, 255)
        return (color and -0x1000000) or (r shl 16) or (g shl 8) or b
    }

    /** Media móvil con suma acumulada: coste lineal aunque el radio sea grande. */
    private fun smooth(values: FloatArray, radius: Int) {
        val n = values.size
        if (n < 3) return
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + values[i]
        for (i in 0 until n) {
            val from = max(0, i - radius)
            val to = min(n - 1, i + radius)
            values[i] = ((prefix[to + 1] - prefix[from]) / (to - from + 1)).toFloat()
        }
    }
}
