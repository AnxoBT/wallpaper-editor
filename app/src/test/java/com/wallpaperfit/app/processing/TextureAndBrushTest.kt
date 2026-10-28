package com.wallpaperfit.app.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TextureAndBrushTest {

    private fun gray(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    private fun blue(pixel: Int) = pixel and 0xFF

    /** Lienzo de 200 × 200 con la foto de [color] en el centro y un hueco de 50 a cada lado. */
    private fun canvas(color: (Int, Int) -> Int): Pair<IntArray, BooleanArray> {
        val hole = BooleanArray(200 * 200) { val x = it % 200; val y = it / 200; x < 50 || x >= 150 || y < 50 || y >= 150 }
        val pixels = IntArray(200 * 200) { if (hole[it]) 0 else color(it % 200, it / 200) }
        return pixels to hole
    }

    private val photo = TextureSynth.Box(50, 50, 150, 150)

    @Test
    fun `una foto lisa da un relleno liso`() {
        val (pixels, hole) = canvas { _, _ -> gray(120) }
        val out = TextureSynth.fill(pixels, 200, 200, hole, photo, 10) {}
        // Solo el ruido de un punto que evita los escalones en los degradados.
        for (i in out.indices) assertTrue("$i: ${blue(out[i])}", abs(blue(out[i]) - 120) <= 2)
    }

    @Test
    fun `el relleno continua el degradado del borde`() {
        val (pixels, hole) = canvas { _, y -> gray(y) }
        val out = TextureSynth.fill(pixels, 200, 200, hole, photo, 10) {}
        val high = blue(out[60 * 200 + 10])
        val low = blue(out[140 * 200 + 10])
        assertTrue("$high < $low", high < low)
    }

    @Test
    fun `la foto fuera de la franja que se funde no cambia y el relleno es opaco`() {
        val (pixels, hole) = canvas { x, y -> gray((x * 7 + y * 13) % 256) }
        val out = TextureSynth.fill(pixels, 200, 200, hole, photo, 10) {}
        assertEquals(pixels[100 * 200 + 100], out[100 * 200 + 100])
        assertTrue(out.all { it ushr 24 == 255 })
    }

    @Test
    fun `repetir el relleno da lo mismo`() {
        val (pixels, hole) = canvas { x, y -> gray((x * 7 + y * 13) % 256) }
        assertArrayEquals(TextureSynth.fill(pixels, 200, 200, hole, photo, 10) {}, TextureSynth.fill(pixels, 200, 200, hole, photo, 10) {})
    }

    @Test
    fun `mezclar funde una union dura`() {
        val pixels = IntArray(100 * 100) { gray(if (it % 100 < 50) 0 else 200) }
        val out = BlendBrush.mix(pixels, 100, 100, 20f)
        val middle = blue(out[50 * 100 + 50])
        assertTrue("$middle", middle in 60..140)
    }

    @Test
    fun `revolver no inventa colores y da siempre lo mismo con el mismo trazo`() {
        val pixels = IntArray(100 * 100) { gray(if ((it % 100 + it / 100) % 2 == 0) 0 else 200) }
        val out = BlendBrush.shuffle(pixels, 100, 100, 20f, 7)
        assertTrue(out.all { blue(it) == 0 || blue(it) == 200 })
        assertTrue(!out.contentEquals(pixels))
        assertArrayEquals(out, BlendBrush.shuffle(pixels, 100, 100, 20f, 7))
    }

    @Test
    fun `el borde del pincel se funde`() {
        val alpha = IntArray(100 * 100) { if (it % 100 < 50) 255 else 0 }
        val soft = BlendBrush.soften(alpha, 100, 100, 20f)
        assertEquals(255, soft[50 * 100 + 10])
        assertEquals(0, soft[50 * 100 + 90])
        assertTrue(soft[50 * 100 + 52] in 1..254)
    }
}
