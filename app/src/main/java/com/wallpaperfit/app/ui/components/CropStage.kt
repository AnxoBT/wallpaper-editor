package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.CropHandle
import com.wallpaperfit.app.model.CropRect
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val CROP_MARGIN = 28.dp
private val CORNER_TOUCH = 30.dp
private val EDGE_TOUCH = 22.dp
private val CORNER_LENGTH = 22.dp
private const val OUTSIDE_SHADE = 0.6f

/**
 * Lienzo de Recortar: la foto entera con el rectángulo de recorte encima. Se arrastran las
 * esquinas o los lados para cambiar el tamaño y el interior para moverlo; fuera del recorte la
 * foto se oscurece.
 */
@Composable
fun CropStage(
    image: ImageBitmap?,
    crop: CropRect,
    croppedWidth: Int,
    croppedHeight: Int,
    onDrag: (CropHandle, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { CROP_MARGIN.toPx() }
    val cornerTouch = with(density) { CORNER_TOUCH.toPx() }
    val edgeTouch = with(density) { EDGE_TOUCH.toPx() }
    val exclusionPx = with(density) { EXCLUSION_SIZE.toPx() }
    val currentOnDrag by rememberUpdatedState(onDrag)

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
        val scale = min((stageWidth - 2 * marginPx) / image.width, (stageHeight - 2 * marginPx) / image.height)
        val photo = Rect(
            offset = Offset((stageWidth - image.width * scale) / 2f, (stageHeight - image.height * scale) / 2f),
            size = Size(image.width * scale, image.height * scale),
        )
        val cropRect = Rect(
            left = photo.left + crop.left * photo.width,
            top = photo.top + crop.top * photo.height,
            right = photo.left + crop.right * photo.width,
            bottom = photo.top + crop.bottom * photo.height,
        )
        val currentPhoto by rememberUpdatedState(photo)
        val currentCrop by rememberUpdatedState(cropRect)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Esquinas y lados del recorte junto al borde de la pantalla: fuera del gesto
                // "atrás" del sistema (Android admite como mucho 200 dp por lado: 3 × 64 dp).
                .systemGestureExclusion { handleZone(currentCrop.left, currentCrop.top, exclusionPx) }
                .systemGestureExclusion { handleZone(currentCrop.left, currentCrop.center.y, exclusionPx) }
                .systemGestureExclusion { handleZone(currentCrop.left, currentCrop.bottom, exclusionPx) }
                .systemGestureExclusion { handleZone(currentCrop.right, currentCrop.top, exclusionPx) }
                .systemGestureExclusion { handleZone(currentCrop.right, currentCrop.center.y, exclusionPx) }
                .systemGestureExclusion { handleZone(currentCrop.right, currentCrop.bottom, exclusionPx) }
                .pointerInput(Unit) {
                    var handle: CropHandle? = null
                    detectDragGestures(
                        onDragStart = { position -> handle = hitTest(position, currentCrop, cornerTouch, edgeTouch) },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                    ) { change, drag ->
                        val h = handle ?: return@detectDragGestures
                        change.consume()
                        val p = currentPhoto
                        currentOnDrag(h, drag.x / p.width, drag.y / p.height)
                    }
                },
        ) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(photo.left.roundToInt(), photo.top.roundToInt()),
                dstSize = IntSize(photo.width.roundToInt().coerceAtLeast(1), photo.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.High,
            )
            shadeOutside(photo, cropRect)
            drawRect(Color.White, cropRect.topLeft, cropRect.size, style = Stroke(width = 1.5.dp.toPx()))
            drawThirdsGrid(cropRect)
            drawCornerHandles(cropRect, CORNER_LENGTH.toPx(), 4.dp.toPx())
        }

        StatusChip(
            text = stringResource(R.string.expand_size, croppedWidth, croppedHeight),
            busy = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
        )
    }
}

/** Esquinas primero (son más pequeñas), después lados y por último el interior. */
private fun hitTest(position: Offset, crop: Rect, cornerTouch: Float, edgeTouch: Float): CropHandle? {
    val nearLeft = abs(position.x - crop.left) < cornerTouch
    val nearRight = abs(position.x - crop.right) < cornerTouch
    val nearTop = abs(position.y - crop.top) < cornerTouch
    val nearBottom = abs(position.y - crop.bottom) < cornerTouch
    when {
        nearLeft && nearTop -> return CropHandle.TOP_LEFT
        nearRight && nearTop -> return CropHandle.TOP_RIGHT
        nearLeft && nearBottom -> return CropHandle.BOTTOM_LEFT
        nearRight && nearBottom -> return CropHandle.BOTTOM_RIGHT
    }
    val withinX = position.x in crop.left..crop.right
    val withinY = position.y in crop.top..crop.bottom
    return when {
        withinY && abs(position.x - crop.left) < edgeTouch -> CropHandle.LEFT
        withinY && abs(position.x - crop.right) < edgeTouch -> CropHandle.RIGHT
        withinX && abs(position.y - crop.top) < edgeTouch -> CropHandle.TOP
        withinX && abs(position.y - crop.bottom) < edgeTouch -> CropHandle.BOTTOM
        withinX && withinY -> CropHandle.MOVE
        else -> null
    }
}

private fun DrawScope.shadeOutside(photo: Rect, crop: Rect) {
    val shade = Color.Black.copy(alpha = OUTSIDE_SHADE)
    drawRect(shade, photo.topLeft, Size(photo.width, crop.top - photo.top))
    drawRect(shade, Offset(photo.left, crop.bottom), Size(photo.width, photo.bottom - crop.bottom))
    drawRect(shade, Offset(photo.left, crop.top), Size(crop.left - photo.left, crop.height))
    drawRect(shade, Offset(crop.right, crop.top), Size(photo.right - crop.right, crop.height))
}

private fun DrawScope.drawThirdsGrid(rect: Rect) {
    val color = Color.White.copy(alpha = 0.4f)
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = rect.left + rect.width * i / 3f
        val y = rect.top + rect.height * i / 3f
        drawLine(color, Offset(x, rect.top), Offset(x, rect.bottom), stroke)
        drawLine(color, Offset(rect.left, y), Offset(rect.right, y), stroke)
    }
}

private fun DrawScope.drawCornerHandles(rect: Rect, length: Float, thickness: Float) {
    val l = min(length, min(rect.width, rect.height) / 2f)
    val corners = listOf(
        Triple(rect.topLeft, 1f, 1f),
        Triple(Offset(rect.right, rect.top), -1f, 1f),
        Triple(Offset(rect.left, rect.bottom), 1f, -1f),
        Triple(rect.bottomRight, -1f, -1f),
    )
    for ((corner, sx, sy) in corners) {
        drawLine(Color.White, corner, Offset(corner.x + sx * l, corner.y), thickness, StrokeCap.Round)
        drawLine(Color.White, corner, Offset(corner.x, corner.y + sy * l), thickness, StrokeCap.Round)
    }
}
