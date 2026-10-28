package com.wallpaperfit.app.processing

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Desenfoque gaussiano local. Usa ScriptIntrinsicBlur de RenderScript (acelerado y presente en
 * todas las versiones de Android aunque esté deprecado desde API 31) y, si el dispositivo no lo
 * soporta, recurre a [BoxBlur] en Kotlin puro.
 *
 * Radios mayores que el máximo del kernel (25 px) se consiguen reduciendo la imagen antes de
 * desenfocar y ampliándola después, lo que además es mucho más rápido.
 */
@Suppress("DEPRECATION")
class BlurEngine(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private var renderScript: RenderScript? = null
    private var renderScriptUnavailable = false

    /** Devuelve una copia desenfocada de [source] con sus mismas dimensiones. */
    @Synchronized
    fun blur(source: Bitmap, radius: Float): Bitmap {
        val input = if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
        if (radius < MIN_RADIUS) return input.copy(Bitmap.Config.ARGB_8888, true)

        val factor = ceil(radius / MAX_KERNEL_RADIUS).toInt().coerceAtLeast(1)
        val work = if (factor > 1) {
            Bitmap.createScaledBitmap(input, max(1, input.width / factor), max(1, input.height / factor), true)
        } else {
            input
        }
        val workRadius = (radius / factor).coerceIn(MIN_RADIUS, MAX_KERNEL_RADIUS)
        val blurred = blurWithRenderScript(work, workRadius)
            ?: BoxBlur.blur(work, workRadius.roundToInt().coerceAtLeast(1))
        if (work !== input) work.recycle()

        return if (factor > 1) {
            Bitmap.createScaledBitmap(blurred, input.width, input.height, true).also {
                if (it !== blurred) blurred.recycle()
            }
        } else {
            blurred
        }
    }

    private fun obtainRenderScript(): RenderScript? {
        if (renderScriptUnavailable) return null
        renderScript?.let { return it }
        return try {
            RenderScript.create(appContext).also { renderScript = it }
        } catch (t: Throwable) {
            Log.w(TAG, "RenderScript no disponible, se usa BoxBlur", t)
            renderScriptUnavailable = true
            null
        }
    }

    private fun blurWithRenderScript(bitmap: Bitmap, radius: Float): Bitmap? {
        val rs = obtainRenderScript() ?: return null
        var input: Allocation? = null
        var output: Allocation? = null
        var script: ScriptIntrinsicBlur? = null
        return try {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            input = Allocation.createFromBitmap(rs, bitmap)
            output = Allocation.createFromBitmap(rs, result)
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius)
            script.setInput(input)
            script.forEach(output)
            output.copyTo(result)
            result
        } catch (t: Throwable) {
            Log.w(TAG, "Fallo de RenderScript, se usa BoxBlur", t)
            renderScriptUnavailable = true
            null
        } finally {
            input?.destroy()
            output?.destroy()
            script?.destroy()
        }
    }

    @Synchronized
    override fun close() {
        renderScript?.destroy()
        renderScript = null
    }

    private companion object {
        const val TAG = "BlurEngine"
        const val MIN_RADIUS = 1f
        const val MAX_KERNEL_RADIUS = 25f
    }
}

/** Aproximación gaussiana mediante tres pasadas de desenfoque de caja separable. */
internal object BoxBlur {

    private const val PASSES = 3

    fun blur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val buffer = IntArray(width * height)
        repeat(PASSES) {
            blurHorizontal(pixels, buffer, width, height, radius)
            blurVertical(buffer, pixels, width, height, radius)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val divisor = 2 * radius + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, width - 1)]
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
                val pIn = src[row + (x + radius + 1).coerceAtMost(width - 1)]
                val pOut = src[row + (x - radius).coerceAtLeast(0)]
                a += (pIn ushr 24) - (pOut ushr 24)
                r += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                g += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                b += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val divisor = 2 * radius + 1
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, height - 1) * width + x]
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = ((a / divisor) shl 24) or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
                val pIn = src[(y + radius + 1).coerceAtMost(height - 1) * width + x]
                val pOut = src[(y - radius).coerceAtLeast(0) * width + x]
                a += (pIn ushr 24) - (pOut ushr 24)
                r += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                g += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                b += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }
}
