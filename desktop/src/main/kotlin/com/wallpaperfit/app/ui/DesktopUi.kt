package com.wallpaperfit.app.ui

import android.content.Context
import com.wallpaperfit.app.model.TargetSize
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException

/** Resolución real de la pantalla principal del PC (en horizontal, tal cual). */
object DeviceScreen {
    private val FALLBACK = TargetSize(1920, 1080)

    fun size(context: Context): TargetSize = try {
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        if (mode.width > 0 && mode.height > 0) TargetSize(mode.width, mode.height) else FALLBACK
    } catch (e: HeadlessException) {
        FALLBACK
    }
}
