package com.wallpaperfit.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuplicateFinderTest {

    private val w = DuplicateFinder.WIDTH
    private val h = DuplicateFinder.HEIGHT

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Una "foto" con un degradado y manchas al azar. */
    private fun photo(seed: Int): IntArray {
        val random = Random(seed)
        val spots = List(6) { Triple(random.nextInt(w), random.nextInt(h), random.nextInt(256)) }
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val spot = spots.sumOf { (sx, sy, v) -> if (kotlin.math.abs(sx - x) + kotlin.math.abs(sy - y) < 3) v else 0 }
            rgb(x * 14 + spot / 3, y * 12, 200 - x * 5 + spot / 4)
        }
    }

    @Test
    fun theSamePhotoWithNoiseIsADuplicate() {
        val random = Random(7)
        val original = photo(1)
        // Lo que cambia al guardarla en otro tamaño o con otra compresión: un poco de ruido.
        val resaved = IntArray(original.size) { i ->
            val c = original[i]
            fun n() = random.nextInt(-1, 2)
            rgb((c shr 16 and 0xFF) + n(), (c shr 8 and 0xFF) + n(), (c and 0xFF) + n())
        }
        assertTrue(DuplicateFinder.same(DuplicateFinder.print(original), DuplicateFinder.print(resaved)))
    }

    @Test
    fun differentPhotosAreNot() {
        assertFalse(DuplicateFinder.same(DuplicateFinder.print(photo(1)), DuplicateFinder.print(photo(2))))
    }

    @Test
    fun darkPhotosWithDifferentContentAreNot() {
        // Dos fondos negros con algo distinto encima (como las parejas que no eran repetidas).
        val black = rgb(0, 0, 0)
        val a = IntArray(w * h) { i -> if (i % w in 2..5 && i / w in 6..8) rgb(230, 230, 230) else black }
        val b = IntArray(w * h) { i -> if (i % w in 10..14 && i / w in 3..12) rgb(200, 20, 20) else black }
        assertFalse(DuplicateFinder.same(DuplicateFinder.print(a), DuplicateFinder.print(b)))
    }

    @Test
    fun groupsJoinChainsAndSkipSingles() {
        val p1 = DuplicateFinder.print(photo(1))
        val p2 = DuplicateFinder.print(photo(2))
        val p3 = DuplicateFinder.print(photo(3))
        val groups = DuplicateFinder.groups(listOf("a" to p1, "b" to p2, "c" to p1, "d" to p3, "e" to p2))
        assertEquals(listOf(listOf("a", "c"), listOf("b", "e")), groups)
    }

    @Test
    fun originalIsTheLargestThenTheOldest() {
        data class P(val name: String, val pixels: Long, val modified: Long)
        val small = P("pequeña", 736L * 1634, 1)
        val bigNew = P("grande nueva", 1080L * 2400, 300)
        val bigOld = P("grande antigua", 1080L * 2400, 100)
        assertEquals(bigOld, DuplicateFinder.original(listOf(small, bigNew, bigOld), { it.pixels }, { it.modified }))
    }
}
