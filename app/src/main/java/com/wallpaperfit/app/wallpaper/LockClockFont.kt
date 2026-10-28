package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Estilo del reloj de la pantalla de bloqueo del propio móvil, cuando el sistema lo deja leer.
 *
 * En vivo está en los ajustes del sistema (fuente, grosor, alineación y si el color sigue al
 * fondo) y su fuente es un archivo del sistema que cualquier app puede abrir, así que el reloj
 * del escritorio puede usar exactamente la misma sin incluirla en la app. En otros móviles (o si
 * no se puede leer) el estilo "Bloqueo" usa Playfair Display Black, una serif gruesa parecida.
 */
object LockClockFont {

    /** Lo que se sabe del reloj de bloqueo del móvil. */
    data class LockStyle(
        /** Archivo de la fuente en el sistema (null si no se sabe o no se puede leer). */
        val fontFile: File?,
        /** Grosor de la fuente variable. */
        val weight: Int?,
        val align: ClockAlign?,
        /** El color del reloj cambia según el fondo. */
        val colorFollowsWallpaper: Boolean,
    )

    /** De dónde sale la fuente de un estilo: un recurso de la app o un archivo del sistema. */
    data class FontSource(val res: Int?, val file: File?, val weight: Int?)

    private const val KEY = "lock_screen_theme_id_os"
    private const val TAG = "LockClockFont"

    @Volatile
    private var cached: LockStyle? = null

    @Volatile
    private var read = false

    /** Estilo del reloj de bloqueo (se lee una vez por proceso; [refresh] para volver a leerlo). */
    fun lockStyle(context: Context, refresh: Boolean = false): LockStyle? {
        if (read && !refresh) return cached
        cached = try {
            Settings.Secure.getString(context.contentResolver, KEY)?.let { parse(JSONObject(it)) }
        } catch (e: Exception) {
            Log.i(TAG, "No se puede leer el estilo del reloj de bloqueo: ${e.message}")
            null
        }
        read = true
        return cached
    }

    private fun parse(json: JSONObject): LockStyle? {
        val time = json.optJSONObject("time_view") ?: return null
        val font = time.optJSONObject("clock_font")
        val file = font?.optString("font_path")?.takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.canRead() }
        val weight = font?.optInt("font_weight", 0)?.takeIf { it > 0 }
        // vivo: 2 = centrado (lo que se ha visto en el reloj clásico); otros valores no se conocen.
        val align = when (time.optJSONObject("location")?.optInt("align", -1)) {
            2 -> ClockAlign.CENTER
            else -> null
        }
        // type_id -1 = "según el fondo"; otros, colores elegidos a mano.
        val follows = time.optJSONObject("main_color")?.optInt("type_id", -1) == -1
        return LockStyle(file, weight, align, follows)
    }

    /** Fuente y grosor para [font]: los suyos, salvo el estilo de bloqueo con la fuente del sistema. */
    fun resolve(context: Context, font: ClockFont): FontSource {
        if (font == ClockFont.VIVO_SERIF) {
            lockStyle(context)?.fontFile?.let { return FontSource(null, it, lockStyle(context)?.weight) }
        }
        return FontSource(font.res, null, font.weight)
    }
}
