package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Dibuja el reloj en una imagen con la fuente exacta. Se usa con las fuentes incluidas en la app,
 * porque el escritorio de algunos móviles (vivo, entre ellos) no sabe cargarlas en un widget y
 * pone una fuente normal en su lugar.
 */
object ClockBitmap {

    /** Fuentes que el widget dibuja él mismo (las del sistema las pinta el escritorio sin problema). */
    fun needsBitmap(font: ClockFont): Boolean = font.res != null

    private val typefaces = HashMap<ClockFont, Typeface>()

    fun render(context: Context, clock: ClockConfig, color: Int, widthPx: Int, heightPx: Int): Bitmap {
        val metrics = context.resources.displayMetrics
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val now = Date()
        val time = SimpleDateFormat(clock.timePattern, Locale.getDefault()).format(now)
        val date = SimpleDateFormat(clock.dateFormat.pattern, Locale.getDefault()).format(now)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(context, clock.font)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, clock.timeSize, metrics)
            this.color = color
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Como en el reloj de bloqueo de vivo, la fecha va en la fuente del sistema.
            typeface = if (clock.font == ClockFont.VIVO_SERIF) Typeface.DEFAULT else timePaint.typeface
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, clock.dateSize, metrics)
            this.color = color
        }
        val shadow = when (ClockWidget.shadowVariant(clock, color)) {
            ShadowVariant.NONE -> null
            ShadowVariant.DARK -> 0xAA000000.toInt()
            ShadowVariant.LIGHT -> 0xAAFFFFFF.toInt()
        }
        if (shadow != null) {
            val radius = 4f * metrics.density
            timePaint.setShadowLayer(radius, 0f, 2f * metrics.density, shadow)
            datePaint.setShadowLayer(radius, 0f, 2f * metrics.density, shadow)
        }

        val timeMetrics = timePaint.fontMetrics
        val dateMetrics = datePaint.fontMetrics
        val timeHeight = timeMetrics.descent - timeMetrics.ascent
        val dateHeight = if (clock.showDate) (dateMetrics.descent - dateMetrics.ascent) * 1.15f else 0f
        var y = (heightPx - timeHeight - dateHeight) / 2f

        fun x(paint: Paint, text: String): Float {
            val w = paint.measureText(text)
            return when (clock.align) {
                ClockAlign.LEFT -> 0f
                ClockAlign.CENTER -> (widthPx - w) / 2f
                ClockAlign.RIGHT -> widthPx - w
            }
        }
        if (clock.showDate && clock.datePosition == DatePosition.ABOVE) {
            canvas.drawText(date, x(datePaint, date), y - dateMetrics.ascent, datePaint)
            y += dateHeight
        }
        canvas.drawText(time, x(timePaint, time), y - timeMetrics.ascent, timePaint)
        y += timeHeight
        if (clock.showDate && clock.datePosition == DatePosition.BELOW) {
            canvas.drawText(date, x(datePaint, date), y - dateMetrics.ascent + dateHeight * 0.1f, datePaint)
        }
        return bitmap
    }

    /** Tipografía de la fuente (en las variables, con el grosor del reloj de bloqueo). */
    fun typeface(context: Context, font: ClockFont): Typeface? {
        val (res, systemFile, weight) = LockClockFont.resolve(context, font)
        if (res == null && systemFile == null) return null
        typefaces[font]?.let { return it }
        val created = try {
            // Fuente del sistema: se abre tal cual. Recurso de la app: se copia a un archivo para poder
            // fijar el grosor de la fuente variable (con el recurso en el nombre: si una versión
            // cambia la fuente de un estilo, no se reutiliza la copia vieja).
            val file = systemFile ?: File(context.cacheDir, "clock_font_${font.name.lowercase()}_$res.ttf").also { copy ->
                if (!copy.exists()) context.resources.openRawResource(res!!).use { input -> copy.outputStream().use { input.copyTo(it) } }
            }
            val builder = Typeface.Builder(file)
            weight?.let { builder.setFontVariationSettings("'wght' $it") }
            builder.build()
        } catch (e: Exception) {
            null
        } ?: return null
        typefaces[font] = created
        return created
    }

    /** Tamaño por defecto de la imagen si el escritorio no dice el de la caja (dp). */
    const val DEFAULT_WIDTH_DP = 320
    const val DEFAULT_HEIGHT_DP = 150
}
