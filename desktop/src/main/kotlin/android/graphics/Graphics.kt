package android.graphics

import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.SamplingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap as SkBitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.Shader as SkShader

/*
 * android.graphics sobre Skia, el mismo motor que hay debajo en Android: así el procesado de la
 * app se compila sin cambios y da el mismo resultado. Solo lo que usa la app.
 */

class Bitmap internal constructor(val skia: SkBitmap) {
    enum class Config { ARGB_8888, RGB_565, RGBA_F16 }

    val width: Int get() = skia.width
    val height: Int get() = skia.height
    val config: Config get() = Config.ARGB_8888
    val byteCount: Int get() = width * height * 4

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val bytes = skia.readPixels(unpremul(width, height), width * 4, x, y)
            ?: error("No se pudieron leer los píxeles")
        val source = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        if (stride == width) {
            source.get(pixels, offset, width * height)
        } else {
            for (row in 0 until height) source.get(pixels, offset + row * stride, width)
        }
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val region = fromPixels(pixels, offset, stride, width, height)
        SkCanvas(skia).writePixels(region.skia, x, y)
        region.recycle()
    }

    fun getPixel(x: Int, y: Int): Int = skia.getColor(x, y)

    fun copy(config: Config, mutable: Boolean): Bitmap {
        val copy = createBitmap(width, height, config)
        SkCanvas(copy.skia).writePixels(skia, 0, 0)
        return copy
    }

    fun setHasAlpha(hasAlpha: Boolean) = Unit

    fun recycle() {
        if (!skia.isClosed) skia.close()
    }

    val isRecycled: Boolean get() = skia.isClosed

    internal fun image(): Image = Image.makeFromBitmap(skia)

    enum class CompressFormat { PNG, JPEG, WEBP, WEBP_LOSSLESS, WEBP_LOSSY }

    companion object {
        private fun unpremul(width: Int, height: Int) =
            ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB), width, height)

        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(SkBitmap().apply { allocN32Pixels(width, height, false) })

        fun createBitmap(pixels: IntArray, width: Int, height: Int, config: Config): Bitmap =
            fromPixels(pixels, 0, width, width, height)

        private fun fromPixels(pixels: IntArray, offset: Int, stride: Int, width: Int, height: Int): Bitmap {
            // Skia solo dibuja sobre píxeles premultiplicados y Android los da sin premultiplicar.
            val buffer = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
            val out = buffer.asIntBuffer()
            for (row in 0 until height) {
                for (column in 0 until width) {
                    val p = pixels[offset + row * stride + column]
                    val a = p ushr 24
                    out.put(
                        if (a == 255) {
                            p
                        } else {
                            (a shl 24) or (((p shr 16 and 0xFF) * a / 255) shl 16) or (((p shr 8 and 0xFF) * a / 255) shl 8) or ((p and 0xFF) * a / 255)
                        },
                    )
                }
            }
            val info = ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB), width, height)
            val result = SkBitmap()
            check(result.installPixels(info, buffer.array(), width * 4)) { "No se pudo crear la imagen" }
            return Bitmap(result)
        }

        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, x, y, width, height)
            return createBitmap(pixels, width, height, Config.ARGB_8888)
        }

        fun createScaledBitmap(source: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
            val scaled = createBitmap(width, height, Config.ARGB_8888)
            val canvas = Canvas(scaled)
            val paint = Paint(if (filter) Paint.FILTER_BITMAP_FLAG else 0)
            canvas.drawBitmap(source, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
            return scaled
        }
    }
}

object BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        var outWidth = 0
        var outHeight = 0
    }

    fun decodeStream(stream: java.io.InputStream, padding: Rect?, options: Options?): Bitmap? = decode(stream.readBytes(), options)

    fun decodeStream(stream: java.io.InputStream): Bitmap? = decode(stream.readBytes(), null)

    /** Skia aplica la orientación EXIF al decodificar, como ImageDecoder en Android. */
    fun decode(data: ByteArray, options: Options?): Bitmap? = try {
        Image.makeFromEncoded(data).use { image ->
            options?.outWidth = image.width
            options?.outHeight = image.height
            if (options?.inJustDecodeBounds == true) return null
            val full = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            SkCanvas(full.skia).drawImage(image, 0f, 0f)
            val sample = options?.inSampleSize ?: 1
            if (sample <= 1) {
                full
            } else {
                Bitmap.createScaledBitmap(full, (full.width / sample).coerceAtLeast(1), (full.height / sample).coerceAtLeast(1), true)
                    .also { full.recycle() }
            }
        }
    } catch (e: IllegalArgumentException) {
        null
    }
}

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0

    fun rgb(red: Int, green: Int, blue: Int): Int = argb(255, red, green, blue)

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    fun alpha(color: Int) = color ushr 24

    fun red(color: Int) = color shr 16 and 0xFF

    fun green(color: Int) = color shr 8 and 0xFF

    fun blue(color: Int) = color and 0xFF
}

data class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    fun width() = right - left

    fun height() = bottom - top
}

data class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    fun width() = right - left

    fun height() = bottom - top

    fun centerX() = (left + right) / 2f

    fun centerY() = (top + bottom) / 2f

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    internal fun skia() = SkRect.makeLTRB(left, top, right, bottom)
}

/** Matriz 3 × 3 con las mismas reglas que la de Android (post = después, pre = antes). */
class Matrix {
    private var m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun multiply(a: FloatArray, b: FloatArray) = FloatArray(9) { i ->
        val row = i / 3
        val column = i % 3
        a[row * 3] * b[column] + a[row * 3 + 1] * b[column + 3] + a[row * 3 + 2] * b[column + 6]
    }

    private fun translation(dx: Float, dy: Float) = floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f)

    private fun scaling(sx: Float, sy: Float) = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)

    private fun rotation(degrees: Float): FloatArray {
        val radians = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(radians).toFloat()
        val s = kotlin.math.sin(radians).toFloat()
        return floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)
    }

    fun setScale(sx: Float, sy: Float) {
        m = scaling(sx, sy)
    }

    fun setScale(sx: Float, sy: Float, px: Float, py: Float) {
        m = floatArrayOf(sx, 0f, px - sx * px, 0f, sy, py - sy * py, 0f, 0f, 1f)
    }

    fun setTranslate(dx: Float, dy: Float) {
        m = translation(dx, dy)
    }

    fun postTranslate(dx: Float, dy: Float): Boolean {
        m = multiply(translation(dx, dy), m)
        return true
    }

    fun postScale(sx: Float, sy: Float): Boolean {
        m = multiply(scaling(sx, sy), m)
        return true
    }

    fun postRotate(degrees: Float): Boolean {
        m = multiply(rotation(degrees), m)
        return true
    }

    fun preScale(sx: Float, sy: Float): Boolean {
        m = multiply(m, scaling(sx, sy))
        return true
    }

    fun preTranslate(dx: Float, dy: Float): Boolean {
        m = multiply(m, translation(dx, dy))
        return true
    }

    fun mapRect(rect: RectF): Boolean {
        val xs = listOf(rect.left, rect.right, rect.right, rect.left)
        val ys = listOf(rect.top, rect.top, rect.bottom, rect.bottom)
        val mappedX = xs.indices.map { m[0] * xs[it] + m[1] * ys[it] + m[2] }
        val mappedY = xs.indices.map { m[3] * xs[it] + m[4] * ys[it] + m[5] }
        rect.set(mappedX.min(), mappedY.min(), mappedX.max(), mappedY.max())
        return true
    }

    internal fun skia() = Matrix33(*m)
}

class Path {
    internal val skia = SkPath()

    fun moveTo(x: Float, y: Float) {
        skia.moveTo(x, y)
    }

    fun lineTo(x: Float, y: Float) {
        skia.lineTo(x, y)
    }
}

object PorterDuff {
    enum class Mode { DST_IN, SRC_OVER, CLEAR, SRC }
}

open class Xfermode

class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()

open class Shader {
    enum class TileMode { CLAMP }

    private var local: Matrix? = null

    fun setLocalMatrix(matrix: Matrix?) {
        local = matrix
    }

    protected fun style() = GradientStyle(FilterTileMode.CLAMP, true, local?.skia())

    internal open fun skia(): SkShader? = null
}

class LinearGradient(
    private val x0: Float,
    private val y0: Float,
    private val x1: Float,
    private val y1: Float,
    private val color0: Int,
    private val color1: Int,
    private val tile: TileMode,
) : Shader() {
    override fun skia() = SkShader.makeLinearGradient(x0, y0, x1, y1, intArrayOf(color0, color1), null, style())
}

class RadialGradient(
    private val centerX: Float,
    private val centerY: Float,
    private val radius: Float,
    private val colors: IntArray,
    private val stops: FloatArray?,
    private val tile: TileMode,
) : Shader() {
    override fun skia() = SkShader.makeRadialGradient(centerX, centerY, radius, colors, stops, style())
}

class BlurMaskFilter(internal val radius: Float, internal val style: Blur) {
    enum class Blur { NORMAL }
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE }

    enum class Cap { BUTT, ROUND, SQUARE }

    enum class Join { MITER, ROUND, BEVEL }

    internal val filter = flags and FILTER_BITMAP_FLAG != 0
    private val antiAlias = flags and ANTI_ALIAS_FLAG != 0
    private val dither = flags and DITHER_FLAG != 0

    var color: Int = Color.BLACK
    var alpha: Int
        get() = Color.alpha(color)
        set(value) {
            color = (color and 0xFFFFFF) or (value shl 24)
        }
    var style = Style.FILL
    var strokeWidth = 0f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var shader: Shader? = null
    var xfermode: Xfermode? = null
    var maskFilter: BlurMaskFilter? = null

    internal fun skia(): SkPaint = SkPaint().also { paint ->
        paint.isAntiAlias = antiAlias
        paint.isDither = dither
        paint.color = color
        paint.mode = if (style == Style.STROKE) PaintMode.STROKE else PaintMode.FILL
        paint.strokeWidth = strokeWidth
        paint.strokeCap = when (strokeCap) {
            Cap.BUTT -> PaintStrokeCap.BUTT
            Cap.ROUND -> PaintStrokeCap.ROUND
            Cap.SQUARE -> PaintStrokeCap.SQUARE
        }
        paint.strokeJoin = when (strokeJoin) {
            Join.MITER -> PaintStrokeJoin.MITER
            Join.ROUND -> PaintStrokeJoin.ROUND
            Join.BEVEL -> PaintStrokeJoin.BEVEL
        }
        shader?.skia()?.let { paint.shader = it }
        (xfermode as? PorterDuffXfermode)?.let {
            paint.blendMode = when (it.mode) {
                PorterDuff.Mode.DST_IN -> BlendMode.DST_IN
                PorterDuff.Mode.SRC_OVER -> BlendMode.SRC_OVER
                PorterDuff.Mode.CLEAR -> BlendMode.CLEAR
                PorterDuff.Mode.SRC -> BlendMode.SRC
            }
        }
        // Android convierte el radio de BlurMaskFilter en sigma con esta misma fórmula.
        maskFilter?.let { paint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, it.radius * 0.57735f + 0.5f) }
    }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }
}

class Canvas(private val bitmap: Bitmap) {
    private val canvas = SkCanvas(bitmap.skia)

    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    private fun sampling(paint: Paint?): SamplingMode =
        if (paint == null || paint.filter) FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE) else SamplingMode.DEFAULT

    fun drawColor(color: Int) {
        canvas.drawPaint(SkPaint().apply { this.color = color })
    }

    fun drawBitmap(source: Bitmap, matrix: Matrix, paint: Paint?) {
        canvas.save()
        canvas.concat(matrix.skia())
        drawBitmap(source, null, RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), paint)
        canvas.restore()
    }

    fun drawBitmap(source: Bitmap, src: Rect?, dst: Rect, paint: Paint?) =
        drawBitmap(source, src, RectF(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat()), paint)

    fun drawBitmap(source: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        source.image().use { image ->
            val from = src?.let { SkRect.makeLTRB(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                ?: SkRect.makeWH(image.width.toFloat(), image.height.toFloat())
            canvas.drawImageRect(image, from, dst.skia(), sampling(paint), paint?.skia(), true)
        }
    }

    fun drawBitmap(source: Bitmap, left: Float, top: Float, paint: Paint?) =
        drawBitmap(source, null, RectF(left, top, left + source.width, top + source.height), paint)

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        canvas.drawRect(SkRect.makeLTRB(left, top, right, bottom), paint.skia())
    }

    fun drawRect(rect: RectF, paint: Paint) {
        canvas.drawRect(rect.skia(), paint.skia())
    }

    fun drawCircle(x: Float, y: Float, radius: Float, paint: Paint) {
        canvas.drawCircle(x, y, radius, paint.skia())
    }

    fun drawPath(path: Path, paint: Paint) {
        canvas.drawPath(path.skia, paint.skia())
    }

    fun saveLayer(bounds: RectF?, paint: Paint?): Int = canvas.saveLayer(bounds?.skia(), paint?.skia())

    fun restoreToCount(count: Int) {
        canvas.restoreToCount(count)
    }
}
