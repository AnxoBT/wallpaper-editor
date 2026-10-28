package com.wallpaperfit.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTest {

    private fun at(dayOfWeek: Int, hour: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
        set(Calendar.HOUR_OF_DAY, hour)
    }

    @Test
    fun dayAndNightSplitAtEightAndTwenty() {
        assertTrue(SourceSchedule.DAY.isActive(at(Calendar.MONDAY, 8)))
        assertTrue(SourceSchedule.DAY.isActive(at(Calendar.MONDAY, 19)))
        assertFalse(SourceSchedule.DAY.isActive(at(Calendar.MONDAY, 20)))
        assertTrue(SourceSchedule.NIGHT.isActive(at(Calendar.MONDAY, 23)))
        assertTrue(SourceSchedule.NIGHT.isActive(at(Calendar.MONDAY, 7)))
    }

    @Test
    fun weekdaysAndWeekend() {
        assertTrue(SourceSchedule.WEEKDAYS.isActive(at(Calendar.FRIDAY, 12)))
        assertFalse(SourceSchedule.WEEKDAYS.isActive(at(Calendar.SATURDAY, 12)))
        assertTrue(SourceSchedule.WEEKEND.isActive(at(Calendar.SUNDAY, 12)))
    }

    @Test
    fun activeSourcesFallBackToAllWhenNoneMatches() {
        val day = WallpaperSource("a", true, "A", SourceSchedule.DAY)
        val night = WallpaperSource("b", true, "B", SourceSchedule.NIGHT)
        val config = WallpaperConfig(sources = listOf(day, night))
        assertEquals(listOf(day), config.activeSources(at(Calendar.MONDAY, 12)))
        assertEquals(listOf(night), config.activeSources(at(Calendar.MONDAY, 22)))
        val onlyWeekend = WallpaperConfig(sources = listOf(WallpaperSource("c", true, "C", SourceSchedule.WEEKEND)))
        assertEquals(onlyWeekend.sources, onlyWeekend.activeSources(at(Calendar.MONDAY, 12)))
    }
}
