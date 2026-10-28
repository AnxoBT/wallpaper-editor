package com.wallpaperfit.app.wallpaper

import androidx.annotation.StringRes
import androidx.annotation.FontRes
import com.wallpaperfit.app.R
import org.json.JSONObject

/** Tipografías del reloj: las del sistema y las incluidas en la app (licencia OFL). */
enum class ClockFont(
    @StringRes val label: Int,
    /** Fuente incluida en la app (null = del sistema). */
    @FontRes val res: Int? = null,
    /** Grosor fijo para las fuentes variables (null = el suyo). */
    val weight: Int? = null,
) {
    /**
     * Estilo del reloj de bloqueo clásico de vivo: serif muy gruesa de alto contraste. Se usa
     * Playfair Display Black (libre, OFL) como equivalente. El nombre se conserva porque va en los
     * ajustes guardados.
     */
    VIVO_SERIF(R.string.clock_font_lock, R.font.playfair_display, weight = 900),
    SANS_LIGHT(R.string.clock_font_light),
    SANS_THIN(R.string.clock_font_thin),
    SANS_MEDIUM(R.string.clock_font_regular),
    CONDENSED(R.string.clock_font_condensed),
    SERIF(R.string.clock_font_serif),
    MONO(R.string.clock_font_mono),
    BEBAS(R.string.clock_font_Bebas, R.font.bebas_neue),
    ORBITRON(R.string.clock_font_Orbitron, R.font.orbitron),
    RIGHTEOUS(R.string.clock_font_Righteous, R.font.righteous),
    QUICKSAND(R.string.clock_font_Quicksand, R.font.quicksand),
    PLAYFAIR(R.string.clock_font_Playfair, R.font.playfair_display),
    PACIFICO(R.string.clock_font_Pacifico, R.font.pacifico),
}

enum class ClockColorMode {
    /**
     * Como el reloj de bloqueo de vivo en automático: color del tono principal del fondo (el
     * mismo "seed" que usa Material You), muy claro sobre zonas oscuras y oscuro sobre claras.
     * Si vivo ya ha calculado el color para esa misma foto, se usa el suyo tal cual.
     */
    VIVO,

    /** Blanco o casi negro, el que más contraste dé con el fondo actual. */
    AUTO,

    /** El color más vivo del fondo, aclarado u oscurecido para que se lea. */
    ACCENT,

    /** Un color elegido. */
    FIXED,
}

/** Sombra del texto; su color (oscuro o claro) se elige solo según el color del reloj. */
enum class ClockShadow { NONE, SOFT }

enum class ClockAlign { LEFT, CENTER, RIGHT }

/** Fecha encima de la hora (como el reloj de bloqueo de vivo) o debajo. */
enum class DatePosition { ABOVE, BELOW }

/** Formato de la fecha: corto "mar, 27 oct" (como vivo) o largo "martes, 27 octubre". */
enum class DateFormat(val pattern: String) { SHORT("EEE, d MMM"), LONG("EEEE, d MMMM") }

data class ClockConfig(
    val font: ClockFont = ClockFont.SANS_LIGHT,
    val timeSize: Float = 64f,
    val dateSize: Float = 16f,
    val showDate: Boolean = true,
    val use24h: Boolean = true,
    val colorMode: ClockColorMode = ClockColorMode.AUTO,
    val fixedColor: Int = 0xFFFFFFFF.toInt(),
    val shadow: ClockShadow = ClockShadow.SOFT,
    val align: ClockAlign = ClockAlign.CENTER,
    val datePosition: DatePosition = DatePosition.BELOW,
    val dateFormat: DateFormat = DateFormat.LONG,
) {
    /** Formato de la hora: el mismo en el widget, en su imagen y en la vista previa. */
    val timePattern: String get() = if (use24h) "HH:mm" else "h:mm"

    /**
     * Ajustes que imitan el reloj de la pantalla de bloqueo (estilo clásico, fuente serif gruesa,
     * fecha corta encima, sin sombra, color según el fondo). [use24h]: el formato del sistema.
     */
    fun likeLockScreen(use24h: Boolean, detected: LockClockFont.LockStyle? = null): ClockConfig = copy(
        use24h = use24h,
        font = ClockFont.VIVO_SERIF,
        timeSize = VIVO_TIME_SIZE,
        dateSize = VIVO_DATE_SIZE,
        showDate = true,
        // Lo que se haya podido leer del reloj de bloqueo del móvil manda sobre el estilo por defecto.
        colorMode = if (detected?.colorFollowsWallpaper == false) colorMode else ClockColorMode.VIVO,
        shadow = ClockShadow.NONE,
        align = detected?.align ?: ClockAlign.CENTER,
        datePosition = DatePosition.ABOVE,
        dateFormat = DateFormat.SHORT,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("font", font.name)
        put("timeSize", timeSize.toDouble())
        put("dateSize", dateSize.toDouble())
        put("showDate", showDate)
        put("use24h", use24h)
        put("colorMode", colorMode.name)
        put("fixedColor", fixedColor)
        put("shadow", shadow.name)
        put("align", align.name)
        put("datePosition", datePosition.name)
        put("dateFormat", dateFormat.name)
    }

    companion object {
        const val MIN_TIME_SIZE = 32f
        const val MAX_TIME_SIZE = 160f
        /** Medidas del reloj de vivo (estilo clásico al tamaño máximo) en una pantalla de 1080 px. */
        const val VIVO_TIME_SIZE = 92f
        const val VIVO_DATE_SIZE = 15f
        const val MIN_DATE_SIZE = 10f
        const val MAX_DATE_SIZE = 28f

        /** Colores fijos que se ofrecen (además de los automáticos). */
        val PALETTE = listOf(
            0xFFFFFFFF, 0xFF111114, 0xFFFF8F70, 0xFFFFD166, 0xFF7BE0AD, 0xFF6BC5FF, 0xFFB79CFF, 0xFFFF6B9A,
        ).map { it.toInt() }

        fun fromJson(json: JSONObject): ClockConfig = ClockConfig(
            font = ClockFont.entries.firstOrNull { it.name == json.optString("font") } ?: ClockFont.SANS_LIGHT,
            timeSize = json.optDouble("timeSize", 64.0).toFloat().coerceIn(MIN_TIME_SIZE, MAX_TIME_SIZE),
            dateSize = json.optDouble("dateSize", 16.0).toFloat().coerceIn(MIN_DATE_SIZE, MAX_DATE_SIZE),
            showDate = json.optBoolean("showDate", true),
            use24h = json.optBoolean("use24h", true),
            colorMode = ClockColorMode.entries.firstOrNull { it.name == json.optString("colorMode") } ?: ClockColorMode.AUTO,
            fixedColor = json.optInt("fixedColor", 0xFFFFFFFF.toInt()),
            shadow = ClockShadow.entries.firstOrNull { it.name == json.optString("shadow") } ?: ClockShadow.SOFT,
            align = ClockAlign.entries.firstOrNull { it.name == json.optString("align") } ?: ClockAlign.CENTER,
            datePosition = DatePosition.entries.firstOrNull { it.name == json.optString("datePosition") } ?: DatePosition.BELOW,
            dateFormat = DateFormat.entries.firstOrNull { it.name == json.optString("dateFormat") } ?: DateFormat.LONG,
        )
    }
}
