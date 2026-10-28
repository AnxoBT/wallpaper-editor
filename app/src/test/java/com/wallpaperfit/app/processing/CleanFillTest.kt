package com.wallpaperfit.app.processing

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** El Corrector debe borrar motas sin copiar otras y sin cambiar el color del fondo. */
class CleanFillTest {

    private val background = 0x5A7FB0

    private fun dustyBackground(size: Int): IntArray {
        val random = Random(11)
        return IntArray(size * size) {
            if (random.nextFloat() < 0.01f) (0xFF shl 24) or 0xF5F5F5 else (0xFF shl 24) or background
        }
    }

    private fun check(size: Int, radius: Int, maxWorkSide: Int) {
        val pixels = dustyBackground(size)
        val hole = BooleanArray(size * size)
        val c = size / 2
        for (y in 0 until size) for (x in 0 until size) {
            if ((x - c) * (x - c) + (y - c) * (y - c) <= radius * radius) hole[y * size + x] = true
        }
        val filled = DetailFill.fill(pixels, size, size, hole, maxWorkSide, DetailFill.Mode.CLEAN) {}
        val holeIdx = hole.indices.filter { hole[it] }
        val specks = holeIdx.count { (filled[it] and 0xFF) > 200 }.toFloat() / holeIdx.size
        val meanBlue = holeIdx.sumOf { filled[it] and 0xFF } / holeIdx.size
        println("limpio ${size}px r=$radius: motas=$specks azul medio=$meanBlue (fondo=${background and 0xFF})")
        assertTrue("quedan motas: $specks", specks < 0.002f)
        assertTrue("color cambiado: $meanBlue", abs(meanBlue - (background and 0xFF)) <= 4)
    }

    @Test
    fun smallStrokeAtFullResolution() = check(size = 300, radius = 40, maxWorkSide = 700)

    @Test
    fun bigStrokeWithDownscaledSearch() = check(size = 1600, radius = 300, maxWorkSide = 700)
}
