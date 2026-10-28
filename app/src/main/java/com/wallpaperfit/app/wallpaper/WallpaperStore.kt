package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Ajustes de los fondos y del reloj, y estado del pase de fondos. Una sola instancia por
 * proceso: la comparten la pantalla de ajustes, el fondo dinámico y el widget, así que un cambio
 * hecho en la app se ve al momento en el fondo.
 */
class WallpaperStore private constructor(context: Context) {

    /** Datos de este móvil: carpetas y fotos elegidas (con permisos que solo valen aquí) y el pase. */
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Preferencias que sirven en cualquier móvil (ajustes del pase sin las fotos, y el reloj): son
     * lo único que entra en la copia de seguridad de Android (ver res/xml/data_extraction_rules.xml).
     */
    private val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    // Tras restaurar una copia en otro móvil solo hay ajustes, sin fotos: se empieza sin carpetas.
    private val _config = MutableStateFlow(
        (read(prefs, KEY_CONFIG) ?: read(settings, KEY_CONFIG))?.let(WallpaperConfig::fromJson) ?: WallpaperConfig(),
    )
    val config: StateFlow<WallpaperConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow(read(prefs, KEY_STATE)?.let(RotationState::fromJson) ?: RotationState())
    val state: StateFlow<RotationState> = _state.asStateFlow()

    private val _clock = MutableStateFlow(
        (read(settings, KEY_CLOCK) ?: read(prefs, KEY_CLOCK))?.let(ClockConfig::fromJson) ?: ClockConfig(),
    )
    val clock: StateFlow<ClockConfig> = _clock.asStateFlow()

    /** Color calculado del fondo actual para el reloj en modo automático / acento. */
    private val _clockColors = MutableStateFlow(
        AdaptiveColors(
            prefs.getInt(KEY_CONTRAST, 0xFFFFFFFF.toInt()),
            prefs.getInt(KEY_ACCENT, 0xFFFFFFFF.toInt()),
            prefs.getInt(KEY_VIVO, 0xFFFFFFFF.toInt()),
        ),
    )
    val clockColors: StateFlow<AdaptiveColors> = _clockColors.asStateFlow()

    init {
        // Versiones anteriores guardaban todo junto: se copian los ajustes al fichero de la copia.
        if (!settings.contains(KEY_CONFIG) || !settings.contains(KEY_CLOCK)) {
            saveSettings(_config.value, _clock.value)
            prefs.edit().remove(KEY_CLOCK).apply()
        }
    }

    @Synchronized
    fun updateConfig(change: (WallpaperConfig) -> WallpaperConfig) {
        val value = change(_config.value)
        _config.value = value
        prefs.edit().putString(KEY_CONFIG, value.toJson().toString()).apply()
        saveSettings(value, _clock.value)
    }

    @Synchronized
    fun saveState(value: RotationState) {
        _state.value = value
        prefs.edit().putString(KEY_STATE, value.toJson().toString()).apply()
    }

    @Synchronized
    fun updateClock(change: (ClockConfig) -> ClockConfig) {
        val value = change(_clock.value)
        _clock.value = value
        saveSettings(_config.value, value)
    }

    private fun saveSettings(config: WallpaperConfig, clock: ClockConfig) {
        settings.edit()
            .putString(KEY_CONFIG, config.copy(sources = emptyList()).toJson().toString())
            .putString(KEY_CLOCK, clock.toJson().toString())
            .apply()
    }

    /** Foto que la app puso en la pantalla de bloqueo (se recuerda aunque se cierre la app). */
    val lockUri: String? get() = prefs.getString(KEY_LOCK_URI, null)

    fun saveLockUri(uri: String?) {
        prefs.edit().putString(KEY_LOCK_URI, uri).apply()
    }

    /** Color que vivo calculó para su reloj de bloqueo con cada foto (las últimas [MAX_VIVO_COLORS]). */
    private val vivoColors: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>().apply {
        read(prefs, KEY_VIVO_COLORS)?.let { json -> json.keys().forEach { put(it, json.getInt(it)) } }
    }

    @Synchronized
    fun vivoColor(uri: String): Int? = vivoColors[uri]

    @Synchronized
    fun saveVivoColor(uri: String, color: Int) {
        vivoColors.remove(uri)
        vivoColors[uri] = color
        while (vivoColors.size > MAX_VIVO_COLORS) vivoColors.remove(vivoColors.keys.first())
        prefs.edit().putString(KEY_VIVO_COLORS, JSONObject(vivoColors as Map<*, *>).toString()).apply()
    }

    fun saveClockColors(colors: AdaptiveColors) {
        _clockColors.value = colors
        prefs.edit().putInt(KEY_CONTRAST, colors.contrast).putInt(KEY_ACCENT, colors.accent).putInt(KEY_VIVO, colors.vivo).apply()
    }

    private fun read(from: SharedPreferences, key: String): JSONObject? = from.getString(key, null)?.let {
        try {
            JSONObject(it)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFERENCES = "wallpapers"
        private const val SETTINGS = "wallpaper_settings"
        private const val KEY_CONFIG = "config"
        private const val KEY_STATE = "state"
        private const val KEY_CLOCK = "clock"
        private const val KEY_CONTRAST = "clock_contrast"
        private const val KEY_ACCENT = "clock_accent"
        private const val KEY_VIVO = "clock_vivo"
        private const val KEY_LOCK_URI = "lock_uri"
        private const val KEY_VIVO_COLORS = "vivo_colors"
        private const val MAX_VIVO_COLORS = 500

        @Volatile
        private var instance: WallpaperStore? = null

        fun get(context: Context): WallpaperStore =
            instance ?: synchronized(this) { instance ?: WallpaperStore(context.applicationContext).also { instance = it } }
    }
}

/**
 * Colores del reloj calculados del fondo: [contrast] (blanco u oscuro), [accent] (color vivo
 * legible) y [vivo] (como el reloj de bloqueo de vivo).
 */
data class AdaptiveColors(val contrast: Int, val accent: Int, val vivo: Int)
