package com.wallpaperfit.app.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.wallpaperfit.app.model.ImageLayer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Imágenes de las capas ya cargadas en memoria (a un tamaño de sobra para cualquier pantalla) y
 * su dibujo sobre el lienzo. Se cargan al añadir la capa y se quedan mientras la app está abierta.
 */
object LayerImages {

    private val images = ConcurrentHashMap<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun get(uri: String): Bitmap? = images[uri]

    suspend fun load(context: Context, uri: String): Bitmap {
        images[uri]?.let { return it }
        val bitmap = BitmapScaler.downscaleToMaxSide(ImageLoader.load(context, Uri.parse(uri)), MAX_SIDE)
        images[uri] = bitmap
        return bitmap
    }

    /** Dibuja [layers] (de abajo arriba) sobre [canvas], que mide [width] × [height]. */
    fun draw(canvas: Canvas, width: Int, height: Int, layers: List<ImageLayer>) {
        for (layer in layers) {
            val bitmap = images[layer.uri] ?: continue
            val dstWidth = layer.width * width
            val scale = dstWidth / bitmap.width
            val matrix = Matrix().apply {
                postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                postScale(if (layer.flipHorizontal) -scale else scale, scale)
                postRotate(layer.rotation)
                postTranslate(layer.centerX * width, layer.centerY * height)
            }
            paint.alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
        paint.alpha = 255
    }

    fun clear() {
        images.clear()
    }

    /** Lado mayor de cada capa en memoria (sobra para pantallas de 2400 px). */
    private const val MAX_SIDE = 2400
}
