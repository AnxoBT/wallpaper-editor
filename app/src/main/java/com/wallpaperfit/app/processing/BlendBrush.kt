package com.wallpaperfit.app.processing

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pinceles del Corrector para retocar transiciones y texturas a mano (por ejemplo, tras Expandir).
 * - [mix] funde los colores de la zona: disimula uniones y crea degradados.
 * - [shuffle] revuelve lo que hay bajo el pincel, con un desplazamiento suave y sin borrar el
 *   grano ni las estrellas: rompe repeticiones y patrones sin dejarlo borroso.
 */
internal object BlendBrush {

    fun mix(pixels: IntArray, width: Int, height: Int, radius: Float): IntArray {
        val out = pixels.copyOf()
        val box = max(1, (radius * MIX_RADIUS).roundToInt())
        val buffer = IntArray(pixels.size)
        repeat(MIX_PASSES) {
            blurRows(out, buffer, width, height, box)
            blurColumns(buffer, out, width, height, box)
        }
        return out
    }

    /** [seed]: el mismo trazo da siempre el mismo resultado, también al rehacer la edición. */
    fun shuffle(pixels: IntArray, width: Int, height: Int, radius: Float, seed: Int): IntArray {
        val random = Random(seed)
        val out = IntArray(pixels.size)
        val coarse = NoiseField(width, height, max(2f, radius), random)
        val coarseY = NoiseField(width, height, max(2f, radius), random)
        val fine = NoiseField(width, height, max(2f, radius * FINE_CELL), random)
        val fineY = NoiseField(width, height, max(2f, radius * FINE_CELL), random)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = coarse.at(x, y) * radius * COARSE_AMOUNT + fine.at(x, y) * radius * FINE_AMOUNT
                val dy = coarseY.at(x, y) * radius * COARSE_AMOUNT + fineY.at(x, y) * radius * FINE_AMOUNT
                val sx = (x + dx.roundToInt()).coerceIn(0, width - 1)
                val sy = (y + dy.roundToInt()).coerceIn(0, height - 1)
                out[y * width + x] = pixels[sy * width + sx]
            }
        }
        return out
    }

    /** Máscara con el borde fundido hacia fuera: el pincel no deja un corte visible. */
    fun soften(alpha: IntArray, width: Int, height: Int, radius: Float): IntArray {
        val out = IntArray(alpha.size) { alpha[it] shl 24 }
        val buffer = IntArray(alpha.size)
        val box = max(1, (radius * SOFT_EDGE).roundToInt())
        repeat(SOFT_PASSES) {
            blurRows(out, buffer, width, height, box)
            blurColumns(buffer, out, width, height, box)
        }
        // El centro del trazo queda a pleno: solo se funde el borde.
        return IntArray(alpha.size) { min(255, (out[it] ushr 24) * 2) }
    }

    /** Ruido suave de -1 a 1: valores al azar en una rejilla, interpolados. */
    private class NoiseField(width: Int, height: Int, private val cell: Float, random: Random) {
        private val columns = (width / cell).toInt() + 2
        private val rows = (height / cell).toInt() + 2
        private val values = FloatArray(columns * rows) { random.nextFloat() * 2f - 1f }

        fun at(x: Int, y: Int): Float {
            val fx = x / cell
            val fy = y / cell
            val cx = floor(fx).toInt()
            val cy = floor(fy).toInt()
            val tx = smooth(fx - cx)
            val ty = smooth(fy - cy)
            val top = values[cy * columns + cx] * (1 - tx) + values[cy * columns + cx + 1] * tx
            val bottom = values[(cy + 1) * columns + cx] * (1 - tx) + values[(cy + 1) * columns + cx + 1] * tx
            return top * (1 - ty) + bottom * ty
        }

        private fun smooth(t: Float) = t * t * (3f - 2f * t)
    }

    private fun blurRows(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val divisor = 2 * radius + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, width - 1)]
                a += p ushr 24
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
                val add = src[row + min(x + radius + 1, width - 1)]
                val remove = src[row + max(x - radius, 0)]
                a += (add ushr 24) - (remove ushr 24)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun blurColumns(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val divisor = 2 * radius + 1
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, height - 1) * width + x]
                a += p ushr 24
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
                val add = src[min(y + radius + 1, height - 1) * width + x]
                val remove = src[max(y - radius, 0) * width + x]
                a += (add ushr 24) - (remove ushr 24)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private const val MIX_RADIUS = 0.6f
    private const val MIX_PASSES = 3
    private const val SOFT_EDGE = 0.35f
    private const val SOFT_PASSES = 2
    private const val FINE_CELL = 0.2f
    private const val COARSE_AMOUNT = 1.2f
    private const val FINE_AMOUNT = 0.4f
}
