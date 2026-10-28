package com.wallpaperfit.app.processing

import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FilterSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltersTest {

    private val color = (0xFF shl 24) or (200 shl 16) or (100 shl 8) or 30

    @Test
    fun invertProducesComplement() {
        val pixels = intArrayOf(color)
        Filters.apply(pixels, FilterSettings(FilterPreset.INVERT))
        assertEquals((0xFF shl 24) or (55 shl 16) or (155 shl 8) or 225, pixels[0])
    }

    @Test
    fun blackAndWhiteHasNoColor() {
        val pixels = intArrayOf(color)
        Filters.apply(pixels, FilterSettings(FilterPreset.BW))
        val r = (pixels[0] shr 16) and 0xFF
        val g = (pixels[0] shr 8) and 0xFF
        val b = pixels[0] and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
    }

    @Test
    fun zeroIntensityKeepsOriginal() {
        val pixels = intArrayOf(color)
        Filters.apply(pixels, FilterSettings(FilterPreset.SEPIA, intensity = 0f))
        assertEquals(color, pixels[0])
    }
}
