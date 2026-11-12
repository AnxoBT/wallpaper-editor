package com.wallpaperfit.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun higherVersionIsAnUpdate() {
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("v1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.12.3"))
        // Versiones de distinta longitud: 1.0 = 1.0.0.
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0"))
    }

    @Test
    fun sameOrOlderVersionIsNot() {
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.1"))
        // Números, no texto: 1.10 es mayor que 1.9.
        assertFalse(UpdateChecker.isNewer("v1.9.0", "1.10.0"))
    }

    @Test
    fun unreadableVersionsAreNotUpdates() {
        assertFalse(UpdateChecker.isNewer("", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("latest", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.1.0", ""))
    }

    @Test
    fun suffixesAreIgnored() {
        assertTrue(UpdateChecker.isNewer("v1.1.0-beta", "1.0.0"))
    }
}
