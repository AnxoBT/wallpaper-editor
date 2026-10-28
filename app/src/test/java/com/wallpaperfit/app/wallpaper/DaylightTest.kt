package com.wallpaperfit.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DaylightTest {

    private val brightness = mapOf("clara" to 0.8f, "media" to 0.36f, "oscura" to 0.1f)
    private val images = listOf("clara", "media", "oscura", "sin medir")

    @Test
    fun dayShowsAnyAndNightOnlyDark() {
        assertEquals(images, Daylight.filter(images, day = true) { brightness[it] })
        assertEquals(listOf("media", "oscura", "sin medir"), Daylight.filter(images, day = false) { brightness[it] })
    }

    @Test
    fun countsNightAndDayPhotos() {
        // "sin medir" no cuenta todavía para la noche.
        assertEquals(Daylight.Counts(night = 2, total = 4), Daylight.counts(images) { brightness[it] })
    }

    @Test
    fun noneFittingFallsBackToAll() {
        val onlyLight = listOf("clara")
        assertEquals(onlyLight, Daylight.filter(onlyLight, day = false) { brightness[it] })
    }

    private fun at(hour: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour) }

    @Test
    fun usesTheNightHoursOfTheConfig() {
        val config = WallpaperConfig()
        assertTrue(Daylight.isDay(config, at(8)))
        assertFalse(Daylight.isDay(config, at(20)))
        assertFalse(Daylight.isDay(config, at(3)))
        // Noche de 22 a 7.
        val late = WallpaperConfig(nightStart = 22, nightEnd = 7)
        assertTrue(Daylight.isDay(late, at(21)))
        assertFalse(Daylight.isDay(late, at(23)))
        assertTrue(Daylight.isDay(late, at(7)))
    }

    @Test
    fun nightCanBeWithinTheSameDayOrEmpty() {
        assertTrue(SourceSchedule.isNight(3, start = 1, end = 6))
        assertFalse(SourceSchedule.isNight(7, start = 1, end = 6))
        assertFalse(SourceSchedule.isNight(12, start = 9, end = 9))
    }

    @Test
    fun sourceSchedulesFollowTheConfiguredNight() {
        val day = WallpaperSource("dia", isFolder = true, name = "Día", schedule = SourceSchedule.DAY)
        val night = WallpaperSource("noche", isFolder = true, name = "Noche", schedule = SourceSchedule.NIGHT)
        val config = WallpaperConfig(sources = listOf(day, night), nightStart = 22, nightEnd = 7)
        assertEquals(listOf(day), config.activeSources(at(21)))
        assertEquals(listOf(night), config.activeSources(at(23)))
    }

    @Test
    fun savedConfigKeepsTheOption() {
        assertTrue(WallpaperConfig(matchDaylight = true).copy().matchDaylight)
        assertFalse(WallpaperConfig().matchDaylight)
    }
}
