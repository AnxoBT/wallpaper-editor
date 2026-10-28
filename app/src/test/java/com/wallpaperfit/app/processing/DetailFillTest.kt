package com.wallpaperfit.app.processing

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DetailFillTest {

    /** Cielo nocturno: fondo oscuro con estrellas de un píxel. */
    private fun starryNight(width: Int, height: Int, density: Float): IntArray {
        val random = Random(7)
        return IntArray(width * height) {
            if (random.nextFloat() < density) (0xFF shl 24) or 0xF0F0F0 else (0xFF shl 24) or 0x101830
        }
    }

    private fun brightFraction(pixels: IntArray, indices: IntRange): Float =
        indices.count { (pixels[it] and 0xFF) > 180 }.toFloat() / indices.count()

    @Test
    fun expandedStarsStaySharp() {
        val width = 800
        val height = 1400
        val holeRows = 500
        val pixels = starryNight(width, height, 0.01f)
        val hole = BooleanArray(width * height) { it / width < holeRows }
        val start = System.nanoTime()
        val filled = DetailFill.fill(pixels, width, height, hole, maxWorkSide = 400, mode = DetailFill.Mode.NATURAL, checkCancelled = {})
        println("DetailFill ${width}x$height: ${(System.nanoTime() - start) / 1_000_000} ms")

        val known = brightFraction(pixels, holeRows * width until width * height)
        val synthesized = brightFraction(filled, 0 until holeRows * width)
        println("estrellas conocidas=$known sintetizadas=$synthesized")
        // Si el relleno se ampliara desde baja resolución, las estrellas desaparecerían (≈ 0).
        assertTrue("el relleno ha perdido las estrellas: $synthesized", synthesized > known * 0.4f)
    }
}
