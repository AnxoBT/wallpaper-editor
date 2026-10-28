package com.wallpaperfit.app.wallpaper

import kotlin.random.Random

/**
 * Lógica de siguiente / anterior del pase de fondos, sin Android (se prueba con tests de JVM).
 *
 * - Siguiente: si antes se volvió atrás, rehace el camino ([RotationState.forward]); si no, toma
 *   la siguiente foto en orden o una al azar que no haya salido en esta vuelta.
 * - Anterior: vuelve exactamente a la que había antes (no a una al azar), hasta
 *   [WallpaperConfig.historySize] pasos.
 */
object Rotation {

    fun next(state: RotationState, images: List<String>, config: WallpaperConfig, random: Random, now: Long): RotationState {
        if (images.isEmpty()) return state
        val available = images.toSet()
        val history = pushed(state.history, state.current, config.historySize)

        // Rehacer el camino si se había vuelto atrás.
        val forward = state.forward.filter { it in available }
        if (forward.isNotEmpty()) {
            return state.copy(current = forward.last(), history = history, forward = forward.dropLast(1), changedAt = now)
        }

        return when (config.order) {
            RotationOrder.SEQUENTIAL -> {
                val index = images.indexOf(state.current)
                state.copy(current = images[(index + 1).mod(images.size)], history = history, forward = emptyList(), changedAt = now)
            }
            RotationOrder.RANDOM -> {
                // Fotos añadidas a la carpeta desde el último cambio: salen las primeras, sin esperar
                // a que acabe la vuelta.
                val known = state.known.toSet()
                val fresh = if (known.isEmpty()) emptyList() else images.filter { it.hashCode() !in known && it != state.current }
                val freshSet = fresh.toSet()
                var pending = fresh.shuffled(random) + state.pending.filter { it in available && it != state.current && it !in freshSet }
                if (pending.isEmpty()) {
                    // Nueva vuelta: todas en orden aleatorio, sin repetir la actual justo después.
                    pending = images.filter { it != state.current }.shuffled(random)
                    if (pending.isEmpty()) pending = images
                }
                state.copy(
                    current = pending.first(),
                    history = history,
                    forward = emptyList(),
                    pending = pending.drop(1),
                    changedAt = now,
                    known = images.map { it.hashCode() },
                )
            }
        }
    }

    /** Vuelve al fondo anterior; null si no hay ninguno guardado. */
    fun previous(state: RotationState, images: List<String>, config: WallpaperConfig, now: Long): RotationState? {
        val available = images.toSet()
        val history = state.history.filter { it in available }
        if (history.isEmpty()) return null
        return state.copy(
            current = history.last(),
            history = history.dropLast(1),
            forward = pushed(state.forward, state.current, config.historySize),
            changedAt = now,
        )
    }

    /** Si ya toca cambiar por tiempo (solo para los cambios por intervalo). */
    fun isAutoChangeDue(state: RotationState, config: WallpaperConfig, now: Long): Boolean {
        if (config.autoPaused) return false
        val interval = config.intervalMs ?: return false
        return now - state.changedAt >= interval
    }

    /** Milisegundos que faltan para el próximo cambio por tiempo, o null si no hay. */
    fun millisUntilAutoChange(state: RotationState, config: WallpaperConfig, now: Long): Long? {
        if (config.autoPaused) return null
        val interval = config.intervalMs ?: return null
        return (state.changedAt + interval - now).coerceAtLeast(0L)
    }

    private fun pushed(list: List<String>, item: String?, limit: Int): List<String> =
        if (item == null) list else (list + item).takeLast(limit)
}
