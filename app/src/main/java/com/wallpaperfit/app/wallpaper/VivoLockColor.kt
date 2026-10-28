package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * Color que vivo calcula para su reloj de bloqueo según el fondo de bloqueo. vivo lo guarda en
 * los ajustes del sistema; si Android deja leerlo, el reloj del escritorio usa exactamente ese
 * color mientras en el bloqueo esté la misma foto que en el escritorio.
 */
object VivoLockColor {

    private const val KEY = "keyguard_aod_time_color"
    private const val TAG = "VivoLockColor"

    /** Color principal del reloj de bloqueo de vivo, o null si no está o no se puede leer. */
    fun read(context: Context): Int? = try {
        Settings.Secure.getString(context.contentResolver, KEY)?.let { JSONObject(it).optInt("mainColor", 0) }?.takeIf { it != 0 }
            .also { Log.i(TAG, "Color del reloj de vivo: ${it?.let { c -> Integer.toHexString(c) }}") }
    } catch (e: Exception) {
        Log.i(TAG, "No se puede leer el color del reloj de vivo: ${e.message}")
        null
    }

    /** Avisa cada vez que vivo recalcula el color (al cambiar el fondo de bloqueo). */
    fun observe(context: Context, onChange: () -> Unit): ContentObserver? = try {
        val uri: Uri = Settings.Secure.getUriFor(KEY)
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }.also { context.contentResolver.registerContentObserver(uri, false, it) }
    } catch (e: Exception) {
        null
    }
}
