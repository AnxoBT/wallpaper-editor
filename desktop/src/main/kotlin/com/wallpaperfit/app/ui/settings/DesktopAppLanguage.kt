package com.wallpaperfit.app.ui.settings

import android.app.Activity
import android.content.Context
import com.wallpaperfit.app.R
import com.wallpaperfit.app.desktop.Strings

/** Idioma de la app. Por defecto, el de Windows. La ventana se redibuja al cambiarlo. */
object AppLanguage {

    enum class Option(val tag: String?, val label: Int) {
        SYSTEM(null, R.string.settings_language_system),
        ENGLISH("en", R.string.settings_language_english),
        SPANISH("es", R.string.settings_language_spanish),
    }

    /** Lo avisa la ventana para volver a crear las pantallas, como hace Android con la actividad. */
    var onChanged: () -> Unit = {}

    fun current(context: Context): Option =
        Option.entries.firstOrNull { it.tag == prefs(context).getString(KEY_LANGUAGE, null) } ?: Option.SYSTEM

    fun set(activity: Activity, option: Option) {
        prefs(activity).edit().putString(KEY_LANGUAGE, option.tag).apply()
        Strings.language = option.tag
        onChanged()
    }

    /** Al arrancar: aplica el idioma guardado. */
    fun restore(context: Context) {
        Strings.language = current(context).tag
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE = "language"
}
