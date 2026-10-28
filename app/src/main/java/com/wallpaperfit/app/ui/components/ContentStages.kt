package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.CropRect
import com.wallpaperfit.app.model.ExpandFill
import com.wallpaperfit.app.model.Expansion
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.HealStroke
import com.wallpaperfit.app.model.Side
import com.wallpaperfit.app.processing.BaseGeometry
import com.wallpaperfit.app.ui.ExpandDraft
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val STAGE_MARGIN = 24.dp
private val CHECKER_SIZE = 12.dp
internal val EXCLUSION_SIZE = 64.dp
private val CheckerLight = Color(0xFF3A3C42)
private val CheckerDark = Color(0xFF2A2C31)
private val BrushColor = Color(0x99FF4D4D)

/**
 * Lienzo de Expandir: la foto dentro de su marco expandido. Se arrastra el borde más cercano al
 * dedo para añadir o quitar lienzo en ese lado; el área nueva muestra el relleno calculado.
 */
@Composable
fun ExpandStage(
    originalPreview: ImageBitmap?,
    crop: CropRect,
    originalWidth: Int,
    originalHeight: Int,
    expansion: Expansion,
    draft: ExpandDraft?,
    isLoading: Boolean,
    onDrag: (Side, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { STAGE_MARGIN.toPx() }
    val checkerPx = with(density) { CHECKER_SIZE.toPx() }
    val handleLength = with(density) { 32.dp.toPx() }
    val handleThickness = with(density) { 5.dp.toPx() }
    val exclusionPx = with(density) { EXCLUSION_SIZE.toPx() }
    val currentOnDrag by rememberUpdatedState(onDrag)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground),
    ) {
        if (originalPreview == null || originalWidth <= 0 || originalHeight <= 0) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val availableWidth = constraints.maxWidth - 2 * marginPx
        val availableHeight = constraints.maxHeight - 2 * marginPx
        val expandedWidth = expansion.expandedWidth(originalWidth)
        val expandedHeight = expansion.expandedHeight(originalHeight)
        val scale = min(availableWidth / expandedWidth, availableHeight / expandedHeight)
        val frame = Rect(
            offset = Offset(
                (constraints.maxWidth - expandedWidth * scale) / 2f,
                (constraints.maxHeight - expandedHeight * scale) / 2f,
            ),
            size = Size(expandedWidth * scale, expandedHeight * scale),
        )
        val photo = Rect(
            offset = Offset(
                frame.left + expansion.left * originalWidth * scale,
                frame.top + expansion.top * originalHeight * scale,
            ),
            size = Size(originalWidth * scale, originalHeight * scale),
        )
        val currentFrame by rememberUpdatedState(frame)
        val currentScale by rememberUpdatedState(scale)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Los tiradores laterales suelen quedar junto al borde de la pantalla: se excluyen
                // del gesto "atrás" del sistema (Android admite como mucho 200 dp por lado).
                .systemGestureExclusion { handleZone(currentFrame.left, currentFrame.center.y, exclusionPx) }
                .systemGestureExclusion { handleZone(currentFrame.right, currentFrame.center.y, exclusionPx) }
                .pointerInput(originalWidth, originalHeight) {
                    var side: Side? = null
                    detectDragGestures(
                        onDragStart = { position -> side = nearestSide(position, currentFrame) },
                        onDragEnd = { side = null },
                        onDragCancel = { side = null },
                    ) { change, drag ->
                        change.consume()
                        val pxPerUnitX = originalWidth * currentScale
                        val pxPerUnitY = originalHeight * currentScale
                        when (side) {
                            Side.LEFT -> currentOnDrag(Side.LEFT, -drag.x / pxPerUnitX)
                            Side.RIGHT -> currentOnDrag(Side.RIGHT, drag.x / pxPerUnitX)
                            Side.TOP -> currentOnDrag(Side.TOP, -drag.y / pxPerUnitY)
                            Side.BOTTOM -> currentOnDrag(Side.BOTTOM, drag.y / pxPerUnitY)
                            null -> Unit
                        }
                    }
                },
        ) {
            clipRect(frame.left, frame.top, frame.right, frame.bottom) {
                drawChecker(frame, checkerPx)
                when {
                    draft != null && draft.expansion == expansion -> drawBitmapInto(draft.image, frame)
                    expansion.fill == ExpandFill.WHITE -> drawRect(Color.White, frame.topLeft, frame.size)
                    expansion.fill == ExpandFill.BLACK -> drawRect(Color.Black, frame.topLeft, frame.size)
                }
            }
            drawBitmapRegion(originalPreview, crop, photo)

            drawRect(Color.White.copy(alpha = 0.9f), frame.topLeft, frame.size, style = Stroke(width = 1.5.dp.toPx()))
            val handleColor = Color.White
            val radius = CornerRadius(handleThickness / 2f)
            drawRoundRect(handleColor, Offset(frame.left - handleThickness / 2f, frame.center.y - handleLength / 2f), Size(handleThickness, handleLength), radius)
            drawRoundRect(handleColor, Offset(frame.right - handleThickness / 2f, frame.center.y - handleLength / 2f), Size(handleThickness, handleLength), radius)
            drawRoundRect(handleColor, Offset(frame.center.x - handleLength / 2f, frame.top - handleThickness / 2f), Size(handleLength, handleThickness), radius)
            drawRoundRect(handleColor, Offset(frame.center.x - handleLength / 2f, frame.bottom - handleThickness / 2f), Size(handleLength, handleThickness), radius)
        }

        StatusChip(
            text = stringResource(R.string.expand_size, expandedWidth.roundToInt(), expandedHeight.roundToInt()),
            busy = isLoading,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
    }
}

/** Zona cuadrada alrededor de un tirador que se excluye del gesto "atrás" del sistema. */
internal fun handleZone(x: Float, y: Float, size: Float): Rect =
    Rect(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)

private fun nearestSide(position: Offset, frame: Rect): Side {
    val distances = mapOf(
        Side.LEFT to abs(position.x - frame.left),
        Side.RIGHT to abs(position.x - frame.right),
        Side.TOP to abs(position.y - frame.top),
        Side.BOTTOM to abs(position.y - frame.bottom),
    )
    return distances.minBy { it.value }.key
}

/**
 * Lienzo del Corrector: se pinta con un dedo sobre lo que se quiere borrar (o se arrastra un
 * rectángulo en modo [HealShape.RECT]) y al soltar se rellena. Con dos dedos se hace zoom y se
 * desplaza la vista.
 */
@Composable
fun HealStage(
    baseView: ImageBitmap?,
    geometry: BaseGeometry?,
    brushSizeDp: Float,
    shape: HealShape,
    isProcessing: Boolean,
    onStroke: (HealStroke) -> Unit,
    modifier: Modifier = Modifier,
    strokeColor: Color = BrushColor,
) {
    val currentShape by rememberUpdatedState(shape)
    val density = LocalDensity.current
    val marginPx = with(density) { STAGE_MARGIN.toPx() }
    val brushPx = with(density) { brushSizeDp.dp.toPx() }
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var liveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val currentOnStroke by rememberUpdatedState(onStroke)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground),
    ) {
        if (baseView == null || geometry == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val center = Offset(width / 2f, height / 2f)
        val fitScale = min((width - 2 * marginPx) / geometry.width, (height - 2 * marginPx) / geometry.height)
        val fitted = Size(geometry.width * fitScale, geometry.height * fitScale)
        val fittedTopLeft = Offset((width - fitted.width) / 2f, (height - fitted.height) / 2f)
        val imageRect = Rect(
            offset = center + (fittedTopLeft - center) * viewScale + viewOffset,
            size = fitted * viewScale,
        )
        val currentRect by rememberUpdatedState(imageRect)
        val currentGeometry by rememberUpdatedState(geometry)
        val currentBrushPx by rememberUpdatedState(brushPx)
        val currentCenter by rememberUpdatedState(center)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val points = mutableListOf(down.position)
                        var painting = true
                        liveStroke = points.toList()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                if (painting) {
                                    painting = false
                                    liveStroke = emptyList()
                                }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val newScale = (viewScale * zoom).coerceIn(1f, MAX_VIEW_ZOOM)
                                val c = currentCenter
                                viewOffset = centroid + pan - c - (centroid - c - viewOffset) * (newScale / viewScale)
                                viewScale = newScale
                                event.changes.forEach { it.consume() }
                            } else if (painting) {
                                val change = pressed.first()
                                points += change.position
                                liveStroke = points.toList()
                                change.consume()
                            }
                        }
                        if (painting) {
                            if (currentShape == HealShape.RECT) {
                                if (points.size >= 2) currentOnStroke(toHealRect(points.first(), points.last(), currentRect, currentGeometry))
                            } else {
                                currentOnStroke(toHealStroke(points, currentRect, currentGeometry, currentBrushPx))
                            }
                        }
                        liveStroke = emptyList()
                    }
                }
                .mouseWheel { position, zoom, _ ->
                    val newScale = (viewScale * zoom).coerceIn(1f, MAX_VIEW_ZOOM)
                    val c = currentCenter
                    viewOffset = position - c - (position - c - viewOffset) * (newScale / viewScale)
                    viewScale = newScale
                },
        ) {
            drawBitmapInto(baseView, imageRect)
            val stroke = liveStroke
            if (shape == HealShape.RECT) {
                if (stroke.size >= 2) {
                    val a = stroke.first()
                    val b = stroke.last()
                    val topLeft = Offset(min(a.x, b.x), min(a.y, b.y))
                    val size = Size(abs(a.x - b.x), abs(a.y - b.y))
                    drawRect(strokeColor, topLeft, size)
                    drawRect(Color.White, topLeft, size, style = Stroke(width = 1.5.dp.toPx()))
                }
            } else if (stroke.isNotEmpty()) {
                if (stroke.size == 1) {
                    drawCircle(strokeColor, brushPx / 2f, stroke.first())
                } else {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                    }
                    drawPath(path, strokeColor, style = Stroke(width = brushPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        if (isProcessing) {
            StatusChip(
                text = stringResource(R.string.processing),
                busy = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
        }
    }
}

private const val MAX_VIEW_ZOOM = 8f

/** Convierte puntos de pantalla en un trazo en coordenadas de la foto original. */
private fun toHealStroke(points: List<Offset>, imageRect: Rect, geometry: BaseGeometry, brushPx: Float): HealStroke {
    val basePerViewPx = geometry.width / imageRect.width
    val coordinates = ArrayList<Float>(points.size * 2)
    for (p in points) {
        val baseX = (p.x - imageRect.left) * basePerViewPx
        val baseY = (p.y - imageRect.top) * basePerViewPx
        coordinates += (baseX - geometry.offsetX) / geometry.scale
        coordinates += (baseY - geometry.offsetY) / geometry.scale
    }
    val radius = brushPx / 2f * basePerViewPx / geometry.scale
    return HealStroke(coordinates, radius)
}

/** Rectángulo entre dos puntos de pantalla, en coordenadas de la foto original. */
private fun toHealRect(a: Offset, b: Offset, imageRect: Rect, geometry: BaseGeometry): HealStroke {
    val basePerViewPx = geometry.width / imageRect.width
    fun x(p: Offset) = ((p.x - imageRect.left) * basePerViewPx - geometry.offsetX) / geometry.scale
    fun y(p: Offset) = ((p.y - imageRect.top) * basePerViewPx - geometry.offsetY) / geometry.scale
    return HealStroke(
        points = listOf(min(x(a), x(b)), min(y(a), y(b)), max(x(a), x(b)), max(y(a), y(b))),
        radius = 0f,
        shape = HealShape.RECT,
    )
}

/** Etiqueta superpuesta con indicador de progreso opcional. */
@Composable
fun StatusChip(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    // Se ve mientras cambia el valor (o se está procesando) y se oculta al poco, para no tapar
    // los tiradores de debajo. No es un Surface: así no se queda con los toques.
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(text, busy) {
        visible = true
        if (!busy) {
            delay(STATUS_CHIP_VISIBLE_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val STATUS_CHIP_VISIBLE_MS = 1500L

private fun DrawScope.drawBitmapInto(image: ImageBitmap, rect: Rect) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}

/** Dibuja solo la parte [crop] (fracciones) de [image] dentro de [rect]. */
private fun DrawScope.drawBitmapRegion(image: ImageBitmap, crop: CropRect, rect: Rect) {
    val x = (crop.left * image.width).roundToInt().coerceIn(0, image.width - 1)
    val y = (crop.top * image.height).roundToInt().coerceIn(0, image.height - 1)
    val w = (crop.widthFraction * image.width).roundToInt().coerceIn(1, image.width - x)
    val h = (crop.heightFraction * image.height).roundToInt().coerceIn(1, image.height - y)
    drawImage(
        image = image,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(w, h),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}

private fun DrawScope.drawChecker(rect: Rect, cell: Float) {
    drawRect(CheckerDark, rect.topLeft, rect.size)
    var y = rect.top
    var row = 0
    while (y < rect.bottom) {
        var x = rect.left + if (row % 2 == 0) 0f else cell
        while (x < rect.right) {
            drawRect(CheckerLight, Offset(x, y), Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}
