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

/** El modelo de IA solo existe para móviles: en Windows el Corrector usa el método clásico. */
class AiInpainter(context: Context) : Closeable {
    fun inpaint512(pixels: IntArray, hole: BooleanArray): IntArray = throw UnsupportedOperationException("Sin IA en Windows")

    fun warmUp() = Unit

    override fun close() = Unit

    companion object {
        const val SIZE = 512
    }
}

/** El reconocimiento de texto solo existe para móviles: en Windows el botón no se muestra. */
object TextDetector {
    class Found(val box: RectF, val color: Int, val lineHeight: Float)

    suspend fun detectLines(bitmap: Bitmap): List<RectF> = emptyList()

    fun withNearbyMarks(bitmap: Bitmap, lines: List<RectF>): List<Found> = emptyList()
}
