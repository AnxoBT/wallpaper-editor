package com.wallpaperfit.app.wallpaper

import com.wallpaperfit.app.storage.TagFilter
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * De dónde salen los fondos: una carpeta (todas sus imágenes, también las nuevas), una foto suelta
 * o una etiqueta (todas las fotos de la galería que la tengan, estén donde estén).
 */
data class WallpaperSource(
    val uri: String,
    val isFolder: Boolean,
    val name: String,
    /** Cuándo entran sus fotos en el pase (por ejemplo, una carpeta de día y otra de noche). */
    val schedule: SourceSchedule = SourceSchedule.ALWAYS,
    /** Solo para etiquetas: quita del pase las fotos que la tienen, en vez de exigirla. */
    val excluded: Boolean = false,
) {
    /** La etiqueta, si la fuente es una etiqueta. */
    val tag: String? get() = if (uri.startsWith(TAG_PREFIX)) uri.removePrefix(TAG_PREFIX) else null

    companion object {
        private const val TAG_PREFIX = "tag:"

        fun forTag(tag: String, excluded: Boolean = false) = WallpaperSource(TAG_PREFIX + tag, isFolder = false, name = tag, excluded = excluded)
    }
}

/** Horario de una carpeta o foto del pase. "De día" es de 8:00 a 20:00. */
enum class SourceSchedule {
    ALWAYS,
    DAY,
    NIGHT,
    WEEKDAYS,
    WEEKEND,
    ;

    /** Si toca ahora; la noche va de [nightStart] a [nightEnd] (horas, puede cruzar la medianoche). */
    fun isActive(now: Calendar, nightStart: Int = DEFAULT_NIGHT_START, nightEnd: Int = DEFAULT_NIGHT_END): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val weekend = now.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
        val night = isNight(hour, nightStart, nightEnd)
        return when (this) {
            ALWAYS -> true
            DAY -> !night
            NIGHT -> night
            WEEKDAYS -> !weekend
            WEEKEND -> weekend
        }
    }

    companion object {
        /** Hora a la que empieza la noche, si no se cambia. */
        const val DEFAULT_NIGHT_START = 20

        /** Hora a la que acaba la noche, si no se cambia. */
        const val DEFAULT_NIGHT_END = 8

        /** Si [hour] cae en la noche de [start] a [end] (si cruza la medianoche, p. ej. de 20 a 8). */
        fun isNight(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start > end -> hour >= start || hour < end
            else -> hour in start until end
        }
    }
}

enum class RotationOrder { RANDOM, SEQUENTIAL }

/**
 * Cuándo cambia el fondo solo. Solo cuenta mientras el fondo se ve: con la pantalla apagada o
 * una app abierta no hay nada en marcha.
 */
enum class AutoChange(val minutes: Long) {
    NEVER(0),
    EVERY_UNLOCK(0),
    MINUTES_15(15),
    HOUR_1(60),
    HOURS_6(360),
    DAY_1(1440),

    /** Cada [WallpaperConfig.customMinutes] minutos. */
    CUSTOM(0),
}

/** Dónde se ve el fondo dinámico. En bloqueo se pone una copia fija de la misma foto. */
enum class WallpaperPlace { HOME, HOME_AND_LOCK }

/** Cómo se ajusta cada foto a la pantalla. */
enum class WallpaperFit { FILL, FIT_BLUR }

/** Qué hace un gesto (doble o triple toque) en un hueco del escritorio. */
enum class TapAction { NEXT, PREVIOUS, RANDOM, TOGGLE_AUTO, NONE }

data class WallpaperConfig(
    val sources: List<WallpaperSource> = emptyList(),
    val order: RotationOrder = RotationOrder.RANDOM,
    val autoChange: AutoChange = AutoChange.NEVER,
    val doubleTapAction: TapAction = TapAction.NEXT,
    val tripleTapAction: TapAction = TapAction.PREVIOUS,
    /** Cambio automático en pausa (se pausa y reanuda con un gesto). */
    val autoPaused: Boolean = false,
    /** Cuántos fondos anteriores se recuerdan para volver con "Anterior". */
    val historySize: Int = 5,
    val place: WallpaperPlace = WallpaperPlace.HOME,
    val fit: WallpaperFit = WallpaperFit.FILL,
    val customMinutes: Int = 5,
    /** De día fotos claras y de noche oscuras (ver [Daylight]). */
    val matchDaylight: Boolean = false,
    /** La noche (para "según la hora" y los horarios de día/noche de las fuentes). */
    val nightStart: Int = SourceSchedule.DEFAULT_NIGHT_START,
    val nightEnd: Int = SourceSchedule.DEFAULT_NIGHT_END,
) {
    /** El filtro de etiquetas del pase. */
    val tagFilter: TagFilter
        get() = TagFilter(
            included = sources.filter { !it.excluded }.mapNotNullTo(HashSet()) { it.tag },
            excluded = sources.filter { it.excluded }.mapNotNullTo(HashSet()) { it.tag },
        )

    /** Intervalo del cambio automático en ms, o null si no es por tiempo. */
    val intervalMs: Long?
        get() = when (autoChange) {
            AutoChange.NEVER, AutoChange.EVERY_UNLOCK -> null
            AutoChange.CUSTOM -> customMinutes * 60_000L
            else -> autoChange.minutes * 60_000L
        }

    /** Fuentes que tocan ahora según su horario; si ninguna toca, todas (nunca se queda sin fotos). */
    fun activeSources(now: Calendar = Calendar.getInstance()): List<WallpaperSource> =
        sources.filter { it.schedule.isActive(now, nightStart, nightEnd) }.ifEmpty { sources }

    fun toJson(): JSONObject = JSONObject().apply {
        put("sources", JSONArray().apply {
            sources.forEach {
                put(JSONObject().put("uri", it.uri).put("folder", it.isFolder).put("name", it.name).put("schedule", it.schedule.name).put("excluded", it.excluded))
            }
        })
        put("order", order.name)
        put("autoChange", autoChange.name)
        put("doubleTapAction", doubleTapAction.name)
        put("tripleTapAction", tripleTapAction.name)
        put("autoPaused", autoPaused)
        put("historySize", historySize)
        put("place", place.name)
        put("fit", fit.name)
        put("customMinutes", customMinutes)
        put("matchDaylight", matchDaylight)
        put("nightStart", nightStart)
        put("nightEnd", nightEnd)
    }

    companion object {
        const val MIN_HISTORY = 1
        const val MAX_HISTORY = 10
        const val MIN_CUSTOM_MINUTES = 1
        const val MAX_CUSTOM_MINUTES = 720

        fun fromJson(json: JSONObject): WallpaperConfig {
            val list = json.optJSONArray("sources") ?: JSONArray()
            return WallpaperConfig(
                sources = (0 until list.length()).map {
                    val o = list.getJSONObject(it)
                    WallpaperSource(
                        o.getString("uri"),
                        o.optBoolean("folder"),
                        o.optString("name"),
                        enumOr(o.optString("schedule"), SourceSchedule.ALWAYS),
                        o.optBoolean("excluded"),
                    )
                },
                order = enumOr(json.optString("order"), RotationOrder.RANDOM),
                autoChange = enumOr(json.optString("autoChange"), AutoChange.NEVER),
                // Versiones anteriores guardaban un sí/no por gesto (siguiente / anterior).
                doubleTapAction = if (json.has("doubleTapAction")) {
                    enumOr(json.optString("doubleTapAction"), TapAction.NEXT)
                } else if (json.optBoolean("doubleTapNext", true)) {
                    TapAction.NEXT
                } else {
                    TapAction.NONE
                },
                tripleTapAction = if (json.has("tripleTapAction")) {
                    enumOr(json.optString("tripleTapAction"), TapAction.PREVIOUS)
                } else if (json.optBoolean("tripleTapPrevious", true)) {
                    TapAction.PREVIOUS
                } else {
                    TapAction.NONE
                },
                autoPaused = json.optBoolean("autoPaused", false),
                historySize = json.optInt("historySize", 5).coerceIn(MIN_HISTORY, MAX_HISTORY),
                place = enumOr(json.optString("place"), WallpaperPlace.HOME),
                fit = enumOr(json.optString("fit"), WallpaperFit.FILL),
                customMinutes = json.optInt("customMinutes", 5).coerceIn(MIN_CUSTOM_MINUTES, MAX_CUSTOM_MINUTES),
                matchDaylight = json.optBoolean("matchDaylight", false),
                nightStart = json.optInt("nightStart", SourceSchedule.DEFAULT_NIGHT_START).coerceIn(0, 23),
                nightEnd = json.optInt("nightEnd", SourceSchedule.DEFAULT_NIGHT_END).coerceIn(0, 23),
            )
        }
    }
}

/**
 * Estado del pase de fondos: el fondo actual y los anteriores ([history], el último es el más
 * reciente) para poder volver atrás con triple toque, y los que se han descartado al volver
 * ([forward]) para que "siguiente" rehaga el camino en vez de sacar otro al azar.
 */
data class RotationState(
    val current: String? = null,
    val history: List<String> = emptyList(),
    val forward: List<String> = emptyList(),
    /** Fotos aún no mostradas en esta vuelta del modo aleatorio (sin repetir hasta verlas todas). */
    val pending: List<String> = emptyList(),
    /** Momento del último cambio (ms), para el cambio automático. */
    val changedAt: Long = 0L,
    /** Huellas de las fotos que había en el último cambio, para saber cuáles son nuevas. */
    val known: List<Int> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("current", current ?: JSONObject.NULL)
        put("history", JSONArray(history))
        put("forward", JSONArray(forward))
        put("pending", JSONArray(pending))
        put("changedAt", changedAt)
        put("known", JSONArray(known))
    }

    companion object {
        fun fromJson(json: JSONObject): RotationState = RotationState(
            current = json.optString("current").takeIf { it.isNotEmpty() && it != "null" },
            history = json.optJSONArray("history").strings(),
            forward = json.optJSONArray("forward").strings(),
            pending = json.optJSONArray("pending").strings(),
            changedAt = json.optLong("changedAt"),
            known = json.optJSONArray("known")?.let { array -> (0 until array.length()).map { array.getInt(it) } }.orEmpty(),
        )
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
