package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CurvePoint
import com.wallpaperfit.app.model.Curves
import com.wallpaperfit.app.model.LensBlur
import com.wallpaperfit.app.model.LensShape
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.processing.PhotoEffects
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.hypot
import kotlin.math.min

/** Vista previa encuadrada, centrada con la proporción del destino; devuelve su tamaño en px. */
@Composable
private fun PreviewBox(preview: ImageBitmap?, targetSize: TargetSize, modifier: Modifier = Modifier, overlay: @Composable BoxScope.(Float, Float) -> Unit) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = targetSize.width.toFloat() / targetSize.height
        val fitsHeight = maxWidth / maxHeight > ratio
        val imageWidth = if (fitsHeight) maxHeight * ratio else maxWidth
        val imageHeight = if (fitsHeight) maxHeight else maxWidth / ratio
        val density = LocalDensity.current
        Box(modifier = Modifier.size(imageWidth, imageHeight), contentAlignment = Alignment.Center) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator()
            }
            overlay(with(density) { imageWidth.toPx() }, with(density) { imageHeight.toPx() })
        }
    }
}

// region Curvas

private val CurveBackground = Color.Black.copy(alpha = 0.55f)
private val GridColor = Color.White.copy(alpha = 0.18f)

private fun CurveChannel.color(): Color = when (this) {
    CurveChannel.RGB -> Color.White
    CurveChannel.RED -> Color(0xFFFF6B6B)
    CurveChannel.GREEN -> Color(0xFF6BE38A)
    CurveChannel.BLUE -> Color(0xFF6BA8FF)
}

/**
 * Vista previa con el editor de curvas superpuesto en la parte inferior: arrastrar puntos,
 * tocar la curva para añadir uno y mantener pulsado para quitarlo.
 */
@Composable
fun CurvesStage(
    preview: ImageBitmap?,
    targetSize: TargetSize,
    curves: Curves,
    channel: CurveChannel,
    onCurveChange: (CurveChannel, List<CurvePoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCurve by rememberUpdatedState(curves[channel])
    val currentChannel by rememberUpdatedState(channel)
    val currentOnChange by rememberUpdatedState(onCurveChange)
    val touchRadius = with(LocalDensity.current) { 28.dp.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }

    PreviewBox(preview, targetSize, modifier) { width, height ->
        val side = min(width, height) * 0.82f
        val sideDp = with(LocalDensity.current) { side.toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-12).dp)
                .size(sideDp)
                .clip(RoundedCornerShape(12.dp))
                .background(CurveBackground)
                .padding(10.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { pos ->
                                val point = toCurve(pos, size.width.toFloat(), size.height.toFloat())
                                val curve = currentCurve
                                if (nearest(curve, pos, size.width.toFloat(), size.height.toFloat(), touchRadius) < 0) {
                                    currentOnChange(currentChannel, (curve + point).sortedBy { it.x })
                                }
                            },
                            onLongPress = { pos ->
                                val curve = currentCurve
                                val index = nearest(curve, pos, size.width.toFloat(), size.height.toFloat(), touchRadius)
                                if (index > 0 && index < curve.lastIndex) {
                                    currentOnChange(currentChannel, curve.filterIndexed { i, _ -> i != index })
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragging = nearest(currentCurve, pos, size.width.toFloat(), size.height.toFloat(), touchRadius * 1.5f)
                            },
                            onDragEnd = { dragging = -1 },
                            onDragCancel = { dragging = -1 },
                        ) { change, _ ->
                            val index = dragging
                            val curve = currentCurve
                            if (index < 0 || index > curve.lastIndex) return@detectDragGestures
                            change.consume()
                            val p = toCurve(change.position, size.width.toFloat(), size.height.toFloat())
                            // Los extremos solo se mueven en vertical; los demás, entre sus vecinos.
                            val x = when (index) {
                                0 -> curve[0].x
                                curve.lastIndex -> curve.last().x
                                else -> p.x.coerceIn(curve[index - 1].x + 0.02f, curve[index + 1].x - 0.02f)
                            }
                            val updated = curve.toMutableList().also { it[index] = CurvePoint(x, p.y) }
                            currentOnChange(currentChannel, updated)
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                for (i in 1..3) {
                    drawLine(GridColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h))
                    drawLine(GridColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f))
                }
                drawLine(GridColor, Offset(0f, h), Offset(w, 0f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                // Las curvas de los demás canales, tenues, para tener referencia.
                for (other in CurveChannel.entries) {
                    if (other == channel || curves[other] == Curves.IDENTITY) continue
                    drawCurve(curves[other], other.color().copy(alpha = 0.35f), w, h, 1.5.dp.toPx())
                }
                val color = channel.color()
                drawCurve(curves[channel], color, w, h, 2.5.dp.toPx())
                curves[channel].forEachIndexed { i, p ->
                    val center = Offset(p.x * w, (1f - p.y) * h)
                    drawCircle(Color.Black.copy(alpha = 0.6f), 9.dp.toPx(), center)
                    drawCircle(if (i == dragging) MaterialAccent else color, 6.dp.toPx(), center)
                }
            }
        }
    }
}

private val MaterialAccent = Color(0xFFFF8F70)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(points: List<CurvePoint>, color: Color, w: Float, h: Float, width: Float) {
    val lut = PhotoEffects.lut(points)
    val path = Path()
    for (i in 0..255) {
        val x = i / 255f * w
        val y = (1f - lut[i] / 255f) * h
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = width))
}

private fun toCurve(pos: Offset, w: Float, h: Float): CurvePoint =
    CurvePoint((pos.x / w).coerceIn(0f, 1f), (1f - pos.y / h).coerceIn(0f, 1f))

/** Índice del punto más cercano a [pos] dentro de [radius] px, o -1. */
private fun nearest(curve: List<CurvePoint>, pos: Offset, w: Float, h: Float, radius: Float): Int {
    var best = -1
    var bestDistance = radius
    curve.forEachIndexed { i, p ->
        val d = hypot(p.x * w - pos.x, (1f - p.y) * h - pos.y)
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

// endregion

// region Desenfoque de lente

/** Vista previa con la zona enfocada dibujada encima; se mueve, escala y gira con gestos. */
@Composable
fun LensStage(
    preview: ImageBitmap?,
    targetSize: TargetSize,
    lens: LensBlur,
    transition: Float,
    onGesture: (panX: Float, panY: Float, zoom: Float, rotation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnGesture by rememberUpdatedState(onGesture)
    PreviewBox(preview, targetSize, modifier) { _, _ ->
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        currentOnGesture(pan.x / size.width, pan.y / size.height, zoom, rotation)
                    }
                }
                .mouseWheel { _, zoom, rotation -> currentOnGesture(0f, 0f, zoom, rotation) },
        ) {
            val w = size.width
            val h = size.height
            val center = Offset(lens.centerX * w, lens.centerY * h)
            val inner = lens.size * min(w, h) / 2f
            val outer = inner + inner * (0.15f + transition * 1.6f)
            val line = Stroke(width = 1.5.dp.toPx())
            val dashed = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            val white = Color.White.copy(alpha = 0.9f)
            when (lens.shape) {
                LensShape.RADIAL -> {
                    drawCircle(white, inner, center, style = line)
                    drawCircle(white.copy(alpha = 0.6f), outer, center, style = dashed)
                }
                LensShape.LINEAR -> rotate(lens.angle, center) {
                    val length = hypot(w, h)
                    for (sign in listOf(-1f, 1f)) {
                        drawLine(white, Offset(center.x - length, center.y + sign * inner), Offset(center.x + length, center.y + sign * inner), line.width)
                        drawLine(
                            white.copy(alpha = 0.6f),
                            Offset(center.x - length, center.y + sign * outer),
                            Offset(center.x + length, center.y + sign * outer),
                            dashed.width,
                            pathEffect = dashed.pathEffect,
                        )
                    }
                }
            }
            drawCircle(Color.Black.copy(alpha = 0.5f), 7.dp.toPx(), center)
            drawCircle(white, 4.dp.toPx(), center)
        }
    }
}

// endregion
