package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.wallpaperfit.app.model.FitSettings
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.FrameTransform
import kotlin.math.max
import kotlin.math.min

/**
 * Lleva la imagen al lienzo de las dimensiones exactas pedidas: primero el relleno de fondo
 * (desenfoque, blanco o negro) y encima la imagen con la posición, zoom y giro elegidos.
 * Todo es proporcional al lienzo, así que la vista previa y la exportación coinciden.
 */
class ImageProcessor(private val blurEngine: BlurEngine) {

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun render(source: Bitmap, outWidth: Int, outHeight: Int, fit: FitSettings): Bitmap {
        require(outWidth > 0 && outHeight > 0) { "Dimensiones de salida no válidas: ${outWidth}x$outHeight" }
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        when (fit.fill) {
            FrameFill.WHITE -> canvas.drawColor(Color.WHITE)
            FrameFill.BLACK -> canvas.drawColor(Color.BLACK)
            FrameFill.BLUR -> drawBlurredBackground(canvas, source)
        }
        drawTransformed(canvas, source, fit.transform)
        // El resultado es siempre opaco: el PNG se guarda en RGB y ocupa menos.
        output.setHasAlpha(false)
        return output
    }

    /**
     * Dibuja la imagen con su matriz de encuadre. Si se va a reducir más de la mitad, antes se
     * reduce por mitades para evitar el aliasing del filtrado bilineal.
     */
    private fun drawTransformed(canvas: Canvas, source: Bitmap, transform: FrameTransform) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val effectiveScale = Geometry.coverScale(source.width, source.height, width, height) * transform.scale
        var image = source
        while (effectiveScale * source.width / image.width <= 0.5f && image.width > 1 && image.height > 1) {
            val next = Bitmap.createScaledBitmap(image, image.width / 2, image.height / 2, true)
            if (image !== source) image.recycle()
            image = next
        }
        val matrix = Geometry.frameMatrix(source.width, source.height, width, height, transform)
        matrix.preScale(source.width / image.width.toFloat(), source.height / image.height.toFloat())
        canvas.drawBitmap(image, matrix, bitmapPaint)
        if (image !== source) image.recycle()
    }

    /** La propia imagen, cubriendo el lienzo, reducida, desenfocada, ampliada y algo oscurecida. */
    private fun drawBlurredBackground(canvas: Canvas, source: Bitmap) {
        val outWidth = canvas.width
        val outHeight = canvas.height
        val smallWidth = max(1, outWidth / BLUR_DOWNSAMPLE)
        val smallHeight = max(1, outHeight / BLUR_DOWNSAMPLE)
        val small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        val cover = Geometry.coverRect(source.width, source.height, smallWidth.toFloat(), smallHeight.toFloat())
        BitmapScaler.drawScaled(Canvas(small), source, cover, bitmapPaint)

        val radius = max(1f, min(smallWidth, smallHeight) * BLUR_BACKGROUND_FRACTION)
        val blurred = blurEngine.blur(small, radius)
        small.recycle()

        canvas.drawBitmap(blurred, null, Rect(0, 0, outWidth, outHeight), bitmapPaint)
        blurred.recycle()
        canvas.drawColor(BLUR_BACKGROUND_DIM)
    }

    private companion object {
        const val BLUR_DOWNSAMPLE = 8
        const val BLUR_BACKGROUND_FRACTION = 0.12f
        val BLUR_BACKGROUND_DIM = Color.argb(56, 0, 0, 0)
    }
}
