package com.wallpaperfit.app.storage

/** Cómo cuenta una etiqueta en un filtro. */
enum class TagState { NONE, INCLUDED, EXCLUDED }

/**
 * Filtro por etiquetas: la foto tiene que tener todas las de [included] y ninguna de [excluded].
 * Cada toque en una etiqueta pasa al estado siguiente: incluir, excluir y quitar.
 */
data class TagFilter(val included: Set<String> = emptySet(), val excluded: Set<String> = emptySet()) {

    val isEmpty: Boolean get() = included.isEmpty() && excluded.isEmpty()

    fun state(tag: String): TagState = when (tag) {
        in included -> TagState.INCLUDED
        in excluded -> TagState.EXCLUDED
        else -> TagState.NONE
    }

    fun cycled(tag: String): TagFilter = when (state(tag)) {
        TagState.NONE -> copy(included = included + tag)
        TagState.INCLUDED -> TagFilter(included - tag, excluded + tag)
        TagState.EXCLUDED -> copy(excluded = excluded - tag)
    }
}
