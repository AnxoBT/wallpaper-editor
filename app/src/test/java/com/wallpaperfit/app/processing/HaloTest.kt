package com.wallpaperfit.app.processing

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Caso real reportado: foto en blanco y negro con hojas sueltas sobre fondo liso. Al expandir,
 * el fondo no debe tener halos.
 */
class HaloTest {

    private val backgroundLevel = 200
    private val width = 900
    private val height = 1400
    private val holeRows = 560

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun scene(): Pair<IntArray, BooleanArray> {
        val random = Random(5)
        val pixels = IntArray(width * height) { gray(backgroundLevel) }
        repeat(width * height / 3600) {
            val cx = random.nextInt(width)
            val cy = random.nextInt(height)
            val r = 5 + random.nextInt(5)
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                if (x in 0 until width && y in 0 until height && (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) {
                    pixels[y * width + x] = gray(40)
                }
            }
        }
        for (i in 0 until holeRows * width) pixels[i] = 0
        return pixels to BooleanArray(width * height) { it / width < holeRows }
    }

    /** Desviación p99 del fondo (sin contar las hojas) dentro del relleno. */
    private fun backgroundP99(filled: IntArray): Int {
        val deviations = (0 until holeRows * width).map { filled[it] and 0xFF }.filter { it > 120 }
            .map { abs(it - backgroundLevel) }.sorted()
        return deviations[(deviations.size * 0.99).toInt()]
    }

    @Test
    fun textureModeHasNoHalos() {
        val (pixels, hole) = scene()
        val filled = DetailFill.fill(pixels, width, height, hole, 400, DetailFill.Mode.NATURAL) {}
        val p99 = backgroundP99(filled)
        println("textura: halo p99=$p99")
        assertTrue("halos en el fondo: p99=$p99", p99 <= 6)
    }
}
