package com.wallpaperfit.app.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.wallpaperfit.app.R
import java.util.Locale

/**
 * Idioma de la app. Por defecto sigue el del móvil; se puede fijar en Ajustes.
 *
 * En Android 13+ lo guarda el propio sistema (aparece también en Ajustes > Apps > Idioma). En
 * versiones anteriores se guarda en la app y se aplica al crear cada pantalla.
 */
object AppLanguage {

    enum class Option(val tag: String?, @StringRes val label: Int) {
        SYSTEM(null, R.string.settings_language_system),
        ENGLISH("en", R.string.settings_language_english),
        SPANISH("es", R.string.settings_language_spanish),
    }

    fun current(context: Context): Option {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString(KEY_LANGUAGE, null)
        }
        val language = tag?.substringBefore('-')?.takeIf { it.isNotEmpty() }
        return Option.entries.firstOrNull { it.tag == language } ?: Option.SYSTEM
    }

    /** Cambia el idioma y vuelve a crear la pantalla con él. */
    fun set(activity: Activity, option: Option) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // El sistema vuelve a crear la actividad él solo.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                option.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs(activity).edit().putString(KEY_LANGUAGE, option.tag).apply()
            activity.recreate()
        }
    }

    /** Android 12 y anteriores: contexto con el idioma elegido (sin elección, el del móvil). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_LANGUAGE, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE = "language"
}
