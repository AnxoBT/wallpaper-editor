package com.wallpaperfit.app.ui.wallpapers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniaturas de las fotos de los fondos, cacheadas en memoria (solo mientras la app está abierta). */
private object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    suspend fun load(context: Context, uri: String, side: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$side"
        cache.get(key)?.let { return@withContext it }
        val parsed = Uri.parse(uri)
        // La miniatura del sistema es lo más rápido; si el proveedor no la da, se decodifica reducida.
        val bitmap = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.contentResolver.loadThumbnail(parsed, Size(side, side), null)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }) ?: try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            null
        }
        bitmap?.also { cache.put(key, it) }
    }

    fun key(uri: String, side: Int) = "$uri@$side"
}

/** Imagen de una foto de la galería cargada en segundo plano, recortada para llenar [modifier]. */
@Composable
fun UriImage(uri: String?, modifier: Modifier = Modifier, side: Int = 320, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(
        initialValue = uri?.let { ThumbnailCache.get(ThumbnailCache.key(it, side))?.asImageBitmap() },
        uri,
        side,
    ) {
        value = uri?.let { ThumbnailCache.load(context, it, side)?.asImageBitmap() }
    }
    Box(modifier.background(CanvasBackground)) {
        image?.let {
            Image(bitmap = it, contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize())
        }
    }
}
