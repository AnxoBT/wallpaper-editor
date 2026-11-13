package com.wallpaperfit.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    @Test
    fun showsAfterUpdatingToAnotherVersion() {
        assertTrue(WhatsNew.shouldShow("1.0.0", "1.1.0"))
    }

    @Test
    fun notOnAFreshInstallNorTwice() {
        // Instalación nueva: ya sale la guía de bienvenida.
        assertFalse(WhatsNew.shouldShow(null, "1.0.0"))
        // La misma versión otra vez.
        assertFalse(WhatsNew.shouldShow("1.1.0", "1.1.0"))
        // Sin versión legible.
        assertFalse(WhatsNew.shouldShow("1.0.0", ""))
    }
}
