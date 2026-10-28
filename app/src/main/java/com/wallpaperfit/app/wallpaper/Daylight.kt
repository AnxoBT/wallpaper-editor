package com.wallpaperfit.app.wallpaper

import android.content.Context
import java.util.Calendar

/**
 * Opción "según la hora": de día salen las fotos claras y de noche las oscuras. La noche es la
 * de la configuración ([WallpaperConfig.nightStart] a [WallpaperConfig.nightEnd]), la misma que
 * usan los horarios de día/noche de las fuentes.
 *
 * El brillo de cada foto (0 = negro, 1 = blanco) se mide una vez y se guarda. No se analiza nada
 * en segundo plano: se mide al activar la opción (con la app abierta) y, si al cambiar sale una
 * aún sin medir (por ejemplo, una foto nueva), en ese momento.
 */
object Daylight {

    /** Hasta este brillo una foto vale para la noche. */
    const val NIGHT_MAX = 0.42f

    /** Desde este brillo vale para el día. Las de en medio valen para los dos. */
    const val DAY_MIN = 0.30f

    fun isDay(config: WallpaperConfig, now: Calendar = Calendar.getInstance()): Boolean =
        SourceSchedule.DAY.isActive(now, config.nightStart, config.nightEnd)

    fun fits(brightness: Float, day: Boolean): Boolean = if (day) brightness >= DAY_MIN else brightness <= NIGHT_MAX

    /**
     * Las fotos que tocan a esta hora. Las aún sin medir cuentan como que sí (se miran al salir).
     * Si no queda ninguna, todas: nunca se queda sin fondo.
     */
    fun filter(images: List<String>, day: Boolean, brightness: (String) -> Float?): List<String> =
        images.filter { uri -> brightness(uri)?.let { fits(it, day) } ?: true }.ifEmpty { images }

    // region Brillo guardado (en milésimas, por foto)

    private const val PREFERENCES = "photo_brightness"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun brightness(context: Context, uri: String): Float? =
        prefs(context).getInt(uri, -1).takeIf { it >= 0 }?.let { it / 1000f }

    fun saveBrightness(context: Context, uri: String, value: Float) {
        prefs(context).edit().putInt(uri, (value.coerceIn(0f, 1f) * 1000).toInt()).apply()
    }

    // endregion
}
