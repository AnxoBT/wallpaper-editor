package com.wallpaperfit.app.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/** Decodifica las fotos con Skia, con su orientación. */
object ImageLoader {
    const val MAX_SOURCE_SIDE = 4096

    suspend fun load(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("No se puede abrir $uri")
        val full = BitmapFactory.decode(bytes, null) ?: throw IOException("Formato de imagen no soportado")
        BitmapScaler.downscaleToMaxSide(full, MAX_SOURCE_SIDE).also { if (it !== full) full.recycle() }
    }
}
