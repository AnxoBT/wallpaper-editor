package com.wallpaperfit.app

import android.content.Context
import com.wallpaperfit.app.ui.settings.AppLanguage
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.wallpaperfit.app.ui.WallpaperFitApp
import com.wallpaperfit.app.ui.theme.WallpaperFitTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // El editor es siempre oscuro: iconos claros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        openWallpapers.value = intent?.getBooleanExtra(EXTRA_OPEN_WALLPAPERS, false) == true
        setContent {
            WallpaperFitTheme {
                WallpaperFitApp(openWallpapers = openWallpapers.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tocar el reloj con la app ya abierta: ir a los ajustes de fondos.
        if (intent.getBooleanExtra(EXTRA_OPEN_WALLPAPERS, false)) {
            openWallpapers.value = false
            openWallpapers.value = true
        }
    }

    private val openWallpapers = mutableStateOf(false)

    companion object {
        const val EXTRA_OPEN_WALLPAPERS = "open_wallpapers"
    }
}
