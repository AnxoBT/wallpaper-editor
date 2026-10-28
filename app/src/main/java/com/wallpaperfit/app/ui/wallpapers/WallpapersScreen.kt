package com.wallpaperfit.app.ui.wallpapers

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButtonDefaults
import com.wallpaperfit.app.storage.PhotoTags
import androidx.compose.runtime.key
import com.wallpaperfit.app.ui.components.TagEditorDialog
import android.net.Uri
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.foundation.layout.widthIn
import com.wallpaperfit.app.wallpaper.TapAction
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.ui.res.pluralStringResource
import com.wallpaperfit.app.wallpaper.SourceSchedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import com.wallpaperfit.app.wallpaper.LockClockFont
import com.wallpaperfit.app.ui.components.GridFastScroller
import android.app.Activity
import com.wallpaperfit.app.ui.components.DismissibleSnackbarHost
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.theme.CanvasBackground
import com.wallpaperfit.app.wallpaper.AutoChange
import com.wallpaperfit.app.wallpaper.ClockAlign
import com.wallpaperfit.app.wallpaper.ClockColorMode
import com.wallpaperfit.app.wallpaper.ClockConfig
import com.wallpaperfit.app.wallpaper.ClockFont
import com.wallpaperfit.app.wallpaper.ClockShadow
import com.wallpaperfit.app.wallpaper.ClockWidget
import com.wallpaperfit.app.wallpaper.DateFormat
import com.wallpaperfit.app.wallpaper.DatePosition
import com.wallpaperfit.app.wallpaper.RotationOrder
import com.wallpaperfit.app.wallpaper.WallpaperConfig
import com.wallpaperfit.app.wallpaper.WallpaperFit
import com.wallpaperfit.app.wallpaper.WallpaperPlace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla "Fondos de pantalla": vista previa con el reloj, fotos del pase, cómo y cuándo
 * cambia, dónde se ve y el estilo del reloj. Todo se guarda al momento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpapersScreen(
    onBack: () -> Unit,
    /** Abrir una foto en el editor; [fromMisaligned]: desde la lista de fotos que no encajan. */
    onEdit: (uri: String, fromMisaligned: Boolean) -> Unit,
    /** Volver del editor a la lista de fotos que no encajan. */
    reopenMisaligned: Boolean = false,
    viewModel: WallpapersViewModel = viewModel(),
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    val colors by viewModel.clockColors.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val active by viewModel.isActive.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Al volver del selector de fondos del sistema, comprobar si ya está activo.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshActive()
            viewModel.syncImages()
        }
    }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.addFolder(uri)
        }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        // El permiso de las fotos elegidas se conserva para poder mostrarlas en el futuro.
        uris.forEach {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // El selector antiguo no da permisos persistentes; la foto se verá mientras dure el permiso.
            }
        }
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var showMisaligned by remember { mutableStateOf(reopenMisaligned) }
    var tagging by remember { mutableStateOf<String?>(null) }
    tagging?.let { TagEditorDialog(Uri.parse(it), onDismiss = { tagging = null }) }
    // De vuelta del editor: la lista sigue ahí y se vuelve a revisar en segundo plano (la foto
    // recién arreglada sale de ella).
    LaunchedEffect(reopenMisaligned) { if (reopenMisaligned) viewModel.checkMisaligned(keepCurrent = true) }
    if (showMisaligned) {
        MisalignedSheet(
            viewModel = viewModel,
            onEdit = { onEdit(it, true) },
            onDismiss = { showMisaligned = false },
        )
    }
    if (showAll) {
        val allImages by produceState(emptyList<String>(), config.sources, images) { value = viewModel.unfilteredImages() }
        AllPhotosSheet(
            images = images,
            allImages = allImages,
            filterTags = config.sources.mapNotNull { it.tag },
            current = state.current,
            onPick = { viewModel.show(it); showAll = false },
            onLongPress = { selected = it; showAll = false },
            onDismiss = { showAll = false },
        )
    }
    // Papelera: Android pide confirmación con su propio diálogo.
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
        selected = null
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteRequests.collect { trash.launch(IntentSenderRequest.Builder(it).build()) }
    }

    Scaffold(
        containerColor = CanvasBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { DismissibleSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PreviewCard(
                    current = state.current,
                    clock = clock,
                    clockColor = ClockWidget.textColor(clock, colors),
                    ratio = viewModel.screenSize.width.toFloat() / viewModel.screenSize.height,
                    busy = busy,
                    hasImages = images.isNotEmpty(),
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                )
            }
            item {
                ActivationCard(active = active, hasImages = images.isNotEmpty()) {
                    context.startActivity(viewModel.liveWallpaperIntent())
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.wp_section_photos, images.size)) }
                    if (config.sources.any { it.isFolder || it.tag != null }) {
                        TextButton(onClick = { viewModel.refreshImages() }, enabled = !refreshing, modifier = Modifier.padding(top = 8.dp)) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wp_refresh))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wp_add_folder), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wp_add_photos), maxLines = 1)
                    }
                }
            }
            item {
                // Etiquetas puestas en el selector de fotos: filtran las fotos del pase (ImageCatalog).
                var menu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wp_add_tag), maxLines = 1)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val tags = remember(menu) { viewModel.allTags() }
                        if (tags.isEmpty()) {
                            Text(
                                stringResource(R.string.wp_add_tag_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        tags.forEach { (tag, count) ->
                            DropdownMenuItem(
                                text = { Text("$tag · $count") },
                                enabled = config.sources.none { it.tag == tag },
                                onClick = {
                                    menu = false
                                    viewModel.addTag(tag)
                                },
                            )
                        }
                    }
                }
            }
            if (config.sources.isNotEmpty()) {
                if (config.sources.any { it.tag != null }) {
                    item { Hint(stringResource(if (images.isEmpty()) R.string.wp_tag_filter_empty else R.string.wp_tag_filter_note)) }
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        config.sources.forEach { source ->
                            // Tocar una fuente: elegir su horario.
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                InputChip(
                                    selected = source.schedule != SourceSchedule.ALWAYS,
                                    onClick = { menu = true },
                                    label = {
                                        val name = source.name.ifEmpty { stringResource(R.string.wp_photo) }
                                        val text = if (source.schedule == SourceSchedule.ALWAYS) {
                                            name
                                        } else {
                                            "$name · ${stringResource(source.schedule.label)}"
                                        }
                                        Text(text, maxLines = 1)
                                    },
                                    leadingIcon = {
                                        val icon = when {
                                            source.isFolder -> Icons.Outlined.Folder
                                            source.tag != null -> Icons.AutoMirrored.Outlined.Label
                                            else -> Icons.Outlined.Image
                                        }
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.wp_remove),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { viewModel.removeSource(source) },
                                        )
                                    },
                                )
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    Text(
                                        stringResource(R.string.wp_schedule_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    SourceSchedule.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.label)) },
                                            leadingIcon = { RadioButton(selected = option == source.schedule, onClick = null) },
                                            onClick = {
                                                menu = false
                                                viewModel.setSourceSchedule(source, option)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    PhotoStrip(
                        images = images,
                        current = state.current,
                        selected = selected,
                        onTap = { viewModel.show(it); selected = null },
                        onLongPress = { selected = it },
                        onShowAll = { showAll = true },
                    )
                }
                item {
                    TextButton(onClick = { viewModel.checkMisaligned(); showMisaligned = true }) {
                        Icon(Icons.Outlined.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wp_misaligned_open))
                    }
                }
                selected?.let { uri ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.show(uri); selected = null }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.wp_show_now), maxLines = 1)
                            }
                            OutlinedButton(onClick = { viewModel.setLockOnly(uri); selected = null }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.wp_lock_only), maxLines = 1)
                            }
                        }
                    }
                }
                // Editar o borrar: la foto elegida (mantener pulsada) o, si no hay, la de fondo.
                (selected ?: state.current)?.let { uri ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Label(stringResource(if (selected != null) R.string.wp_target_selected else R.string.wp_target_current))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { selected = null; onEdit(uri, false) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.wp_edit), maxLines = 1)
                                }
                                OutlinedButton(onClick = { tagging = uri }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.tags_title), maxLines = 1)
                                }
                                // Borrar, solo con el icono: con texto no caben los tres botones.
                                OutlinedIconButton(
                                    onClick = { viewModel.deleteImage(uri) },
                                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.wp_delete), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item { Hint(stringResource(R.string.wp_empty)) }
            }

            item { SectionTitle(stringResource(R.string.wp_section_change)) }
            item { ChangeSettings(config, viewModel) }

            item { SectionTitle(stringResource(R.string.wp_section_where)) }
            item { PlaceSettings(config, viewModel) }

            item { SectionTitle(stringResource(R.string.wp_section_clock)) }
            item { ClockSettings(clock, state.current, ClockWidget.textColor(clock, colors), viewModel) }

            item { Hint(stringResource(R.string.wp_battery_note)) }
        }
    }
}

// region Vista previa

@Composable
private fun PreviewCard(
    current: String?,
    clock: ClockConfig,
    clockColor: Int,
    ratio: Float,
    busy: Boolean,
    hasImages: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledTonalIconButton(onClick = onPrevious, enabled = hasImages && !busy) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.wp_previous))
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .width(176.dp)
                .aspectRatio(ratio)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        ) {
            UriImage(current, Modifier.fillMaxSize(), side = 720)
            if (current == null) {
                Icon(
                    Icons.Outlined.Wallpaper,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                )
            }
            ClockPreview(
                clock = clock,
                color = Color(clockColor),
                scale = 0.42f,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp, start = 10.dp, end = 10.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        FilledTonalIconButton(onClick = onNext, enabled = hasImages && !busy) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.wp_next))
        }
    }
}

/** Reloj como lo pintará el widget, escalado a la vista previa. */
@Composable
fun ClockPreview(clock: ClockConfig, color: Color, scale: Float, modifier: Modifier = Modifier) {
    // La hora avanza mientras la pantalla está abierta (una vez por minuto, en el cambio de minuto).
    val now by produceState(Date()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = Date()
        }
    }
    val time = SimpleDateFormat(clock.timePattern, Locale.getDefault()).format(now)
    val date = SimpleDateFormat(clock.dateFormat.pattern, Locale.getDefault()).format(now)
    val shadowColor = if (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f > 0.5f) Color(0xAA000000) else Color(0xAAFFFFFF)
    val shadow = if (clock.shadow == ClockShadow.SOFT) Shadow(shadowColor, Offset(0f, 2f), 10f) else null
    val align = when (clock.align) {
        ClockAlign.LEFT -> TextAlign.Start
        ClockAlign.CENTER -> TextAlign.Center
        ClockAlign.RIGHT -> TextAlign.End
    }
    val family = clock.font.family()
    val dateText: @Composable () -> Unit = {
        Text(
            text = date,
            color = color,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                // La fuente de vivo solo tiene cifras: la fecha va en la del sistema, como en el widget.
                // Como en el reloj de bloqueo de vivo, la fecha va en la fuente del sistema.
                fontFamily = if (clock.font == ClockFont.VIVO_SERIF) FontFamily.Default else family,
                fontSize = (clock.dateSize * scale).sp,
                shadow = shadow,
            ),
        )
    }
    Column(modifier) {
        if (clock.showDate && clock.datePosition == DatePosition.ABOVE) dateText()
        Text(
            text = time,
            color = color,
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontFamily = family, fontSize = (clock.timeSize * scale).sp, shadow = shadow, lineHeight = (clock.timeSize * scale * 1.1f).sp),
        )
        if (clock.showDate && clock.datePosition == DatePosition.BELOW) dateText()
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun ClockFont.family(): FontFamily {
    val (res, file, weight) = LockClockFont.resolve(LocalContext.current, this)
    val variation = weight?.let { FontVariation.Settings(FontVariation.weight(it)) } ?: FontVariation.Settings()
    file?.let { return FontFamily(Font(it, variationSettings = variation)) }
    res?.let { return FontFamily(Font(it, variationSettings = variation)) }
    return when (this) {
        ClockFont.SANS_LIGHT -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
        ClockFont.SANS_THIN -> FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))
        ClockFont.SANS_MEDIUM -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        ClockFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        ClockFont.SERIF -> FontFamily.Serif
        ClockFont.MONO -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

@Composable
private fun ActivationCard(active: Boolean, hasImages: Boolean, onActivate: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF7BE0AD))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wp_active), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wp_active_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wp_inactive), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (hasImages) R.string.wp_inactive_desc else R.string.wp_inactive_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onActivate, enabled = hasImages) { Text(stringResource(R.string.wp_activate)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoStrip(
    images: List<String>,
    current: String?,
    selected: String?,
    onTap: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    // La tira se coloca en la foto actual cada vez que cambia.
    LaunchedEffect(current, images.size) {
        val index = images.indexOf(current)
        if (index >= 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images, key = { it }) { uri ->
            val border = when (uri) {
                selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else -> null
            }
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (border != null) Modifier.border(border, RoundedCornerShape(12.dp)) else Modifier)
                    .combinedClickable(onClick = { onTap(uri) }, onLongClick = { onLongPress(uri) }),
            ) {
                UriImage(uri, Modifier.fillMaxSize(), side = 240)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Hint(stringResource(R.string.wp_strip_hint)) }
        TextButton(onClick = onShowAll) {
            Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.wp_show_all, images.size))
        }
    }
}

/**
 * Fotos con otra forma que la pantalla: solo se listan; tocar una la abre en el editor para
 * encuadrarla a mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MisalignedSheet(viewModel: WallpapersViewModel, onEdit: (String) -> Unit, onDismiss: () -> Unit) {
    val found by viewModel.misaligned.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val list = found
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.wp_misaligned_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    list == null -> stringResource(R.string.wp_misaligned_checking)
                    list.isEmpty() -> stringResource(R.string.wp_misaligned_none)
                    else -> pluralStringResource(R.plurals.wp_misaligned_found, list.size, list.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(160.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            return@ModalBottomSheet
        }
        val gridState = rememberLazyGridState()
        Box(Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(list, key = { it.uri }) { item ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEdit(item.uri) },
                    ) {
                        UriImage(item.uri, Modifier.fillMaxWidth().aspectRatio(9f / 16f), side = 320)
                        Text(
                            "${item.width} × ${item.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            GridFastScroller(gridState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** Todas las fotos en cuadrícula: tocar una la pone de fondo y cierra. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AllPhotosSheet(
    images: List<String>,
    /** Las de las fuentes sin el filtro de etiquetas de la pantalla de Fondos. */
    allImages: List<String>,
    filterTags: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { PhotoTags.get(context) }
    val tags by store.tags.collectAsStateWithLifecycle()
    // Empieza con el filtro de Fondos puesto. Quitarlo aquí es solo para ver: el pase no cambia.
    val canUnfilter = filterTags.isNotEmpty() && allImages.size > images.size
    var useFilter by remember { mutableStateOf(true) }
    val base = if (useFilter || !canUnfilter) images else allImages
    val inPass = remember(images) { images.toHashSet() }
    val names by produceState(emptyMap<String, String>(), images, allImages) { value = store.namesOf(allImages.ifEmpty { images }) }
    // Filtro por etiquetas (varias a la vez: fotos con todas), solo las que tienen fotos del pase.
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var untagged by remember { mutableStateOf(false) }
    // Buscador: fotos con alguna etiqueta que contenga lo escrito (sin mirar mayúsculas ni tildes).
    var query by remember { mutableStateOf("") }
    val wanted = PhotoTags.fold(query)
    fun tagsOf(uri: String) = names[uri]?.let { tags[it] }.orEmpty()
    val untaggedCount = remember(base, names, tags) { if (names.isEmpty()) 0 else base.count { tagsOf(it).isEmpty() } }
    val shown = remember(base, names, tags, selected, untagged, query) {
        when {
            untagged -> base.filter { tagsOf(it).isEmpty() }
            else -> base.filter { PhotoTags.matches(tagsOf(it), selected, query) }
        }
    }
    // El buscador se ve si alguna foto del pase tiene etiquetas (aunque el filtro no deje ninguna).
    val anyTagged = remember(base, names, tags) { base.any { tagsOf(it).isNotEmpty() } }
    // Etiquetas de las fotos que quedan (para afinar), las marcadas primero.
    val counts = remember(shown, names, tags, selected) {
        PhotoTags.selectedFirst(PhotoTags.counts(shown.map { tagsOf(it) }), selected)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.wp_all_title, shown.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (canUnfilter) {
            FilterChip(
                selected = useFilter,
                onClick = { useFilter = !useFilter },
                label = { Text(stringResource(R.string.wp_all_only_filter, filterTags.joinToString(", "), images.size), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            )
            if (!useFilter) {
                Text(
                    stringResource(R.string.wp_all_outside_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
        if (anyTagged) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    untagged = false
                },
                placeholder = { Text(stringResource(R.string.tags_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tags_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                if (untaggedCount > 0 && wanted.isEmpty() && selected.isEmpty()) {
                    item(key = "#untagged") {
                        FilterChip(
                            selected = untagged,
                            onClick = {
                                untagged = !untagged
                                selected = emptySet()
                            },
                            label = { Text("${stringResource(R.string.tags_untagged)} · $untaggedCount") },
                        )
                    }
                }
                items(counts, key = { it.first }) { (tag, count) ->
                    val on = tag in selected
                    FilterChip(
                        selected = on,
                        onClick = {
                            selected = if (on) selected - tag else selected + tag
                            untagged = false
                        },
                        label = { Text("$tag · $count") },
                    )
                }
            }
        }
        val gridState = key(selected, untagged, wanted, useFilter) { rememberLazyGridState(initialFirstVisibleItemIndex = shown.indexOf(current).coerceAtLeast(0)) }
        // A toda altura: si no, la hoja cambia de tamaño (y la barra de sitio) según cuántas fotos queden.
        Box(Modifier.fillMaxSize()) {
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.tags_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(3),
                state = gridState,
                // A la derecha queda sitio para la barra de desplazamiento rápido.
                contentPadding = PaddingValues(start = 12.dp, end = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(shown, key = { it }) { uri ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (uri == current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                            .combinedClickable(onClick = { onPick(uri) }, onLongClick = { onLongPress(uri) }),
                    ) {
                        UriImage(uri, Modifier.fillMaxSize().alpha(if (uri in inPass) 1f else OUTSIDE_FILTER_ALPHA), side = 320)
                    }
                }
            }
            GridFastScroller(gridState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** Fotos que se ven en "Ver todas" pero no entran en el pase por el filtro. */
private const val OUTSIDE_FILTER_ALPHA = 0.4f

// endregion

// region Ajustes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChangeSettings(config: WallpaperConfig, viewModel: WallpapersViewModel) {
    SettingsCard {
        Label(stringResource(R.string.wp_order))
        val orders = RotationOrder.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            orders.forEachIndexed { i, order ->
                SegmentedButton(
                    selected = order == config.order,
                    onClick = { viewModel.setOrder(order) },
                    shape = SegmentedButtonDefaults.itemShape(i, orders.size),
                    label = { Text(stringResource(if (order == RotationOrder.RANDOM) R.string.wp_order_random else R.string.wp_order_sequential)) },
                )
            }
        }
        Label(stringResource(R.string.wp_auto))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoChange.entries.forEach { value ->
                FilterChip(selected = value == config.autoChange, onClick = { viewModel.setAutoChange(value) }, label = { Text(stringResource(value.label)) })
            }
        }
        if (config.autoChange == AutoChange.CUSTOM) {
            val index = CUSTOM_STEPS.indexOfFirst { it >= config.customMinutes }.let { if (it < 0) CUSTOM_STEPS.lastIndex else it }
            Label(stringResource(R.string.wp_auto_custom_value, formatMinutes(config.customMinutes)))
            Slider(
                value = index.toFloat(),
                onValueChange = { viewModel.setCustomMinutes(CUSTOM_STEPS[it.roundToInt().coerceIn(0, CUSTOM_STEPS.lastIndex)]) },
                valueRange = 0f..CUSTOM_STEPS.lastIndex.toFloat(),
                steps = CUSTOM_STEPS.size - 2,
            )
        }
        if (config.intervalMs != null) Hint(stringResource(R.string.wp_auto_note))
        if (config.autoPaused) {
            // Pausado con un gesto: se ve aquí y se puede reanudar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Hint(stringResource(R.string.wp_auto_paused)) }
                TextButton(onClick = viewModel::toggleAutoPaused) { Text(stringResource(R.string.wp_auto_resume)) }
            }
        }
        Label(stringResource(R.string.wp_gestures))
        TapActionRow(stringResource(R.string.wp_gesture_double), config.doubleTapAction, viewModel::setDoubleTapAction)
        TapActionRow(stringResource(R.string.wp_gesture_triple), config.tripleTapAction, viewModel::setTripleTapAction)
        if (config.doubleTapAction == TapAction.PREVIOUS || config.tripleTapAction == TapAction.PREVIOUS) {
            Label(stringResource(R.string.wp_history, config.historySize))
            Slider(
                value = config.historySize.toFloat(),
                onValueChange = { viewModel.setHistorySize(it.roundToInt()) },
                valueRange = WallpaperConfig.MIN_HISTORY.toFloat()..WallpaperConfig.MAX_HISTORY.toFloat(),
                steps = WallpaperConfig.MAX_HISTORY - WallpaperConfig.MIN_HISTORY - 1,
            )
        }
        Hint(stringResource(R.string.wp_tap_note))
    }
}

/** "Doble toque · Siguiente ▾": tocar abre la lista de acciones. */
@Composable
private fun TapActionRow(title: String, action: TapAction, onChange: (TapAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { menu = true }
                .padding(vertical = 10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(action.label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            TapAction.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { RadioButton(selected = option == action, onClick = null) },
                    onClick = {
                        menu = false
                        onChange(option)
                    },
                )
            }
        }
    }
}

private val TapAction.label: Int
    get() = when (this) {
        TapAction.NEXT -> R.string.wp_tap_next
        TapAction.PREVIOUS -> R.string.wp_tap_previous
        TapAction.RANDOM -> R.string.wp_tap_random
        TapAction.TOGGLE_AUTO -> R.string.wp_tap_toggle_auto
        TapAction.NONE -> R.string.wp_tap_none
    }

private val AutoChange.label: Int
    get() = when (this) {
        AutoChange.NEVER -> R.string.wp_auto_never
        AutoChange.EVERY_UNLOCK -> R.string.wp_auto_unlock
        AutoChange.MINUTES_15 -> R.string.wp_auto_15m
        AutoChange.HOUR_1 -> R.string.wp_auto_1h
        AutoChange.HOURS_6 -> R.string.wp_auto_6h
        AutoChange.DAY_1 -> R.string.wp_auto_1d
        AutoChange.CUSTOM -> R.string.wp_auto_custom
    }

/** Intervalos que ofrece el deslizador "Personalizado", en minutos. */
private val CUSTOM_STEPS = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720)

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.wp_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.wp_hours, minutes / 60)
    else -> stringResource(R.string.wp_hours_minutes, minutes / 60, minutes % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSettings(config: WallpaperConfig, viewModel: WallpapersViewModel) {
    SettingsCard {
        val places = WallpaperPlace.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            places.forEachIndexed { i, place ->
                SegmentedButton(
                    selected = place == config.place,
                    onClick = { viewModel.setPlace(place) },
                    shape = SegmentedButtonDefaults.itemShape(i, places.size),
                    label = { Text(stringResource(if (place == WallpaperPlace.HOME) R.string.wp_place_home else R.string.wp_place_both), maxLines = 1) },
                )
            }
        }
        Hint(stringResource(R.string.wp_place_note))
        Label(stringResource(R.string.wp_fit))
        val fits = WallpaperFit.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            fits.forEachIndexed { i, fit ->
                SegmentedButton(
                    selected = fit == config.fit,
                    onClick = { viewModel.setFit(fit) },
                    shape = SegmentedButtonDefaults.itemShape(i, fits.size),
                    label = { Text(stringResource(if (fit == WallpaperFit.FILL) R.string.wp_fit_fill else R.string.wp_fit_blur), maxLines = 1) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClockSettings(clock: ClockConfig, current: String?, color: Int, viewModel: WallpapersViewModel) {
    SettingsCard {
        // Vista previa del reloj sobre el fondo actual, para ver cada cambio sin subir.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            UriImage(current, Modifier.fillMaxSize(), side = 720)
            ClockPreview(
                clock = clock,
                color = Color(color),
                scale = 0.8f,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
            )
        }
        FilledTonalButton(onClick = viewModel::requestClockWidget, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.wp_add_widget))
        }
        OutlinedButton(onClick = viewModel::matchLockClock, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wp_match_lock))
        }
        Hint(stringResource(R.string.wp_widget_resize))
        Label(stringResource(R.string.wp_clock_font))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ClockFont.entries) { font ->
                val selected = font == clock.font
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { viewModel.setClockFont(font) },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("12:45", style = TextStyle(fontFamily = font.family(), fontSize = 24.sp))
                        Text(stringResource(font.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Label(stringResource(R.string.wp_clock_size, clock.timeSize.roundToInt()))
        Slider(value = clock.timeSize, onValueChange = viewModel::setClockTimeSize, valueRange = ClockConfig.MIN_TIME_SIZE..ClockConfig.MAX_TIME_SIZE)
        SwitchRow(stringResource(R.string.wp_clock_date), null, clock.showDate, viewModel::setClockShowDate)
        if (clock.showDate) {
            Label(stringResource(R.string.wp_clock_date_size, clock.dateSize.roundToInt()))
            Slider(value = clock.dateSize, onValueChange = viewModel::setClockDateSize, valueRange = ClockConfig.MIN_DATE_SIZE..ClockConfig.MAX_DATE_SIZE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = clock.datePosition == DatePosition.ABOVE, onClick = { viewModel.setClockDatePosition(DatePosition.ABOVE) }, label = { Text(stringResource(R.string.wp_date_above)) })
                FilterChip(selected = clock.datePosition == DatePosition.BELOW, onClick = { viewModel.setClockDatePosition(DatePosition.BELOW) }, label = { Text(stringResource(R.string.wp_date_below)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = clock.dateFormat == DateFormat.SHORT, onClick = { viewModel.setClockDateFormat(DateFormat.SHORT) }, label = { Text(stringResource(R.string.wp_date_short)) })
                FilterChip(selected = clock.dateFormat == DateFormat.LONG, onClick = { viewModel.setClockDateFormat(DateFormat.LONG) }, label = { Text(stringResource(R.string.wp_date_long)) })
            }
        }
        SwitchRow(stringResource(R.string.wp_clock_24h), null, clock.use24h, viewModel::setClock24h)
        SwitchRow(stringResource(R.string.wp_clock_shadow), null, clock.shadow == ClockShadow.SOFT) {
            viewModel.setClockShadow(if (it) ClockShadow.SOFT else ClockShadow.NONE)
        }
        Label(stringResource(R.string.wp_clock_align))
        val aligns = ClockAlign.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            aligns.forEachIndexed { i, align ->
                SegmentedButton(
                    selected = align == clock.align,
                    onClick = { viewModel.setClockAlign(align) },
                    shape = SegmentedButtonDefaults.itemShape(i, aligns.size),
                    label = {
                        Text(
                            stringResource(
                                when (align) {
                                    ClockAlign.LEFT -> R.string.wp_align_left
                                    ClockAlign.CENTER -> R.string.wp_align_center
                                    ClockAlign.RIGHT -> R.string.wp_align_right
                                },
                            ),
                        )
                    },
                )
            }
        }
        Label(stringResource(R.string.wp_clock_color))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = clock.colorMode == ClockColorMode.VIVO, onClick = { viewModel.setClockColorMode(ClockColorMode.VIVO) }, label = { Text(stringResource(R.string.wp_color_lock)) })
            FilterChip(selected = clock.colorMode == ClockColorMode.AUTO, onClick = { viewModel.setClockColorMode(ClockColorMode.AUTO) }, label = { Text(stringResource(R.string.wp_color_auto)) })
            FilterChip(selected = clock.colorMode == ClockColorMode.ACCENT, onClick = { viewModel.setClockColorMode(ClockColorMode.ACCENT) }, label = { Text(stringResource(R.string.wp_color_accent)) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClockConfig.PALETTE.forEach { value ->
                val selected = clock.colorMode == ClockColorMode.FIXED && clock.fixedColor == value
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { viewModel.setClockFixedColor(value) },
                )
            }
        }
        Hint(stringResource(R.string.wp_clock_note))
    }
}

// endregion

// region Piezas

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// endregion

private val SourceSchedule.label: Int
    get() = when (this) {
        SourceSchedule.ALWAYS -> R.string.wp_schedule_always
        SourceSchedule.DAY -> R.string.wp_schedule_day
        SourceSchedule.NIGHT -> R.string.wp_schedule_night
        SourceSchedule.WEEKDAYS -> R.string.wp_schedule_weekdays
        SourceSchedule.WEEKEND -> R.string.wp_schedule_weekend
    }
