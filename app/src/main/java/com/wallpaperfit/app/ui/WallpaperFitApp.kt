package com.wallpaperfit.app.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.wallpaperfit.app.ui.settings.SettingsScreen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import android.Manifest
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wallpaperfit.app.model.ToolGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Tool
import androidx.compose.ui.graphics.Color
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.ui.components.AdjustPanel
import com.wallpaperfit.app.ui.components.DismissibleSnackbarHost
import com.wallpaperfit.app.ui.components.LayerStage
import com.wallpaperfit.app.ui.components.LayersPanel
import com.wallpaperfit.app.ui.gallery.GalleryPicker
import com.wallpaperfit.app.ui.components.BrushPanel
import com.wallpaperfit.app.ui.components.CurvesPanel
import com.wallpaperfit.app.ui.components.CurvesStage
import com.wallpaperfit.app.ui.components.HistorySheet
import com.wallpaperfit.app.ui.components.LensShapeSelector
import com.wallpaperfit.app.ui.components.LensStage
import com.wallpaperfit.app.ui.components.AppLogo
import com.wallpaperfit.app.ui.components.CropPanel
import com.wallpaperfit.app.ui.components.CropStage
import com.wallpaperfit.app.ui.components.EditorTopBar
import com.wallpaperfit.app.ui.components.ExpandPanel
import com.wallpaperfit.app.ui.components.ExpandStage
import com.wallpaperfit.app.ui.components.ExportSheet
import com.wallpaperfit.app.ui.components.FilterPanel
import com.wallpaperfit.app.ui.components.FitPanel
import com.wallpaperfit.app.ui.components.FrameStage
import com.wallpaperfit.app.ui.components.HealPanel
import com.wallpaperfit.app.ui.components.HealStage
import com.wallpaperfit.app.ui.components.ImageStage
import com.wallpaperfit.app.ui.components.ToolBar
import com.wallpaperfit.app.ui.theme.CanvasBackground
import com.wallpaperfit.app.ui.wallpapers.UriImage
import com.wallpaperfit.app.ui.wallpapers.WallpapersScreen
import com.wallpaperfit.app.wallpaper.WallpaperStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

/** Color del trazo del Pincel: cálido si el valor aclara/sube, frío si oscurece/baja. */
private val BrushPositive = Color(0x99FFC14D)
private val BrushNegative = Color(0x994DA6FF)

@Composable
fun WallpaperFitApp(openWallpapers: Boolean = false, viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Pantalla de fondos: desde el inicio, desde el widget del reloj o desde los ajustes del fondo.
    var showWallpapers by rememberSaveable { mutableStateOf(openWallpapers) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openWallpapers) { if (openWallpapers) showWallpapers = true }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Selector propio (abre en la última carpeta y a la misma altura). El Photo Picker del
    // sistema queda como alternativa: no requiere READ_MEDIA_IMAGES.
    var picker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    // De dónde se abrió el editor: al salir de él (Atrás) se vuelve ahí.
    var editorOrigin by rememberSaveable { mutableStateOf(EditorOrigin.PICKER) }
    var reopenMisaligned by rememberSaveable { mutableStateOf(false) }
    // Acción pendiente de confirmar porque hay cambios sin guardar (salir o abrir otra foto).
    var confirmDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Última foto editada: el selector vuelve a su carpeta y a su altura.
    var lastEdited by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            editorOrigin = EditorOrigin.PICKER
            viewModel.loadImage(uri)
        }
    }
    val pickLayer = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addLayer(uri)
    }
    val openImage = {
        if (state.hasUnsavedChanges) confirmDiscard = { picker = PickerTarget.EDIT } else picker = PickerTarget.EDIT
    }
    val addLayer = { picker = PickerTarget.LAYER }

    // Solo Android 9 o inferior necesita permiso de escritura para la galería pública.
    val requestWritePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveToGallery() else viewModel.onStoragePermissionDenied()
    }
    // Selector de carpeta del sistema: el permiso se guarda para usarlo en siguientes sesiones.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setSaveFolder(uri)
        }
    }
    val changeFolder = { pickFolder.launch(null) }

    // Permiso del sistema para sobrescribir la foto original.
    val writeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onWriteGranted() else viewModel.onWriteDenied()
    }
    LaunchedEffect(viewModel) {
        viewModel.writeRequests.collect { writeRequest.launch(IntentSenderRequest.Builder(it).build()) }
    }

    val saveToGallery: () -> Unit = {
        // Con una carpeta elegida no hace falta el permiso antiguo de almacenamiento.
        val needsLegacyPermission = state.saveFolderName == null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveToGallery()
        }
    }

    when {
        picker != null -> {
            val target = picker!!
            GalleryPicker(
                title = stringResource(if (target == PickerTarget.EDIT) R.string.picker_title else R.string.picker_layer_title),
                onPick = { uri ->
                    picker = null
                    if (target == PickerTarget.EDIT) {
                        editorOrigin = EditorOrigin.PICKER
                        viewModel.loadImage(uri)
                    } else {
                        viewModel.addLayer(uri)
                    }
                },
                onCancel = { picker = null },
                onSystemPicker = {
                    picker = null
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (target == PickerTarget.EDIT) pickImage.launch(request) else pickLayer.launch(request)
                },
                rememberPosition = target == PickerTarget.EDIT,
                // Empezar en la carpeta de la foto que se está editando (o de la última editada).
                focus = if (target == PickerTarget.EDIT) (if (state.hasImage) state.sourceUri else lastEdited) else null,
            )
        }
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(onBack = { showSettings = false })
        }
        showWallpapers -> {
            BackHandler {
                showWallpapers = false
                reopenMisaligned = false
            }
            WallpapersScreen(
                onBack = {
                    showWallpapers = false
                    reopenMisaligned = false
                },
                // Abre la foto en el editor; al salir de él se vuelve aquí (o a la lista de fotos
                // que no encajan, si se abrió desde ella).
                onEdit = { uri, fromMisaligned ->
                    editorOrigin = if (fromMisaligned) EditorOrigin.MISALIGNED else EditorOrigin.WALLPAPERS
                    reopenMisaligned = false
                    viewModel.loadImage(android.net.Uri.parse(uri), fromWallpapers = true)
                    showWallpapers = false
                },
                reopenMisaligned = reopenMisaligned,
            )
        }
        state.hasImage -> {
            // Atrás sin herramienta abierta: de vuelta a donde se eligió la foto (el selector, en
            // su carpeta y altura; Fondos; o la lista de fotos que no encajan).
            val leaveEditor = {
                lastEdited = state.sourceUri
                viewModel.closeImage()
                when (editorOrigin) {
                    EditorOrigin.PICKER -> picker = PickerTarget.EDIT
                    EditorOrigin.WALLPAPERS -> showWallpapers = true
                    EditorOrigin.MISALIGNED -> {
                        reopenMisaligned = true
                        showWallpapers = true
                    }
                }
            }
            BackHandler(enabled = state.activeTool == null) {
                if (state.hasUnsavedChanges) confirmDiscard = leaveEditor else leaveEditor()
            }
            EditorScreen(
                state = state,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onOpenImage = openImage,
                onAddLayer = addLayer,
                onSaveToGallery = saveToGallery,
                onChangeFolder = changeFolder,
            )
        }
        else -> StartScreen(
            isLoading = state.isLoading,
            deviceSize = state.deviceSize,
            snackbarHostState = snackbarHostState,
            onOpenImage = openImage,
            onOpenWallpapers = { showWallpapers = true },
            onOpenSettings = { showSettings = true },
        )
    }

    confirmDiscard?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = null
                    action()
                }) { Text(stringResource(R.string.unsaved_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = null }) { Text(stringResource(R.string.unsaved_keep)) }
            },
        )
    }
}

/** Para qué se abre el selector de fotos. */
private enum class PickerTarget { EDIT, LAYER }

/** Desde dónde se abrió el editor (a dónde vuelve con Atrás). */
private enum class EditorOrigin { PICKER, WALLPAPERS, MISALIGNED }

/** Inicio: las dos partes de la app, editar una foto o gestionar los fondos. */
@Composable
private fun StartScreen(
    isLoading: Boolean,
    deviceSize: TargetSize,
    snackbarHostState: SnackbarHostState,
    onOpenImage: () -> Unit,
    onOpenWallpapers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val wallpaperConfig by store.config.collectAsStateWithLifecycle()
    val current by store.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = CanvasBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { DismissibleSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLogo(modifier = Modifier.size(104.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.hub_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                HubCard(
                    title = stringResource(R.string.hub_edit),
                    description = stringResource(R.string.hub_edit_desc),
                    footnote = stringResource(R.string.start_default_resolution, deviceSize.width, deviceSize.height),
                    icon = Icons.Outlined.PhotoLibrary,
                    accent = MaterialTheme.colorScheme.primary,
                    busy = isLoading,
                    onClick = onOpenImage,
                )
                Spacer(Modifier.height(14.dp))
                HubCard(
                    title = stringResource(R.string.hub_wallpapers),
                    description = stringResource(R.string.hub_wallpapers_desc),
                    footnote = if (current.current != null) {
                        // Cuenta fuentes (carpetas, fotos sueltas, etiquetas), no fotos: leer las carpetas aquí sería lento.
                        pluralStringResource(R.plurals.hub_wallpapers_active, wallpaperConfig.sources.size, wallpaperConfig.sources.size)
                    } else {
                        null
                    },
                    icon = Icons.Outlined.Wallpaper,
                    accent = MaterialTheme.colorScheme.secondary,
                    busy = false,
                    onClick = onOpenWallpapers,
                    preview = current.current,
                )
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    description: String,
    footnote: String?,
    icon: ImageVector,
    accent: Color,
    busy: Boolean,
    onClick: () -> Unit,
    preview: String? = null,
) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    UriImage(preview, Modifier.fillMaxSize(), side = 160)
                } else if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accent)
                } else {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (footnote != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(footnote, style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorScreen(
    state: EditorUiState,
    viewModel: EditorViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenImage: () -> Unit,
    onAddLayer: () -> Unit,
    onSaveToGallery: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    val tool = state.activeTool
    val params = tool?.let { AdjustParam.forTool(it) }.orEmpty()

    BackHandler(enabled = tool != null) { viewModel.cancelTool() }
    // La pestaña de herramientas se recuerda aquí: la barra desaparece mientras hay una herramienta abierta.
    var toolGroup by rememberSaveable { mutableStateOf(ToolGroup.COMPOSE) }

    Scaffold(
        containerColor = CanvasBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            EditorTopBar(
                activeTool = tool,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onOpenImage = onOpenImage,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onHistory = { viewModel.showHistorySheet(true) },
                onCompare = viewModel::setShowOriginal,
                onExport = { viewModel.showExportSheet(true) },
                exportEnabled = !state.isProcessing,
            )
        },
        bottomBar = {
            when (tool) {
                null -> ToolBar(group = toolGroup, onGroupSelected = { toolGroup = it }, onToolSelected = viewModel::openTool)
                Tool.CROP -> CropPanel(
                    aspect = state.cropAspect,
                    onAspectSelected = viewModel::selectCropAspect,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.EXPAND -> ExpandPanel(
                    fill = state.working.content.expansion.fill,
                    detail = state.working.content.expansion.detail,
                    targetSize = state.working.fit.size,
                    onFillSelected = viewModel::onExpandFillSelected,
                    onDetailSelected = viewModel::onExpandDetailSelected,
                    onFitToScreen = viewModel::fitExpansionToScreen,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.LAYERS -> LayersPanel(
                    layers = state.working.layers,
                    selected = state.selectedLayer,
                    onSelect = viewModel::selectLayer,
                    onAdd = onAddLayer,
                    onOpacity = viewModel::setLayerOpacity,
                    onAction = viewModel::layerAction,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.HEAL -> HealPanel(
                    brushSize = state.brushSize,
                    shape = state.healShape,
                    engine = state.healEngine,
                    canUndoStroke = state.working.content.heals.size > state.committed.content.heals.size,
                    isProcessing = state.isProcessing,
                    isDetectingText = state.isDetectingText,
                    onBrushSizeChange = viewModel::setBrushSize,
                    onShapeChange = viewModel::setHealShape,
                    onEngineChange = viewModel::setHealEngine,
                    onDetectText = viewModel::detectText,
                    onUndoStroke = viewModel::undoHealStroke,
                    canRedoStroke = state.healRedo.isNotEmpty(),
                    onRedoStroke = viewModel::redoHealStroke,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.FIT -> FitPanel(
                    widthText = state.widthText,
                    heightText = state.heightText,
                    isValid = state.isResolutionValid,
                    selectedPreset = state.selectedPreset,
                    deviceSize = state.deviceSize,
                    isDeviceSize = state.isDeviceSize,
                    fill = state.working.fit.fill,
                    onWidthChange = viewModel::onWidthChange,
                    onHeightChange = viewModel::onHeightChange,
                    onDeviceSelected = viewModel::selectDeviceSize,
                    onPresetSelected = viewModel::selectPreset,
                    onFillSelected = viewModel::onFrameFillSelected,
                    onFit = viewModel::frameFit,
                    onFill = viewModel::frameFill,
                    onRotate = viewModel::frameRotate90,
                    onFlip = viewModel::frameFlip,
                    onAlign = viewModel::alignFrame,
                    magnet = state.frameMagnet,
                    onMagnetChange = viewModel::setFrameMagnet,
                    onNudge = viewModel::nudgeFrame,
                    scalePercent = state.working.fit.transform.scale * 100f,
                    rotationDegrees = state.working.fit.transform.rotation,
                    onZoomStep = viewModel::zoomFrameStep,
                    onRotationStep = viewModel::rotateFrameStep,
                    shiftPx = state.working.fit.let { fit ->
                        Math.round(fit.transform.offsetX * fit.size.width) to Math.round(fit.transform.offsetY * fit.size.height)
                    },
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.FILTERS -> FilterPanel(
                    filter = state.working.filter,
                    thumbnails = state.filterThumbnails,
                    onSelect = viewModel::selectFilter,
                    onIntensityChange = viewModel::setFilterIntensity,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.CURVES -> CurvesPanel(
                    channel = state.curveChannel,
                    onChannelSelected = viewModel::selectCurveChannel,
                    onPreset = viewModel::applyCurvePreset,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                Tool.BRUSH -> BrushPanel(
                    effect = state.brushEffect,
                    value = state.brushValue,
                    brushSize = state.brushSize,
                    canUndoStroke = state.working.brush.size > state.committed.brush.size,
                    onEffectSelected = viewModel::selectBrushEffect,
                    onValueChange = viewModel::setBrushValue,
                    onBrushSizeChange = viewModel::setBrushSize,
                    onUndoStroke = viewModel::undoBrushStroke,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                )
                else -> AdjustPanel(
                    tool = tool,
                    params = params,
                    selected = state.selectedParam,
                    adjustments = state.working.adjustments,
                    onSelect = viewModel::selectParam,
                    onValueChange = viewModel::setParamValue,
                    onCancel = viewModel::cancelTool,
                    onReset = viewModel::resetTool,
                    onApply = viewModel::applyTool,
                    hint = stringResource(if (tool == Tool.LENS_BLUR) R.string.lens_hint else R.string.gesture_hint),
                    extra = {
                        if (tool == Tool.LENS_BLUR) LensShapeSelector(state.working.lens.shape, viewModel::setLensShape)
                    },
                )
            }
        },
        snackbarHost = { DismissibleSnackbarHost(snackbarHostState) },
    ) { padding ->
        when (tool) {
            Tool.CROP -> CropStage(
                image = state.originalPreview,
                crop = state.working.content.crop,
                croppedWidth = state.croppedWidth,
                croppedHeight = state.croppedHeight,
                onDrag = viewModel::onCropDrag,
                modifier = Modifier.padding(padding),
            )
            Tool.EXPAND -> ExpandStage(
                originalPreview = state.originalPreview,
                crop = state.working.content.crop,
                originalWidth = state.croppedWidth,
                originalHeight = state.croppedHeight,
                expansion = state.working.content.expansion,
                draft = state.expandDraft,
                isLoading = state.isExpandDraftLoading,
                onDrag = viewModel::onExpandDrag,
                modifier = Modifier.padding(padding),
            )
            Tool.FIT -> FrameStage(
                image = state.baseView,
                preview = state.preview,
                fit = state.working.fit,
                onGesture = { g ->
                    viewModel.onFrameGesture(
                        g.centroid.x, g.centroid.y, g.pan.x, g.pan.y, g.zoom, g.rotation,
                        g.frameSize.width, g.frameSize.height, g.snapDistance,
                    )
                },
                onGestureEnd = viewModel::onFrameGestureEnd,
                onDoubleTap = viewModel::frameFill,
                modifier = Modifier.padding(padding),
            )
            Tool.LAYERS -> LayerStage(
                preview = state.preview,
                targetSize = state.working.fit.size,
                layers = state.working.layers,
                selected = state.selectedLayer,
                onSelect = viewModel::selectLayer,
                onGesture = viewModel::onLayerGesture,
                modifier = Modifier.padding(padding),
            )
            Tool.HEAL -> HealStage(
                baseView = state.baseView,
                geometry = state.baseGeometry,
                brushSizeDp = state.brushSize,
                shape = state.healShape,
                isProcessing = state.isProcessing,
                onStroke = viewModel::addHealStroke,
                modifier = Modifier.padding(padding),
            )
            Tool.BRUSH -> HealStage(
                baseView = state.baseView,
                geometry = state.baseGeometry,
                brushSizeDp = state.brushSize,
                shape = HealShape.STROKE,
                isProcessing = false,
                onStroke = viewModel::addBrushStroke,
                strokeColor = if (state.brushValue >= 0f) BrushPositive else BrushNegative,
                modifier = Modifier.padding(padding),
            )
            Tool.CURVES -> CurvesStage(
                preview = state.preview,
                targetSize = state.working.fit.size,
                curves = state.working.curves,
                channel = state.curveChannel,
                onCurveChange = viewModel::setCurve,
                modifier = Modifier.padding(padding),
            )
            Tool.LENS_BLUR -> LensStage(
                preview = state.preview,
                targetSize = state.working.fit.size,
                lens = state.working.lens,
                transition = state.working.adjustments[AdjustParam.LENS_TRANSITION] / 100f,
                onGesture = viewModel::onLensGesture,
                modifier = Modifier.padding(padding),
            )
            else -> ImageStage(
                preview = state.preview,
                targetSize = state.working.fit.size,
                activeTool = tool,
                params = params,
                selectedParam = state.selectedParam,
                adjustments = state.working.adjustments,
                showOriginal = state.showOriginal,
                isProcessing = state.isProcessing,
                onSelectParam = viewModel::selectParam,
                onAdjustBy = viewModel::adjustSelectedBy,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.showHistorySheet) {
        HistorySheet(
            steps = state.historySteps,
            current = state.historyIndex,
            onSelect = viewModel::goToHistory,
            onDismiss = { viewModel.showHistorySheet(false) },
        )
    }

    if (state.showExportSheet) {
        ExportSheet(
            size = state.committed.fit.size,
            format = state.exportFormat,
            isExporting = state.isExporting,
            saveFolderName = state.saveFolderName,
            sourceFolderName = state.sourceFolderName,
            saveMode = state.saveMode,
            originalName = state.originalName,
            onFormatSelected = viewModel::onFormatSelected,
            onSaveModeSelected = viewModel::setSaveMode,
            onChangeFolder = onChangeFolder,
            onResetFolder = viewModel::resetSaveFolder,
            onSaveToGallery = onSaveToGallery,
            onReplaceOriginal = viewModel::replaceOriginal,
            onSetWallpaper = viewModel::setAsWallpaper,
            liveWallpaperActive = state.liveWallpaperActive,
            showOnWallpaper = state.showOnWallpaper,
            onShowOnWallpaperChange = viewModel::setShowOnWallpaper,
            onDismiss = { viewModel.showExportSheet(false) },
        )
    }
}
