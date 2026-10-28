package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.ImageLayer
import com.wallpaperfit.app.model.LayerAction
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.processing.LayerImages
import com.wallpaperfit.app.ui.label
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lienzo de Capas: la vista previa sin capas y, encima, las capas dibujadas aquí mismo para que
 * se muevan sin esperas. Tocar una capa la elige; arrastrar, pellizcar y girar con dos dedos la
 * colocan.
 */
@Composable
fun LayerStage(
    preview: ImageBitmap?,
    targetSize: TargetSize,
    layers: List<ImageLayer>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onGesture: (panX: Float, panY: Float, zoom: Float, rotation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLayers by rememberUpdatedState(layers)
    val currentOnGesture by rememberUpdatedState(onGesture)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = targetSize.width.toFloat() / targetSize.height
        val fitsHeight = maxWidth / maxHeight > ratio
        val boxWidth = if (fitsHeight) maxHeight * ratio else maxWidth
        val boxHeight = if (fitsHeight) maxHeight else maxWidth / ratio
        Box(Modifier.size(boxWidth, boxHeight)) {
            if (preview != null) {
                Image(preview, null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            // La capa más alta bajo el dedo.
                            val hit = currentLayers.lastOrNull { contains(it, pos, size.width.toFloat(), size.height.toFloat()) }
                            currentOnSelect(hit?.id)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            currentOnGesture(pan.x / size.width, pan.y / size.height, zoom, rotation)
                        }
                    }
                    .mouseWheel { _, zoom, rotation -> currentOnGesture(0f, 0f, zoom, rotation) },
            ) {
                val w = size.width
                val h = size.height
                for (layer in layers) {
                    val bitmap = LayerImages.get(layer.uri) ?: continue
                    val image = bitmap.asImageBitmap()
                    val dw = layer.width * w
                    val dh = dw * layer.aspect
                    translate(layer.centerX * w, layer.centerY * h) {
                        rotate(layer.rotation, pivot = Offset.Zero) {
                            scale(if (layer.flipHorizontal) -1f else 1f, 1f, pivot = Offset.Zero) {
                                drawImage(
                                    image,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(image.width, image.height),
                                    dstOffset = IntOffset((-dw / 2f).roundToInt(), (-dh / 2f).roundToInt()),
                                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                                    alpha = layer.opacity,
                                    filterQuality = FilterQuality.Medium,
                                )
                                if (layer.id == selected) {
                                    drawRect(
                                        accent,
                                        topLeft = Offset(-dw / 2f, -dh / 2f),
                                        size = Size(dw, dh),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Si el punto [pos] (px de la vista) cae dentro de la capa, teniendo en cuenta el giro. */
private fun contains(layer: ImageLayer, pos: Offset, w: Float, h: Float): Boolean {
    val dx = pos.x - layer.centerX * w
    val dy = pos.y - layer.centerY * h
    val radians = Math.toRadians(-layer.rotation.toDouble())
    val x = dx * cos(radians) - dy * sin(radians)
    val y = dx * sin(radians) + dy * cos(radians)
    val halfW = layer.width * w / 2f
    val halfH = halfW * layer.aspect
    return x in -halfW..halfW && y in -halfH..halfH
}

/** Panel de Capas: miniaturas (tocar para elegir), añadir, opacidad y acciones de la elegida. */
@Composable
fun LayersPanel(
    layers: List<ImageLayer>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpacity: (Float) -> Unit,
    onAction: (LayerAction) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    val current = layers.firstOrNull { it.id == selected }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.layer_add), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            layers.asReversed().forEach { layer ->
                val bitmap = LayerImages.get(layer.uri)
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (layer.id == selected) 2.dp else 0.dp, if (layer.id == selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onSelect(layer.id) },
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            if (layers.isEmpty()) {
                Text(
                    stringResource(R.string.layer_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        if (current != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.layer_opacity), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = current.opacity,
                    onValueChange = onOpacity,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text("${(current.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LayerButton(Icons.Outlined.FitScreen, R.string.layer_fit) { onAction(LayerAction.FIT) }
                LayerButton(Icons.Outlined.Fullscreen, R.string.layer_fill) { onAction(LayerAction.FILL) }
                LayerButton(Icons.Outlined.CenterFocusStrong, R.string.layer_center) { onAction(LayerAction.CENTER) }
                LayerButton(Icons.Outlined.Flip, R.string.frame_flip) { onAction(LayerAction.FLIP) }
                LayerButton(Icons.Outlined.FlipToFront, R.string.layer_front) { onAction(LayerAction.FRONT) }
                LayerButton(Icons.Outlined.FlipToBack, R.string.layer_back) { onAction(LayerAction.BACK) }
                LayerButton(Icons.Outlined.Delete, R.string.layer_delete) { onAction(LayerAction.DELETE) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.layer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        ToolActionRow(title = stringResource(Tool.LAYERS.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

@Composable
private fun LayerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
