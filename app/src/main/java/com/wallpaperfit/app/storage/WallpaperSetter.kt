package com.wallpaperfit.app.storage

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import com.wallpaperfit.app.model.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Aplica el resultado como fondo de pantalla del sistema (permiso normal SET_WALLPAPER). */
object WallpaperSetter {

    suspend fun apply(context: Context, bitmap: Bitmap, target: WallpaperTarget) = withContext(Dispatchers.IO) {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
            throw IOException("El sistema no permite cambiar el fondo de pantalla")
        }
        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        val id = manager.setBitmap(bitmap, null, true, flags)
        if (id == 0) throw IOException("WallpaperManager rechazó la imagen")
    }
}
