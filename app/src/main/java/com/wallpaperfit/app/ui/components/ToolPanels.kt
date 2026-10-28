package com.wallpaperfit.app.ui.components

import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.ui.layout.Layout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AlignHorizontalCenter
import androidx.compose.material.icons.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.outlined.AlignHorizontalRight
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.PlatformFeatures
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.BrushEffect
import com.wallpaperfit.app.model.CropAspect
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CurvePreset
import com.wallpaperfit.app.model.LensShape
import com.wallpaperfit.app.model.ExpandFill
import com.wallpaperfit.app.model.FillDetail
import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FilterSettings
import com.wallpaperfit.app.model.FrameAlign
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.ResolutionPreset
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.ui.EditorUiState
import com.wallpaperfit.app.ui.description
import com.wallpaperfit.app.ui.format
import com.wallpaperfit.app.ui.label
import kotlin.math.roundToInt

/** Fondo común de los paneles inferiores. */
@Composable
private fun PanelColumn(top: Int = 8, imeAware: Boolean = false, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .then(if (imeAware) Modifier.imePadding() else Modifier)
            .padding(top = top.dp),
    ) {
        content()
    }
}

@Composable
private fun HintText(text: String, singleLine: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

/** Panel de las herramientas de ajuste: chips de parámetro, deslizador y acciones. */
@Composable
fun AdjustPanel(
    tool: Tool,
    params: List<AdjustParam>,
    selected: AdjustParam?,
    adjustments: Adjustments,
    onSelect: (AdjustParam) -> Unit,
    onValueChange: (AdjustParam, Float) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    hint: String = stringResource(R.string.gesture_hint),
    extra: @Composable () -> Unit = {},
) {
    PanelColumn {
        extra()
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            params.forEach { param ->
                FilterChip(
                    selected = param == selected,
                    onClick = { onSelect(param) },
                    label = { Text("${stringResource(param.label)}  ${param.format(adjustments[param])}") },
                )
            }
        }
        if (selected != null) {
            Slider(
                value = adjustments[selected],
                onValueChange = { onValueChange(selected, it) },
                valueRange = selected.min..selected.max,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        HintText(hint)
        ToolActionRow(title = stringResource(tool.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

/** Selector de la forma del Desenfoque de lente (va encima de los parámetros). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensShapeSelector(shape: LensShape, onShapeChange: (LensShape) -> Unit) {
    val shapes = LensShape.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        shapes.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == shape,
                onClick = { onShapeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = shapes.size),
                label = { Text(stringResource(option.label)) },
            )
        }
    }
}

/** Panel de Curvas: canal, curvas predefinidas y acciones. La curva se edita sobre la imagen. */
@Composable
fun CurvesPanel(
    channel: CurveChannel,
    onChannelSelected: (CurveChannel) -> Unit,
    onPreset: (CurvePreset) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 10) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurveChannel.entries.forEach { option ->
                FilterChip(
                    selected = option == channel,
                    onClick = { onChannelSelected(option) },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurvePreset.entries.forEach { preset ->
                OutlinedButton(onClick = { onPreset(preset) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(stringResource(preset.label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        HintText(stringResource(R.string.curves_hint))
        ToolActionRow(title = stringResource(Tool.CURVES.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

/** Panel del Pincel: efecto, valor (0 borra), tamaño del pincel y deshacer trazo. */
@Composable
fun BrushPanel(
    effect: BrushEffect,
    value: Float,
    brushSize: Float,
    canUndoStroke: Boolean,
    onEffectSelected: (BrushEffect) -> Unit,
    onValueChange: (Float) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onUndoStroke: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 10) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrushEffect.entries.forEach { option ->
                FilterChip(
                    selected = option == effect,
                    onClick = { onEffectSelected(option) },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.brush_value), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = value,
                // Pasos de 10 como los niveles del pincel de Snapseed.
                onValueChange = { onValueChange((it / 10f).roundToInt() * 10f) },
                valueRange = -100f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            val rounded = value.roundToInt()
            Text(
                text = if (rounded == 0) stringResource(R.string.brush_erase) else if (rounded > 0) "+$rounded" else "$rounded",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.brush_size), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = EditorUiState.MIN_BRUSH..EditorUiState.MAX_BRUSH,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            TextButton(onClick = onUndoStroke, enabled = canUndoStroke) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.undo_stroke), modifier = Modifier.size(18.dp))
            }
        }
        HintText(stringResource(R.string.brush_hint))
        ToolActionRow(title = stringResource(Tool.BRUSH.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

/** Panel de Filtros: miniaturas de cada filtro e intensidad. */
@Composable
fun FilterPanel(
    filter: FilterSettings,
    thumbnails: Map<FilterPreset, ImageBitmap>,
    onSelect: (FilterPreset) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 12) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterPreset.entries.forEach { preset ->
                FilterThumbnail(
                    label = stringResource(preset.label),
                    image = thumbnails[preset],
                    selected = preset == filter.preset,
                    onClick = { onSelect(preset) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.filter_intensity), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = filter.intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..100f,
                enabled = filter.preset != FilterPreset.NONE,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(filter.intensity.roundToInt().toString(), style = MaterialTheme.typography.labelLarge)
        }
        ToolActionRow(title = stringResource(Tool.FILTERS.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

@Composable
private fun FilterThumbnail(label: String, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                ),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Panel de Encuadre: resolución destino (atajos o a mano), relleno de los huecos y acciones
 * rápidas sobre la imagen. Moverla, acercarla y girarla se hace con gestos sobre el recuadro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitPanel(
    widthText: String,
    heightText: String,
    isValid: Boolean,
    selectedPreset: ResolutionPreset?,
    deviceSize: TargetSize,
    isDeviceSize: Boolean,
    fill: FrameFill,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onDeviceSelected: () -> Unit,
    onPresetSelected: (ResolutionPreset) -> Unit,
    onFillSelected: (FrameFill) -> Unit,
    onFit: () -> Unit,
    onFill: () -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onAlign: (FrameAlign) -> Unit,
    magnet: Boolean,
    onMagnetChange: (Boolean) -> Unit,
    onNudge: (Int, Int) -> Unit,
    /** Desplazamiento actual de la imagen en píxeles del resultado (x, y). */
    shiftPx: Pair<Int, Int>,
    /** Zoom actual en % y giro en grados, para los ajustes paso a paso. */
    scalePercent: Float,
    rotationDegrees: Float,
    onZoomStep: (Int) -> Unit,
    onRotationStep: (Float) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    // Pestañas para que el panel ocupe poco y la vista previa se vea grande; además se puede
    // plegar del todo para ver la imagen a pantalla casi completa mientras se ajusta.
    var tab by rememberSaveable { mutableStateOf(FitTab.FRAME) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    PanelColumn(top = 6, imeAware = true) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                FitTab.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == tab && !collapsed,
                        onClick = {
                            tab = option
                            collapsed = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = FitTab.entries.size),
                        icon = {},
                        label = { Text(stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            IconToggleButton(checked = collapsed, onCheckedChange = { collapsed = it }) {
                Icon(
                    if (collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                    contentDescription = stringResource(if (collapsed) R.string.fit_show_options else R.string.fit_hide_options),
                )
            }
        }
        if (!collapsed) {
            // Las tres pestañas miden lo mismo (la más alta): al cambiar de una a otra los botones
            // no se mueven de sitio.
            SameHeightPages(selected = tab.ordinal) {
                Column {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = isDeviceSize,
                            onClick = onDeviceSelected,
                            label = { Text(stringResource(R.string.preset_device, deviceSize.width, deviceSize.height)) },
                        )
                        ResolutionPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = preset == selectedPreset && !isDeviceSize,
                                onClick = { onPresetSelected(preset) },
                                label = { Text(stringResource(preset.label)) },
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = onWidthChange,
                                label = { Text(stringResource(R.string.width_label)) },
                                singleLine = true,
                                isError = !isValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                            )
                            Text("×", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = onHeightChange,
                                label = { Text(stringResource(R.string.height_label)) },
                                singleLine = true,
                                isError = !isValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!isValid) {
                            Text(
                                text = stringResource(R.string.resolution_error, EditorUiState.MIN_SIDE, EditorUiState.MAX_SIDE),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.frame_empty_fill), style = MaterialTheme.typography.labelLarge)
                    val fills = FrameFill.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        fills.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == fill,
                                onClick = { onFillSelected(option) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = fills.size),
                                label = { Text(stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrameAction(Icons.Outlined.FitScreen, stringResource(R.string.frame_fit), onFit, Modifier.weight(1f))
                        FrameAction(Icons.Outlined.Fullscreen, stringResource(R.string.frame_fill), onFill, Modifier.weight(1f))
                        FrameAction(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.frame_rotate), onRotate, Modifier.weight(1f))
                        FrameAction(Icons.Outlined.Flip, stringResource(R.string.frame_flip), onFlip, Modifier.weight(1f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.frame_align), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        AlignButton(Icons.Outlined.AlignHorizontalLeft, R.string.align_left) { onAlign(FrameAlign.LEFT) }
                        AlignButton(Icons.Outlined.AlignHorizontalCenter, R.string.align_center_h) { onAlign(FrameAlign.CENTER_HORIZONTAL) }
                        AlignButton(Icons.Outlined.AlignHorizontalRight, R.string.align_right) { onAlign(FrameAlign.RIGHT) }
                        AlignButton(Icons.Outlined.VerticalAlignTop, R.string.align_top) { onAlign(FrameAlign.TOP) }
                        AlignButton(Icons.Outlined.VerticalAlignCenter, R.string.align_center_v) { onAlign(FrameAlign.CENTER_VERTICAL) }
                        AlignButton(Icons.Outlined.VerticalAlignBottom, R.string.align_bottom) { onAlign(FrameAlign.BOTTOM) }
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Ajuste fino: cada toque mueve 1 px del resultado; mantener pulsado repite y acelera.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(stringResource(R.string.frame_nudge), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.frame_shift, shiftPx.first, shiftPx.second),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // Cruceta: cada flecha mueve la imagen hacia donde apunta.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NudgeButton(Icons.Outlined.KeyboardArrowUp, R.string.nudge_up) { onNudge(0, -it) }
                            Row {
                                NudgeButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, R.string.nudge_left) { onNudge(-it, 0) }
                                Spacer(Modifier.size(38.dp))
                                NudgeButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, R.string.nudge_right) { onNudge(it, 0) }
                            }
                            NudgeButton(Icons.Outlined.KeyboardArrowDown, R.string.nudge_down) { onNudge(0, it) }
                        }
                        Spacer(Modifier.weight(1f))
                        FilterChip(
                            selected = magnet,
                            onClick = { onMagnetChange(!magnet) },
                            label = { Text(stringResource(R.string.frame_magnet)) },
                        )
                    }
                    // Zoom y giro paso a paso (mantener pulsado repite y acelera).
                    StepperRow(
                        label = stringResource(R.string.fit_zoom_value, scalePercent),
                        decrease = R.string.fit_zoom_out,
                        increase = R.string.fit_zoom_in,
                        onStep = onZoomStep,
                    )
                    StepperRow(
                        label = stringResource(R.string.fit_rotation_value, rotationDegrees),
                        decrease = R.string.fit_rotate_left,
                        increase = R.string.fit_rotate_right,
                        onStep = { onRotationStep(it * ROTATION_STEP) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HintText(stringResource(R.string.frame_hint), singleLine = true)
        }
        ToolActionRow(title = stringResource(Tool.FIT.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

/**
 * Mide todas las páginas y ocupa la altura de la más alta, pero solo coloca (y dibuja) la
 * [selected]; las demás no reciben toques.
 */
@Composable
private fun SameHeightPages(selected: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            placeables.getOrNull(selected)?.placeRelative(0, 0)
        }
    }
}

/** Pestañas del panel de Encuadre. */
private enum class FitTab(@StringRes val label: Int) {
    SIZE(R.string.fit_tab_size),
    FRAME(R.string.fit_tab_frame),
    PRECISE(R.string.fit_tab_precise),
}

/** Paso del giro fino (grados por toque). */
private const val ROTATION_STEP = 0.1f

/** Fila "valor  [−] [+]": un toque = 1 paso; mantener pulsado repite y acelera. */
@Composable
private fun StepperRow(label: String, @StringRes decrease: Int, @StringRes increase: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        NudgeButton(Icons.Outlined.Remove, decrease) { onStep(-it) }
        Spacer(Modifier.width(8.dp))
        NudgeButton(Icons.Outlined.Add, increase) { onStep(it) }
    }
}

/**
 * Botón de flecha: un toque = 1 px. Mantenido, repite cada vez más deprisa y con pasos mayores
 * (1 → 4 → 12 px) para recorrer distancias largas sin soltar.
 */
@Composable
private fun NudgeButton(icon: ImageVector, @StringRes description: Int, onStep: (Int) -> Unit) {
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    step(1)
                    var delayMs = 350L
                    var repeats = 0
                    // Al soltar (o cancelar) termina; si pasa el tiempo sin soltar, otro paso.
                    while (withTimeoutOrNull(delayMs) { waitForUpOrCancellation(); true } == null) {
                        repeats++
                        step(
                            when {
                                repeats < 8 -> 1
                                repeats < 20 -> 4
                                else -> 12
                            },
                        )
                        delayMs = (delayMs * 0.8f).toLong().coerceAtLeast(50L)
                    }
                    pressed = false
                }
            },
    ) {
        Icon(icon, contentDescription = stringResource(description))
    }
}

@Composable
private fun AlignButton(icon: ImageVector, @StringRes description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
    }
}

/** Panel de Recortar: proporción del recorte. El rectángulo se ajusta con gestos sobre la foto. */
@Composable
fun CropPanel(
    aspect: CropAspect,
    onAspectSelected: (CropAspect) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 12) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropAspect.entries.forEach { option ->
                FilterChip(
                    selected = option == aspect,
                    onClick = { onAspectSelected(option) },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        HintText(stringResource(R.string.crop_hint))
        ToolActionRow(title = stringResource(Tool.CROP.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

@Composable
private fun FrameAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Panel de Expandir: tipo de relleno y ajuste automático a la proporción de la pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandPanel(
    fill: ExpandFill,
    detail: FillDetail,
    targetSize: TargetSize,
    onFillSelected: (ExpandFill) -> Unit,
    onDetailSelected: (FillDetail) -> Unit,
    onFitToScreen: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 12) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fills = ExpandFill.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                fills.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == fill,
                        onClick = { onFillSelected(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = fills.size),
                        label = { Text(stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (fill == ExpandFill.SMART) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FillDetail.entries.forEach { option ->
                        FilterChip(
                            selected = option == detail,
                            onClick = { onDetailSelected(option) },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
                Text(
                    text = stringResource(detail.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onFitToScreen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.expand_fit_screen, targetSize.width, targetSize.height))
            }
        }
        Spacer(Modifier.height(6.dp))
        HintText(stringResource(R.string.expand_hint))
        ToolActionRow(title = stringResource(Tool.EXPAND.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}

/**
 * Panel del Corrector: forma de selección (pincel o rectángulo), motor de relleno (IA o clásico),
 * detección automática de textos y firmas, tamaño del pincel y deshacer el último trazo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealPanel(
    brushSize: Float,
    shape: HealShape,
    engine: HealEngine,
    canUndoStroke: Boolean,
    isProcessing: Boolean,
    isDetectingText: Boolean,
    onBrushSizeChange: (Float) -> Unit,
    onShapeChange: (HealShape) -> Unit,
    onEngineChange: (HealEngine) -> Unit,
    onDetectText: () -> Unit,
    onUndoStroke: () -> Unit,
    canRedoStroke: Boolean,
    onRedoStroke: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    PanelColumn(top = 12) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val shapes = listOf(HealShape.STROKE, HealShape.RECT)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    shapes.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == shape,
                            onClick = { onShapeChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = shapes.size),
                            label = { Text(stringResource(option.label), maxLines = 1) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealEngine.entries.filter { it != HealEngine.AI || PlatformFeatures.AI_TOOLS }.forEach { option ->
                    FilterChip(
                        selected = option == engine,
                        onClick = { onEngineChange(option) },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (PlatformFeatures.AI_TOOLS) OutlinedButton(
                    onClick = onDetectText,
                    enabled = !isDetectingText && !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (isDetectingText) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.heal_detect_text))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUndoStroke, enabled = canUndoStroke && !isProcessing) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.undo_stroke))
                }
                IconButton(onClick = onRedoStroke, enabled = canRedoStroke && !isProcessing) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.redo_stroke))
                }
            }
            if (shape == HealShape.STROKE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.brush_size), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = brushSize,
                        onValueChange = onBrushSizeChange,
                        valueRange = EditorUiState.MIN_BRUSH..EditorUiState.MAX_BRUSH,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }
        }
        HintText(
            stringResource(
                when {
                    engine == HealEngine.MIX -> R.string.heal_hint_mix
                    engine == HealEngine.SHUFFLE -> R.string.heal_hint_shuffle
                    shape == HealShape.RECT -> R.string.heal_hint_rect
                    else -> R.string.heal_hint
                },
            ),
        )
        ToolActionRow(title = stringResource(Tool.HEAL.label), onCancel = onCancel, onReset = onReset, onApply = onApply)
    }
}
