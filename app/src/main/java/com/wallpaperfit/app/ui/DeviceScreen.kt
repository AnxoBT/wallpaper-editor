package com.wallpaperfit.app.ui

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.wallpaperfit.app.model.TargetSize
import kotlin.math.max
import kotlin.math.min

/** Resolución real de la pantalla de este móvil, en vertical (ancho < alto). */
object DeviceScreen {

    fun size(context: Context): TargetSize {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Pantalla completa, incluidas las barras del sistema: lo que ocupa un fondo de pantalla.
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        if (width <= 0 || height <= 0) return TargetSize.FALLBACK
        return TargetSize(min(width, height), max(width, height))
    }
}
