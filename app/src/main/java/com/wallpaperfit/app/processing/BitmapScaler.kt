package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Escalado de alta calidad. El filtrado bilineal de Canvas produce aliasing al reducir más de 2x,
 * así que primero se reduce a la mitad sucesivamente y el último paso se hace con filtrado.
 */
object BitmapScaler {

    fun scaleHighQuality(source: Bitmap, width: Int, height: Int): Bitmap {
        if (source.width == width && source.height == height) return source
        var current = source
        while (current.width / 2 >= width && current.height / 2 >= height) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        val result = Bitmap.createScaledBitmap(current, width, height, true)
        if (current !== source && current !== result) current.recycle()
        return result
    }

    fun downscaleToMaxSide(source: Bitmap, maxSide: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxSide) return source
        val scale = maxSide.toFloat() / longest
        return scaleHighQuality(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    /** Dibuja [source] en [dst] (coordenadas flotantes, puede exceder el lienzo) sin aliasing. */
    fun drawScaled(canvas: Canvas, source: Bitmap, dst: RectF, paint: Paint) {
        var current = source
        while (dst.width() <= current.width / 2f && dst.height() <= current.height / 2f && current.width > 1 && current.height > 1) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        canvas.drawBitmap(current, null, dst, paint)
        if (current !== source) current.recycle()
    }
}
