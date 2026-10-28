package com.wallpaperfit.app.processing

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class InpainterTest {

    /** Degradado vertical con ruido: textura sencilla cuyo relleno es fácil de verificar. */
    private fun gradient(width: Int, height: Int): IntArray {
        val random = Random(1)
        return IntArray(width * height) { i ->
            val y = i / width
            val v = (60 + 120 * y / height + random.nextInt(-6, 7)).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or 255
        }
    }

    private fun timeExpandLike(width: Int, height: Int, holeRows: Int): Long {
        val pixels = gradient(width, height)
        val hole = BooleanArray(width * height) { it / width < holeRows }
        val start = System.nanoTime()
        val result = Inpainter.inpaint(pixels, width, height, hole)
        val ms = (System.nanoTime() - start) / 1_000_000
        // El relleno junto al borde debe continuar el tono de la primera fila conocida.
        val expected = (pixels[holeRows * width + width / 2] shr 16) and 0xFF
        val value = (result[(holeRows - 2) * width + width / 2] shr 16) and 0xFF
        assertTrue("valor $value, esperado ≈ $expected", abs(value - expected) < 20)
        assertTrue(result.all { (it ushr 24) == 0xFF })
        println("inpaint ${width}x$height hueco=$holeRows filas: $ms ms")
        return ms
    }

    @Test
    fun draftExpansionIsFast() {
        timeExpandLike(420, 388, 150)
    }

    @Test
    fun fullExpansionCompletes() {
        timeExpandLike(500, 1100, 600)
    }

    @Test
    fun smallHoleIsFilledFromSurroundings() {
        val width = 200
        val height = 200
        val pixels = IntArray(width * height) { (0xFF shl 24) or 0xF0F0F0 }
        val hole = BooleanArray(width * height)
        for (y in 90 until 110) for (x in 90 until 110) {
            pixels[y * width + x] = (0xFF shl 24) // mancha negra
            hole[y * width + x] = true
        }
        val result = Inpainter.inpaint(pixels, width, height, hole)
        val center = result[100 * width + 100] and 0xFF
        assertTrue("la mancha no se borró: $center", center > 220)
    }
}
