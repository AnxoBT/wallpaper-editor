package com.wallpaperfit.app.ui.wallpapers

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperfit.app.R
import com.wallpaperfit.app.desktop.FileDialogs
import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.ui.PhotoGridCells
import com.wallpaperfit.app.ui.components.DismissibleSnackbarHost
import com.wallpaperfit.app.ui.components.GridFastScroller
import com.wallpaperfit.app.ui.theme.CanvasBackground
import com.wallpaperfit.app.wallpaper.AutoChange
import com.wallpaperfit.app.wallpaper.ImageCatalog
import com.wallpaperfit.app.wallpaper.RotationOrder
import com.wallpaperfit.app.wallpaper.SourceSchedule
import com.wallpaperfit.app.wallpaper.WallpaperController
import com.wallpaperfit.app.wallpaper.WallpaperFit
import com.wallpaperfit.app.wallpaper.WallpaperSource
import com.wallpaperfit.app.wallpaper.WallpaperStore
import kotlinx.coroutines.launch

/**
 * Pase de fondos en Windows: de dónde salen las fotos, cada cuánto cambian y cómo se ajustan. Es
 * más corto que el del móvil porque aquí no hay gestos en el escritorio ni widget del reloj.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpapersScreen(onBack: () -> Unit, onEdit: (String, Boolean) -> Unit, reopenMisaligned: Boolean) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val tags = remember { PhotoTags.get(context) }
    val config by store.config.collectAsStateWithLifecycle()
    val state by store.state.collectAsStateWithLifecycle()
    val active by WallpaperController.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAll by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }

    val openedWith = remember { config.sources }
    val images by produceState(emptyList<String>(), config.sources) {
        value = ImageCatalog.images(context, config.sources)
        // Al abrir la pantalla no ha cambiado nada: se respeta la foto puesta a mano.
        if (active && config.sources != openedWith) WallpaperController.followFilter(context)
    }
    val allImages by produceState(emptyList<String>(), config.sources, images) { value = ImageCatalog.unfiltered(context, config.sources) }

    val errorText = stringResource(R.string.wp_error)
    val noPreviousText = stringResource(R.string.wp_no_previous)

    fun change(failure: String = errorText, action: suspend () -> Boolean) {
        if (changing) return
        changing = true
        scope.launch {
            val done = action()
            changing = false
            if (!done) snackbar.showSnackbar(failure)
        }
    }

    fun addSources(new: List<WallpaperSource>) {
        store.updateConfig { current -> current.copy(sources = (current.sources + new).distinctBy { it.uri }) }
    }

    if (showAll) {
        AllPhotosDialog(
            images = images,
            allImages = allImages,
            filterTags = config.sources.mapNotNull { it.tag },
            onPick = { uri ->
                showAll = false
                change { WallpaperController.show(context, uri) }
            },
            onDismiss = { showAll = false },
        )
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UriImage(
                                state.current,
                                Modifier.size(width = 142.dp, height = 80.dp).clip(RoundedCornerShape(12.dp)),
                                side = 320,
                            )
                            Spacer(Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (active) R.string.wp_active else R.string.wp_inactive),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(
                                        when {
                                            active -> R.string.desktop_active_desc
                                            config.sources.isEmpty() -> R.string.wp_inactive_empty
                                            else -> R.string.desktop_inactive_desc
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { change(noPreviousText) { WallpaperController.previous(context) } }, enabled = !changing && images.isNotEmpty()) {
                                Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.wp_previous))
                            }
                            IconButton(onClick = { change { WallpaperController.next(context) } }, enabled = !changing && images.isNotEmpty()) {
                                Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.wp_next))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = active || config.sources.isNotEmpty(),
                                onClick = {
                                    val start = !active
                                    WallpaperController.setActive(context, start)
                                    if (start && state.current == null) change { WallpaperController.next(context) }
                                },
                            ) { Text(stringResource(if (active) R.string.desktop_stop else R.string.wp_activate)) }
                            state.current?.let { current ->
                                OutlinedButton(onClick = { onEdit(current, false) }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.wp_edit))
                                }
                            }
                        }
                    }
                }

                item { Title(stringResource(R.string.wp_section_photos, images.size)) }
                item {
                    Card {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    FileDialogs.chooseFolder()?.let { folder ->
                                        addSources(listOf(WallpaperSource(Uri.fromFile(folder).toString(), true, folder.name.ifEmpty { folder.path })))
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.wp_add_folder))
                            }
                            OutlinedButton(
                                onClick = {
                                    addSources(FileDialogs.openImages().map { WallpaperSource(Uri.fromFile(it).toString(), false, it.name) })
                                },
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.wp_add_photos))
                            }
                            var tagMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { tagMenu = true }) {
                                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.wp_add_tag))
                                }
                                DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                                    val all = tags.all().filter { it.second > 0 }
                                    if (all.isEmpty()) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.wp_add_tag_empty)) }, onClick = { tagMenu = false }, enabled = false)
                                    }
                                    all.forEach { (tag, count) ->
                                        DropdownMenuItem(
                                            text = { Text("$tag ($count)") },
                                            onClick = {
                                                tagMenu = false
                                                addSources(listOf(WallpaperSource.forTag(tag)))
                                            },
                                        )
                                    }
                                }
                            }
                            if (images.isNotEmpty()) {
                                TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.wp_show_all, images.size)) }
                            }
                        }
                        if (config.sources.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.wp_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (config.sources.any { it.tag != null }) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(if (images.isEmpty()) R.string.wp_tag_filter_empty else R.string.wp_tag_filter_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        config.sources.forEach { source -> SourceRow(source, store) }
                    }
                }

                item { Title(stringResource(R.string.wp_section_change)) }
                item {
                    Card {
                        Label(stringResource(R.string.wp_order))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Option(stringResource(R.string.wp_order_random), config.order == RotationOrder.RANDOM) { store.updateConfig { it.copy(order = RotationOrder.RANDOM) } }
                            Option(stringResource(R.string.wp_order_sequential), config.order == RotationOrder.SEQUENTIAL) { store.updateConfig { it.copy(order = RotationOrder.SEQUENTIAL) } }
                        }
                        Label(stringResource(R.string.wp_auto))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoChange.entries.filter { it != AutoChange.EVERY_UNLOCK }.forEach { option ->
                                Option(stringResource(option.label), config.autoChange == option) { store.updateConfig { it.copy(autoChange = option) } }
                            }
                        }
                        if (config.autoChange == AutoChange.CUSTOM) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = config.customMinutes.toString(),
                                    onValueChange = { text ->
                                        text.toIntOrNull()?.let { value ->
                                            store.updateConfig { it.copy(customMinutes = value.coerceIn(1, 720)) }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.widthIn(max = 110.dp),
                                )
                                Text(stringResource(R.string.desktop_minutes))
                            }
                        }
                        Label(stringResource(R.string.wp_fit))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Option(stringResource(R.string.wp_fit_fill), config.fit == WallpaperFit.FILL) { store.updateConfig { it.copy(fit = WallpaperFit.FILL) } }
                            Option(stringResource(R.string.wp_fit_blur), config.fit == WallpaperFit.FIT_BLUR) { store.updateConfig { it.copy(fit = WallpaperFit.FIT_BLUR) } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.desktop_keep_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
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

private val SourceSchedule.label: Int
    get() = when (this) {
        SourceSchedule.ALWAYS -> R.string.wp_schedule_always
        SourceSchedule.DAY -> R.string.wp_schedule_day
        SourceSchedule.NIGHT -> R.string.wp_schedule_night
        SourceSchedule.WEEKDAYS -> R.string.wp_schedule_weekdays
        SourceSchedule.WEEKEND -> R.string.wp_schedule_weekend
    }

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun Option(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SourceRow(source: WallpaperSource, store: WallpaperStore) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(
            when {
                source.isFolder -> Icons.Outlined.Folder
                source.tag != null -> Icons.AutoMirrored.Outlined.Label
                else -> Icons.Outlined.Image
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(source.name, modifier = Modifier.weight(1f), maxLines = 1)
        Box {
            TextButton(onClick = { menu = true }) { Text(stringResource(source.schedule.label)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SourceSchedule.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label)) },
                        onClick = {
                            menu = false
                            store.updateConfig { config ->
                                config.copy(sources = config.sources.map { if (it.uri == source.uri) it.copy(schedule = option) else it })
                            }
                        },
                    )
                }
            }
        }
        IconButton(onClick = { store.updateConfig { config -> config.copy(sources = config.sources.filter { it.uri != source.uri }) } }) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.wp_remove))
        }
    }
}

/** Todas las fotos del pase, con buscador y filtro de etiquetas; elegir una la pone de fondo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllPhotosDialog(images: List<String>, allImages: List<String>, filterTags: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tags = remember { PhotoTags.get(context) }
    val allTags by tags.tags.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    // Empieza con el filtro de Fondos puesto. Quitarlo aquí es solo para ver: el pase no cambia.
    val canUnfilter = filterTags.isNotEmpty() && allImages.size > images.size
    var useFilter by remember { mutableStateOf(true) }
    val base = if (useFilter || !canUnfilter) images else allImages
    val inPass = remember(images) { images.toHashSet() }
    val names by produceState(emptyMap<String, String>(), images, allImages) { value = tags.namesOf(allImages.ifEmpty { images }) }
    fun tagsOf(uri: String) = names[uri]?.let { allTags[it] }.orEmpty()
    val shown = remember(base, names, allTags, query, selected) { base.filter { PhotoTags.matches(tagsOf(it), selected, query) } }
    val offered = remember(shown, selected) { PhotoTags.selectedFirst(PhotoTags.counts(shown.map { tagsOf(it) }), selected).take(MAX_CHIPS) }
    val grid = rememberLazyGridState()
    LaunchedEffect(query, selected, useFilter) { grid.scrollToItem(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 720.dp, max = 980.dp),
        title = { Text(stringResource(R.string.wp_all_title, shown.size)) },
        text = {
            Column(Modifier.height(560.dp)) {
                if (canUnfilter) {
                    FilterChip(
                        selected = useFilter,
                        onClick = { useFilter = !useFilter },
                        label = { Text(stringResource(R.string.wp_all_only_filter, filterTags.joinToString(", "), images.size), maxLines = 1) },
                    )
                    if (!useFilter) {
                        Text(stringResource(R.string.wp_all_outside_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tags_search)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tags_clear)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (offered.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        offered.forEach { (tag, count) ->
                            InputChip(
                                selected = tag in selected,
                                onClick = { selected = if (tag in selected) selected - tag else selected + tag },
                                label = { Text("$tag ($count)") },
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(columns = PhotoGridCells, state = grid, modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it }) { uri ->
                            UriImage(
                                uri,
                                Modifier.padding(2.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(6.dp)).clickable { onPick(uri) }
                                    .alpha(if (uri in inPass) 1f else OUTSIDE_FILTER_ALPHA),
                            )
                        }
                    }
                    GridFastScroller(grid, Modifier.align(Alignment.TopEnd))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) } },
    )
}

private const val MAX_CHIPS = 14

/** Fotos que se ven en "Ver todas" pero no entran en el pase por el filtro. */
private const val OUTSIDE_FILTER_ALPHA = 0.4f
