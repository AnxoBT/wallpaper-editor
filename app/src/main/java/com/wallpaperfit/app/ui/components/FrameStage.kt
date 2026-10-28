package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.FitSettings
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.FrameTransform
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val FRAME_MARGIN = 40.dp
private const val OUTSIDE_ALPHA = 0.35f
private val SNAP_DISTANCE = 12.dp

/** Gesto sobre el recuadro, en píxeles de pantalla, con el centroide relativo al centro del recuadro. */
data class FrameGesture(
    val centroid: Offset,
    val pan: Offset,
    val zoom: Float,
    val rotation: Float,
    val frameSize: Size,
    /** Distancia en píxeles de pantalla a la que la imagen se pega a bordes y centro. */
    val snapDistance: Float,
)

/**
 * Lienzo de Encuadre: un recuadro fijo con la proporción de la pantalla destino y la imagen
 * libre debajo. Fuera del recuadro la imagen se ve atenuada para saber qué se queda fuera.
 * Mientras se toca se dibuja en vivo; al soltar se muestra el resultado real renderizado.
 */
@Composable
fun FrameStage(
    image: ImageBitmap?,
    preview: ImageBitmap?,
    fit: FitSettings,
    onGesture: (FrameGesture) -> Unit,
    onGestureEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { FRAME_MARGIN.toPx() }
    val snapPx = with(density) { SNAP_DISTANCE.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val currentOnGestureEnd by rememberUpdatedState(onGestureEnd)
    var interacting by remember { mutableStateOf(false) }
    val currentOnGesture by rememberUpdatedState(onGesture)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground),
    ) {
        if (image == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val stageWidth = constraints.maxWidth.toFloat()
        val stageHeight = constraints.maxHeight.toFloat()
        val ratio = fit.size.ratio
        val frameWidth = min(stageWidth - 2 * marginPx, (stageHeight - 2 * marginPx) * ratio)
        val frameHeight = frameWidth / ratio
        val frame = Rect(
            offset = Offset((stageWidth - frameWidth) / 2f, (stageHeight - frameHeight) / 2f),
            size = Size(frameWidth, frameHeight),
        )
        val currentFrame by rememberUpdatedState(frame)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Solo observa cuándo hay dedos en pantalla, sin consumir el gesto.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        interacting = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        interacting = false
                        currentOnGestureEnd()
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        val f = currentFrame
                        currentOnGesture(FrameGesture(centroid - f.center, pan, zoom, rotation, f.size, snapPx))
                    }
                }
                .mouseWheel { position, zoom, rotation ->
                    val f = currentFrame
                    currentOnGesture(FrameGesture(position - f.center, Offset.Zero, zoom, rotation, f.size, snapPx))
                    currentOnGestureEnd()
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentOnDoubleTap() })
                },
        ) {
            // Fuera del recuadro: la imagen atenuada, para ver lo que se recorta.
            drawTransformed(image, frame, fit.transform, alpha = OUTSIDE_ALPHA)

            clipRect(frame.left, frame.top, frame.right, frame.bottom) {
                if (!interacting && preview != null) {
                    drawBitmapInto(preview, frame)
                } else {
                    when (fit.fill) {
                        FrameFill.WHITE -> drawRect(Color.White, frame.topLeft, frame.size)
                        FrameFill.BLACK -> drawRect(Color.Black, frame.topLeft, frame.size)
                        FrameFill.BLUR -> if (preview != null) {
                            drawBitmapInto(preview, frame)
                        } else {
                            drawRect(Color.DarkGray, frame.topLeft, frame.size)
                        }
                    }
                    drawTransformed(image, frame, fit.transform, alpha = 1f)
                }
            }

            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(width = 1.5.dp.toPx()))
            if (interacting) drawThirds(frame)
            drawSnapGuides(image, frame, fit.transform, accent)
        }

        StatusChip(
            text = stringResource(
                R.string.frame_info,
                (fit.transform.scale * 100f).roundToInt(),
                fit.transform.rotation.roundToInt(),
            ),
            busy = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
        )
    }
}

/** Misma transformación que [com.wallpaperfit.app.processing.Geometry.frameMatrix], en Compose. */
private fun DrawScope.drawTransformed(image: ImageBitmap, frame: Rect, transform: FrameTransform, alpha: Float) {
    val cover = max(frame.width / image.width, frame.height / image.height) * transform.scale
    val center = Offset(frame.center.x + transform.offsetX * frame.width, frame.center.y + transform.offsetY * frame.height)
    translate(center.x, center.y) {
        rotate(transform.rotation, pivot = Offset.Zero) {
            scale(if (transform.flipHorizontal) -cover else cover, cover, pivot = Offset.Zero) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(-image.width / 2, -image.height / 2),
                    dstSize = IntSize(image.width, image.height),
                    alpha = alpha,
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

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

/**
 * Resalta en el color de acento los bordes del recuadro con los que la imagen está alineada
 * (y las líneas centrales si está centrada): así se ve cuándo ha actuado el imán.
 */
private fun DrawScope.drawSnapGuides(image: ImageBitmap, frame: Rect, transform: FrameTransform, accent: Color) {
    val cover = max(frame.width / image.width, frame.height / image.height) * transform.scale
    val halfWidth = image.width * cover / 2f
    val halfHeight = image.height * cover / 2f
    val radians = Math.toRadians(transform.rotation.toDouble())
    val c = abs(cos(radians)).toFloat()
    val s = abs(sin(radians)).toFloat()
    val extentX = halfWidth * c + halfHeight * s
    val extentY = halfWidth * s + halfHeight * c
    val centerX = frame.center.x + transform.offsetX * frame.width
    val centerY = frame.center.y + transform.offsetY * frame.height
    val tolerance = 0.75f
    val stroke = 3.dp.toPx()
    if (abs(centerX - extentX - frame.left) < tolerance) drawLine(accent, frame.topLeft, Offset(frame.left, frame.bottom), stroke)
    if (abs(centerX + extentX - frame.right) < tolerance) drawLine(accent, Offset(frame.right, frame.top), frame.bottomRight, stroke)
    if (abs(centerY - extentY - frame.top) < tolerance) drawLine(accent, frame.topLeft, Offset(frame.right, frame.top), stroke)
    if (abs(centerY + extentY - frame.bottom) < tolerance) drawLine(accent, Offset(frame.left, frame.bottom), frame.bottomRight, stroke)
    val centerStroke = 1.5.dp.toPx()
    if (abs(centerX - frame.center.x) < tolerance) drawLine(accent, Offset(frame.center.x, frame.top), Offset(frame.center.x, frame.bottom), centerStroke)
    if (abs(centerY - frame.center.y) < tolerance) drawLine(accent, Offset(frame.left, frame.center.y), Offset(frame.right, frame.center.y), centerStroke)
}

/** Guías de tercios para componer mientras se mueve la imagen. */
private fun DrawScope.drawThirds(frame: Rect) {
    val color = Color.White.copy(alpha = 0.45f)
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = frame.left + frame.width * i / 3f
        val y = frame.top + frame.height * i / 3f
        drawLine(color, Offset(x, frame.top), Offset(x, frame.bottom), stroke)
        drawLine(color, Offset(frame.left, y), Offset(frame.right, y), stroke)
    }
}
