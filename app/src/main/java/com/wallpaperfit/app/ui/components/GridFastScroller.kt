package com.wallpaperfit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Medidas de una cuadrícula de filas iguales, para situar la barra de desplazamiento. */
internal data class GridMetrics(val columns: Int, val rowStep: Int, val maxScroll: Int, val viewport: Int) {

    /** Parte de la cuadrícula que cabe en pantalla: el tamaño del tirador respecto a la barra. */
    val thumbFraction: Float get() = viewport.toFloat() / (viewport + maxScroll)

    /** Cuánto se ha bajado, de 0 a 1. */
    fun progress(firstItem: Int, firstItemOffset: Int): Float =
        (((firstItem / columns) * rowStep + firstItemOffset).toFloat() / maxScroll).coerceIn(0f, 1f)

    /** Foto y desplazamiento dentro de ella que corresponden a [fraction] del recorrido. */
    fun target(fraction: Float): Pair<Int, Int> {
        val pixels = (fraction * maxScroll).roundToInt()
        return (pixels / rowStep) * columns to pixels % rowStep
    }

    companion object {
        fun of(total: Int, columns: Int, itemHeight: Int, spacing: Int, padding: Int, viewport: Int): GridMetrics {
            val rows = ceil(total.toDouble() / columns).toInt()
            val content = rows * itemHeight + (rows - 1) * spacing + padding
            return GridMetrics(columns, itemHeight + spacing, (content - viewport).coerceAtLeast(0), viewport)
        }
    }
}

/**
 * Barra lateral para recorrer cuadrículas de fotos: arrastrar el tirador (o tocar la barra) salta
 * a esa altura de la lista. El tirador es proporcional a lo que cabe en pantalla, así que crece
 * cuantas menos fotos hay, y si caben todas no sale. Se ve mientras se desplaza o se arrastra, y
 * se atenúa después. Va encima de la cuadrícula, alineada a la derecha.
 */
@Composable
fun GridFastScroller(state: LazyGridState, modifier: Modifier = Modifier) {
    // [state] cambia al filtrar (cada filtro empieza su lista): lo que se calcula de él, también.
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) {
                null
            } else {
                GridMetrics.of(
                    total = info.totalItemsCount,
                    columns = items.groupingBy { it.row }.eachCount().values.max(),
                    itemHeight = items.first().size.height,
                    spacing = info.mainAxisItemSpacing,
                    padding = info.beforeContentPadding + info.afterContentPadding,
                    viewport = info.viewportSize.height,
                )
            }
        }
    }
    val grid = metrics
    if (grid == null || grid.maxScroll <= 0) return

    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val active = dragging || state.isScrollInProgress
    val alpha by animateFloatAsState(if (active) 1f else 0.45f, label = "fastScrollAlpha")
    val progress by remember(state, grid) {
        derivedStateOf {
            // Al final del todo, abajo del todo aunque las cuentas se queden a un píxel.
            if (state.canScrollForward) grid.progress(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) else 1f
        }
    }

    BoxWithConstraints(modifier.fillMaxHeight().width(TOUCH_WIDTH).padding(vertical = 8.dp)) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = (trackPx * grid.thumbFraction).coerceIn(with(density) { MIN_THUMB_HEIGHT.toPx() }, trackPx)
        val range = (trackPx - thumbPx).coerceAtLeast(1f)

        // Los gestos no se reinician mientras se arrastra, pero siempre usan las medidas de ahora.
        val jumpTo by rememberUpdatedState { y: Float ->
            val (index, offset) = grid.target(((y - thumbPx / 2f) / range).coerceIn(0f, 1f))
            scope.launch { state.scrollToItem(index, offset) }
            Unit
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(TOUCH_WIDTH)
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jumpTo(it.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> jumpTo(change.position.y) }
                }
                .pointerInput(state) { detectTapGestures { jumpTo(it.y) } },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (progress * range).roundToInt()) }
                    .padding(end = 4.dp)
                    .size(width = if (active) 8.dp else 5.dp, height = with(density) { thumbPx.toDp() })
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Zona táctil (más ancha que el tirador, para acertar sin mirar). */
private val TOUCH_WIDTH = 28.dp

/** Con cientos de fotos el tirador sería una raya imposible de coger. */
private val MIN_THUMB_HEIGHT = 40.dp
