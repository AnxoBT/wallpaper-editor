package com.wallpaperfit.app.wallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.wallpaperfit.app.MainActivity
import com.wallpaperfit.app.R

/**
 * Widget de reloj para la pantalla de inicio. La hora la mueve el propio sistema (TextClock):
 * la app no se despierta cada minuto. Solo se actualiza cuando cambian los ajustes del reloj o
 * el fondo (para adaptar el color).
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    /** Al cambiar el tamaño del widget, la hora se vuelve a ajustar a su caja. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            // En vertical el lanzador da el ancho mínimo y el alto máximo de la caja.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            manager.updateAppWidget(id, build(context, width, height))
        }

        /**
         * Factor (≤ 1) para que la hora y la fecha quepan en una caja de [widthDp] × [heightDp]:
         * con un tamaño fijo, si la caja es más pequeña, el lanzador recorta el texto y el reloj
         * no se ve como en la vista previa.
         */
        fun fitScale(context: Context, clock: ClockConfig, widthDp: Int, heightDp: Int): Float {
            if (widthDp <= 0 || heightDp <= 0) return 1f
            val metrics = context.resources.displayMetrics
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = clockTypeface(context, clock.font)
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, clock.timeSize, metrics)
            }
            val timeWidth = paint.measureText(if (clock.use24h) "00:00" else "12:00")
            val timeHeight = paint.fontMetrics.let { it.descent - it.ascent }
            val dateHeight = if (clock.showDate) {
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, clock.dateSize, metrics) * 1.25f
            } else {
                0f
            }
            val boxWidth = widthDp * metrics.density
            val boxHeight = heightDp * metrics.density
            return minOf(1f, boxWidth / (timeWidth * 1.04f), boxHeight / (timeHeight + dateHeight))
        }

        /** Tipografía de la fuente: las incluidas en la app, desde ClockBitmap; las demás, del sistema. */
        private fun clockTypeface(context: Context, font: ClockFont): Typeface? = ClockBitmap.typeface(context, font) ?: when (font) {
            ClockFont.SANS_THIN -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            ClockFont.SANS_LIGHT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            ClockFont.SANS_MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            ClockFont.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            ClockFont.SERIF -> Typeface.SERIF
            ClockFont.MONO -> Typeface.MONOSPACE
            else -> null
        }

        /** Color del texto según el modo elegido y los colores calculados del fondo actual. */
        fun textColor(clock: ClockConfig, colors: AdaptiveColors): Int = when (clock.colorMode) {
            ClockColorMode.VIVO -> colors.vivo
            ClockColorMode.AUTO -> colors.contrast
            ClockColorMode.ACCENT -> colors.accent
            ClockColorMode.FIXED -> clock.fixedColor
        }

        /** Sombra oscura bajo texto claro y clara bajo texto oscuro. */
        fun shadowVariant(clock: ClockConfig, color: Int): ShadowVariant = when {
            clock.shadow == ClockShadow.NONE -> ShadowVariant.NONE
            Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f > 128f -> ShadowVariant.DARK
            else -> ShadowVariant.LIGHT
        }

        fun build(context: Context, widthDp: Int = 0, heightDp: Int = 0): RemoteViews {
            val store = WallpaperStore.get(context)
            val chosen = store.clock.value
            val scale = fitScale(context, chosen, widthDp, heightDp)
            val clock = chosen.copy(timeSize = chosen.timeSize * scale, dateSize = chosen.dateSize * scale)
            val color = textColor(clock, store.clockColors.value)
            // Fuentes de la app: el escritorio no siempre sabe cargarlas, así que el reloj se dibuja
            // aquí como imagen. Se renueva cada minuto desde el fondo dinámico (que es quien está en
            // marcha mientras se ve el escritorio); sin él, se usa el texto del sistema.
            // Solo mientras el fondo dinámico está en marcha: es quien la renueva cada minuto.
            if (ClockBitmap.needsBitmap(clock.font) && SlideshowWallpaperService.engineRunning) {
                drawnMinute = System.currentTimeMillis() / MINUTE_MS
                val density = context.resources.displayMetrics.density
                val w = ((if (widthDp > 0) widthDp else ClockBitmap.DEFAULT_WIDTH_DP) * density).toInt()
                val h = ((if (heightDp > 0) heightDp else ClockBitmap.DEFAULT_HEIGHT_DP) * density).toInt()
                val views = RemoteViews(context.packageName, R.layout.widget_clock_image)
                views.setImageViewBitmap(R.id.clock_image, ClockBitmap.render(context, clock, color, w, h))
                views.setOnClickPendingIntent(R.id.clock_root, openSettings(context))
                return views
            }
            val views = RemoteViews(context.packageName, clockLayout(clock.font, shadowVariant(clock, color)))

            views.setTextColor(R.id.clock_time, color)
            views.setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, clock.timeSize)
            // Una sola de las dos fechas (encima o debajo de la hora) se ve.
            for ((id, position) in listOf(R.id.clock_date_top to DatePosition.ABOVE, R.id.clock_date to DatePosition.BELOW)) {
                views.setTextColor(id, color)
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, clock.dateSize)
                views.setCharSequence(id, "setFormat24Hour", clock.dateFormat.pattern)
                views.setCharSequence(id, "setFormat12Hour", clock.dateFormat.pattern)
                views.setViewVisibility(id, if (clock.showDate && clock.datePosition == position) View.VISIBLE else View.GONE)
            }
            // Mismo formato en los dos modos del sistema: manda lo elegido en la app.
            val format = clock.timePattern
            views.setCharSequence(R.id.clock_time, "setFormat24Hour", format)
            views.setCharSequence(R.id.clock_time, "setFormat12Hour", format)

            val gravity = when (clock.align) {
                ClockAlign.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
                ClockAlign.CENTER -> Gravity.CENTER
                ClockAlign.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            }
            views.setInt(R.id.clock_root, "setGravity", gravity)
            views.setInt(R.id.clock_time, "setGravity", gravity)
            views.setInt(R.id.clock_date, "setGravity", gravity)
            views.setInt(R.id.clock_date_top, "setGravity", gravity)

            views.setOnClickPendingIntent(R.id.clock_root, openSettings(context))
            return views
        }

        /** Tocar el reloj abre los ajustes de fondos y reloj. */
        private fun openSettings(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_WALLPAPERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Minuto de la última imagen del reloj (para no redibujarla si no ha cambiado). */
        @Volatile
        private var drawnMinute = -1L

        private const val MINUTE_MS = 60_000L

        /** Reloj dibujado como imagen: se renueva si el minuto ha cambiado desde la última vez. */
        fun refreshDrawnClock(context: Context) {
            if (!ClockBitmap.needsBitmap(WallpaperStore.get(context).clock.value.font)) return
            if (System.currentTimeMillis() / MINUTE_MS == drawnMinute) return
            updateAll(context)
        }

        /** Pide al lanzador que añada el widget (el usuario confirma dónde). */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, ClockWidget::class.java), null, null)
        }
    }
}
