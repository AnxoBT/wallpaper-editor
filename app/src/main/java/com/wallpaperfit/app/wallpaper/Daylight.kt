package com.wallpaperfit.app.wallpaper

import android.content.Context
import java.util.Calendar

/**
 * Opción "según la hora": de noche solo salen las fotos oscuras (para que el fondo no deslumbre a
 * oscuras); de día, cualquiera. La noche es la
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

    fun isDay(config: WallpaperConfig, now: Calendar = Calendar.getInstance()): Boolean =
        SourceSchedule.DAY.isActive(now, config.nightStart, config.nightEnd)

    /** De día vale cualquiera; de noche, solo las oscuras. */
    fun fits(brightness: Float, day: Boolean): Boolean = day || brightness <= NIGHT_MAX

    /**
     * Las fotos que tocan a esta hora. Las aún sin medir cuentan como que sí (se miran al salir).
     * Si no queda ninguna, todas: nunca se queda sin fondo.
     */
    fun filter(images: List<String>, day: Boolean, brightness: (String) -> Float?): List<String> =
        images.filter { uri -> brightness(uri)?.let { fits(it, day) } ?: true }.ifEmpty { images }

    /** Cuántas fotos valen de noche y cuántas hay (las aún sin medir no cuentan para la noche). */
    data class Counts(val night: Int, val total: Int)

    fun counts(images: List<String>, brightness: (String) -> Float?): Counts =
        Counts(night = images.mapNotNull(brightness).count { fits(it, day = false) }, total = images.size)

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
