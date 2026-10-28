package com.wallpaperfit.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RotationTest {

    private val images = (1..8).map { "img$it" }

    @Test
    fun previousReturnsExactlyTheEarlierWallpapersEvenInRandomMode() {
        val config = WallpaperConfig(order = RotationOrder.RANDOM, historySize = 3)
        val random = Random(1)
        var state = RotationState()
        val shown = ArrayList<String>()
        repeat(5) {
            state = Rotation.next(state, images, config, random, 0)
            shown += state.current!!
        }
        // Atrás tres veces: las tres anteriores, en orden inverso.
        state = Rotation.previous(state, images, config, 0)!!
        assertEquals(shown[3], state.current)
        state = Rotation.previous(state, images, config, 0)!!
        assertEquals(shown[2], state.current)
        state = Rotation.previous(state, images, config, 0)!!
        assertEquals(shown[1], state.current)
        // Solo se recuerdan tres.
        assertNull(Rotation.previous(state, images, config, 0))

        // Siguiente rehace el camino en vez de sacar otra al azar.
        state = Rotation.next(state, images, config, random, 0)
        assertEquals(shown[2], state.current)
        state = Rotation.next(state, images, config, random, 0)
        assertEquals(shown[3], state.current)
        state = Rotation.next(state, images, config, random, 0)
        assertEquals(shown[4], state.current)
    }

    @Test
    fun randomModeShowsEveryPhotoBeforeRepeating() {
        val config = WallpaperConfig(order = RotationOrder.RANDOM)
        var state = RotationState(current = "img1")
        val seen = HashSet<String>()
        repeat(images.size - 1) {
            state = Rotation.next(state, images, config, Random(7), 0)
            seen += state.current!!
        }
        assertEquals(images.toSet() - "img1", seen)
    }

    @Test
    fun sequentialModeWrapsAround() {
        val config = WallpaperConfig(order = RotationOrder.SEQUENTIAL)
        var state = RotationState(current = "img8")
        state = Rotation.next(state, images, config, Random(0), 0)
        assertEquals("img1", state.current)
    }

    @Test
    fun removedPhotosAreSkipped() {
        val config = WallpaperConfig(historySize = 5)
        var state = RotationState(current = "img3", history = listOf("img1", "img2"))
        state = Rotation.previous(state, images - "img2", config, 0)!!
        assertEquals("img1", state.current)
    }

    @Test
    fun deletingCurrentGoesBackToPreviousThatStillExists() {
        val config = WallpaperConfig(historySize = 5)
        val random = kotlin.random.Random(1)
        val state = RotationState(current = "img3", history = listOf("img1", "img2"))
        // Se borra la actual: vuelve a la anterior y la borrada no queda para "siguiente".
        val back = Rotation.afterRemoved(state, images - "img3", config, random, 0)
        assertEquals("img2", back.current)
        assertEquals(listOf("img1"), back.history)
        assertEquals(emptyList<String>(), back.forward)
        // Si la anterior tampoco existe, la de antes.
        assertEquals("img1", Rotation.afterRemoved(state, images - "img3" - "img2", config, random, 0).current)
        // Sin anteriores: una del pase que no sea la borrada.
        val none = Rotation.afterRemoved(RotationState(current = "img3"), images - "img3", config, random, 0)
        assertTrue(none.current in images - "img3")
    }

    @Test
    fun autoChangeByInterval() {
        val config = WallpaperConfig(autoChange = AutoChange.HOUR_1)
        assertTrue(Rotation.isAutoChangeDue(RotationState(changedAt = 0), config, 3_600_000))
        assertTrue(!Rotation.isAutoChangeDue(RotationState(changedAt = 0), config, 3_599_000))
    }
}
