package com.wallpaperfit.app.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val installedAt = 1_000_000L

    @Test
    fun newerApkUploadedLaterIsAnUpdate() {
        assertTrue(UpdateChecker.isNewer(assetSize = 62_300_000, assetTime = installedAt + 1, installedSize = 62_200_000, installedAt = installedAt))
    }

    @Test
    fun theSameApkIsNot() {
        // La misma versión: mismo tamaño, aunque se subiera después de instalarla.
        assertFalse(UpdateChecker.isNewer(assetSize = 62_200_000, assetTime = installedAt + 1, installedSize = 62_200_000, installedAt = installedAt))
    }

    @Test
    fun anOlderApkIsNot() {
        // Instalada a mano una versión más reciente que la publicada.
        assertFalse(UpdateChecker.isNewer(assetSize = 62_300_000, assetTime = installedAt - 1, installedSize = 62_200_000, installedAt = installedAt))
        assertFalse(UpdateChecker.isNewer(assetSize = 0, assetTime = installedAt + 1, installedSize = 62_200_000, installedAt = installedAt))
    }
}
