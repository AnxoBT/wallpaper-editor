package com.wallpaperfit.app

import android.app.Application
import android.content.Context
import com.wallpaperfit.app.ui.settings.AppLanguage

/**
 * La app entera usa el idioma elegido en Ajustes: también los avisos que preparan los ViewModel, el
 * widget y el fondo dinámico (no solo las pantallas). En Android 13+ ya lo hace el sistema.
 */
class WallpaperFitApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
