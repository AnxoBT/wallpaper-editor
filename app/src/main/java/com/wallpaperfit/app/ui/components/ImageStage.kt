package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.ui.format
import com.wallpaperfit.app.ui.label
import com.wallpaperfit.app.ui.theme.CanvasBackground
import kotlin.math.abs

/** Unidades que cambia un parámetro al arrastrar el ancho completo de la imagen. */
private const val VALUE_PER_WIDTH = 120f
private val PARAM_STEP = 36.dp

private enum class DragAxis { NONE, HORIZONTAL, VERTICAL }

/**
 * Lienzo central del editor. Muestra la vista previa con la proporción de la pantalla destino y
 * aplica los gestos de la herramienta activa:
 * en las herramientas de ajuste: arrastre vertical para elegir parámetro, horizontal para cambiar
 * su valor, como en Snapseed.
 */
@Composable
fun ImageStage(
    preview: ImageBitmap?,
    targetSize: TargetSize,
    activeTool: Tool?,
    params: List<AdjustParam>,
    selectedParam: AdjustParam?,
    adjustments: Adjustments,
    showOriginal: Boolean,
    isProcessing: Boolean,
    onSelectParam: (AdjustParam) -> Unit,
    onAdjustBy: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adjustable = activeTool != null && params.isNotEmpty()
    var menuVisible by remember { mutableStateOf(false) }

    val currentParams by rememberUpdatedState(params)
    val currentSelected by rememberUpdatedState(selectedParam)
    val currentOnSelect by rememberUpdatedState(onSelectParam)
    val currentOnAdjust by rememberUpdatedState(onAdjustBy)

    val adjustGestures = if (adjustable) {
        Modifier.pointerInput(activeTool) {
            val stepPx = PARAM_STEP.toPx()
            var axis = DragAxis.NONE
            var accumulatedY = 0f
            var startIndex = 0
            detectDragGestures(
                onDragStart = {
                    axis = DragAxis.NONE
                    accumulatedY = 0f
                },
                onDragEnd = { menuVisible = false },
                onDragCancel = { menuVisible = false },
            ) { change, drag ->
                change.consume()
                if (axis == DragAxis.NONE) {
                    axis = if (abs(drag.x) >= abs(drag.y)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                    if (axis == DragAxis.VERTICAL) {
                        startIndex = currentParams.indexOf(currentSelected).coerceAtLeast(0)
                        menuVisible = true
                    }
                }
                when (axis) {
                    DragAxis.HORIZONTAL -> currentOnAdjust(drag.x / size.width * VALUE_PER_WIDTH)
                    DragAxis.VERTICAL -> {
                        accumulatedY += drag.y
                        val index = (startIndex + (accumulatedY / stepPx).toInt()).coerceIn(0, currentParams.lastIndex)
                        val param = currentParams[index]
                        if (param != currentSelected) currentOnSelect(param)
                    }
                    DragAxis.NONE -> Unit
                }
            }
        }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .then(adjustGestures)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = targetSize.width.toFloat() / targetSize.height
        val fitsHeight = maxWidth / maxHeight > ratio
        val imageWidth = if (fitsHeight) maxHeight * ratio else maxWidth
        val imageHeight = if (fitsHeight) maxHeight else maxWidth / ratio

        Box(
            modifier = Modifier
                .size(imageWidth, imageHeight),
            contentAlignment = Alignment.Center,
        ) {
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
        }

        if (isProcessing) {
            StatusChip(
                text = stringResource(R.string.processing),
                busy = true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showOriginal) {
            OverlayChip(
                text = stringResource(R.string.original_label),
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else if (adjustable && selectedParam != null && !menuVisible) {
            ValuePill(
                param = selectedParam,
                value = adjustments[selectedParam],
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (adjustable && menuVisible) {
            ParamMenu(params = params, selected = selectedParam, adjustments = adjustments)
        }
    }
}

@Composable
private fun OverlayChip(text: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f), modifier = modifier) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Indicador superior estilo Snapseed: nombre, valor y barra de posición del parámetro activo. */
@Composable
private fun ValuePill(param: AdjustParam, value: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.65f), modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(param.label), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = param.format(value),
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp),
            ) {
                val y = size.height / 2f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                val fraction = (value - param.min) / (param.max - param.min)
                val origin = if (param.isSigned) 0.5f else 0f
                drawLine(
                    color = accent,
                    start = Offset(size.width * origin, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Lista de parámetros que aparece al arrastrar en vertical. */
@Composable
private fun ParamMenu(params: List<AdjustParam>, selected: AdjustParam?, adjustments: Adjustments) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.78f)) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 200.dp)
                .padding(vertical = 8.dp),
        ) {
            params.forEach { param ->
                val isSelected = param == selected
                val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 24.dp)
                        .height(PARAM_STEP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(param.label),
                        color = color,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        text = param.format(adjustments[param]),
                        color = color.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
