package com.wallpaperfit.app.processing

import android.graphics.Matrix
import android.graphics.RectF
import com.wallpaperfit.app.model.FrameTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Cálculos de encuadre compartidos por el renderizado final y la vista previa de Compose. */
object Geometry {

    /** Escala absoluta con la que la imagen (sin girar) cubre justo el lienzo. */
    fun coverScale(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): Float =
        max(dstWidth / srcWidth, dstHeight / srcHeight)

    /** Rectángulo centrado donde la imagen se ve entera, escalada para cubrir (modo "cover"). */
    fun coverRect(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): RectF {
        val scale = coverScale(srcWidth, srcHeight, dstWidth, dstHeight)
        val width = srcWidth * scale
        val height = srcHeight * scale
        val left = (dstWidth - width) / 2f
        val top = (dstHeight - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    /**
     * Matriz que lleva píxeles de la imagen al lienzo de [outWidth] × [outHeight]:
     * centra la imagen, la voltea, la escala, la gira y la desplaza según [transform].
     * La vista previa de Compose aplica exactamente las mismas operaciones.
     */
    fun frameMatrix(srcWidth: Int, srcHeight: Int, outWidth: Float, outHeight: Float, transform: FrameTransform): Matrix {
        val scale = coverScale(srcWidth, srcHeight, outWidth, outHeight) * transform.scale
        return Matrix().apply {
            setTranslate(-srcWidth / 2f, -srcHeight / 2f)
            if (transform.flipHorizontal) postScale(-1f, 1f)
            postScale(scale, scale)
            postRotate(transform.rotation)
            postTranslate(outWidth / 2f + transform.offsetX * outWidth, outHeight / 2f + transform.offsetY * outHeight)
        }
    }

    /** Caja que ocupa la imagen (ya girada) dentro del lienzo, en píxeles del lienzo. */
    fun imageBounds(srcWidth: Int, srcHeight: Int, outWidth: Float, outHeight: Float, transform: FrameTransform): RectF {
        val bounds = RectF(0f, 0f, srcWidth.toFloat(), srcHeight.toFloat())
        frameMatrix(srcWidth, srcHeight, outWidth, outHeight, transform).mapRect(bounds)
        return bounds
    }

    /** Escala relativa con la que la imagen girada se ve entera dentro del recuadro. */
    fun fitScale(srcWidth: Int, srcHeight: Int, outWidth: Float, outHeight: Float, rotation: Float): Float {
        val (c, s) = cosSin(rotation)
        val boundsWidth = srcWidth * c + srcHeight * s
        val boundsHeight = srcWidth * s + srcHeight * c
        val absolute = min(outWidth / boundsWidth, outHeight / boundsHeight)
        return absolute / coverScale(srcWidth, srcHeight, outWidth, outHeight)
    }

    /** Escala relativa con la que la imagen girada cubre todo el recuadro, sin huecos. */
    fun fillScale(srcWidth: Int, srcHeight: Int, outWidth: Float, outHeight: Float, rotation: Float): Float {
        val (c, s) = cosSin(rotation)
        val frameWidth = outWidth * c + outHeight * s
        val frameHeight = outWidth * s + outHeight * c
        val absolute = max(frameWidth / srcWidth, frameHeight / srcHeight)
        return absolute / coverScale(srcWidth, srcHeight, outWidth, outHeight)
    }

    private fun cosSin(degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return abs(cos(radians)).toFloat() to abs(sin(radians)).toFloat()
    }
}
