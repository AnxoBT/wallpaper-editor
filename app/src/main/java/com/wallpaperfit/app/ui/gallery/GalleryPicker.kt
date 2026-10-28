package com.wallpaperfit.app.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.ui.components.TagEditorDialog
import androidx.compose.material.icons.outlined.History
import com.wallpaperfit.app.storage.RecentEdits
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ContentUris
import androidx.compose.foundation.border
import com.wallpaperfit.app.ui.components.GridFastScroller
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.AlbumGridCells
import com.wallpaperfit.app.ui.GalleryLibraryAction
import com.wallpaperfit.app.ui.PhotoGridCells
import com.wallpaperfit.app.ui.theme.CanvasBackground
import com.wallpaperfit.app.ui.wallpapers.UriImage

/**
 * Selector de fotos propio: se abre en la última carpeta usada y a la altura de la última foto
 * elegida (la primera vez, en todas las fotos, la más reciente primero).
 * Con [focus] (la foto que se está editando) se abre en su carpeta, con esa foto a la vista.
 * Si no hay permiso de fotos, ofrece el selector del sistema. La lupa busca por etiquetas y
 * mantener pulsada una foto permite cambiar las suyas.
 * [rememberPosition]: el selector de capas no cambia la posición guardada del de editar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPicker(
    title: String,
    onPick: (Uri) -> Unit,
    onCancel: () -> Unit,
    onSystemPicker: () -> Unit,
    rememberPosition: Boolean = true,
    focus: Uri? = null,
) {
    val context = LocalContext.current
    val repository = remember { GalleryRepository(context) }
    var granted by remember { mutableStateOf(hasPhotoPermission(context)) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPhotoPermission(context)
    }
    val saved = remember { repository.lastPosition() }
    // null = lista de carpetas; un valor = dentro de esa carpeta ("" = todas las fotos).
    var album by remember { mutableStateOf<String?>(saved.albumId ?: ALL) }
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    // Últimas fotos editadas: una carpeta más, la primera de la lista.
    val recentEdits = remember { RecentEdits.get(context) }
    val recent by recentEdits.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { recentEdits.prune() }
    // Búsqueda por etiquetas y foto cuyas etiquetas se están cambiando.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tagging by remember { mutableStateOf<Uri?>(null) }
    tagging?.let { TagEditorDialog(it, onDismiss = { tagging = null }) }

    // Foto que se está editando: su carpeta y su número, para empezar justo ahí.
    var focusId by remember { mutableStateOf<Long?>(null) }
    var libraryVersion by remember { mutableStateOf(0) }
    LaunchedEffect(granted, libraryVersion) {
        if (!granted) return@LaunchedEffect
        focus?.let { repository.locate(it) }?.let { (bucket, id) ->
            album = bucket
            focusId = id
        }
        albums = repository.albums()
    }
    BackHandler {
        when {
            searching -> searching = false
            album != null && albums != null -> album = null
            else -> onCancel()
        }
    }

    val currentAlbum = album
    val albumName = when {
        currentAlbum == null -> title
        currentAlbum == ALL -> stringResource(R.string.picker_all)
        currentAlbum == RECENT -> stringResource(R.string.recent_title)
        else -> albums?.firstOrNull { it.id == currentAlbum }?.name ?: title
    }

    Scaffold(
        containerColor = CanvasBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) SearchField(query, onChange = { query = it }) else Text(albumName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                searching -> searching = false
                                currentAlbum != null && albums != null -> album = null
                                else -> onCancel()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (searching) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tags_clear))
                            }
                        }
                        return@TopAppBar
                    }
                    if (granted) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.tags_search))
                        }
                    }
                    // Últimas fotos editadas (solo si hay alguna).
                    if (recent.isNotEmpty() && granted) {
                        IconButton(onClick = { album = RECENT }) {
                            Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.recent_title))
                        }
                    }
                    GalleryLibraryAction(onChanged = { libraryVersion++ })
                    IconButton(onClick = onSystemPicker) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.picker_system))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBackground),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !granted -> PermissionRequest(
                    onGrant = { requestPermission.launch(photoPermissions()) },
                    onSystemPicker = onSystemPicker,
                )
                searching -> TagSearch(query, onQuery = { query = it }, onPick = onPick, onLongPress = { tagging = it })
                albums == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                currentAlbum == null -> AlbumGrid(albums.orEmpty()) { album = it }
                currentAlbum == RECENT -> RecentGrid(recent) { onPick(Uri.parse(it)) }
                else -> key(currentAlbum) {
                    // La posición guardada solo vale para la carpeta donde se guardó.
                    val start = if (currentAlbum == (saved.albumId ?: ALL)) saved else PickerPosition(currentAlbum, 0, 0)
                    PhotoGrid(
                        repository = repository,
                        albumId = currentAlbum.takeIf { it != ALL },
                        start = start,
                        focusId = focusId,
                        onPick = { uri, position ->
                            if (rememberPosition) repository.savePosition(position.copy(albumId = currentAlbum))
                            onPick(uri)
                        },
                        onLongPress = { tagging = it },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    repository: GalleryRepository,
    albumId: String?,
    start: PickerPosition,
    focusId: Long?,
    onPick: (Uri, PickerPosition) -> Unit,
    onLongPress: (Uri) -> Unit,
) {
    var photos by remember { mutableStateOf<List<Uri>?>(null) }
    LaunchedEffect(albumId) { photos = repository.photos(albumId) }
    val list = photos
    if (list == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    // La foto que se está editando queda en la segunda fila, con las de alrededor a la vista.
    val focusIndex = focusId?.let { id -> list.indexOfFirst { ContentUris.parseId(it) == id } }?.takeIf { it >= 0 }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (focusIndex != null) {
            (focusIndex - focusIndex % COLUMNS - COLUMNS).coerceAtLeast(0)
        } else {
            start.index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        },
        initialFirstVisibleItemScrollOffset = if (focusIndex != null) 0 else start.offset,
    )
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = PhotoGridCells,
            state = state,
            // A la derecha queda sitio para la barra de desplazamiento rápido.
            contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 24.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.toString() }) { uri ->
                val focused = focusIndex != null && ContentUris.parseId(uri) == focusId
                UriImage(
                    uri.toString(),
                    Modifier
                        .aspectRatio(1f)
                        .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
                        .combinedClickable(
                            onClick = { onPick(uri, PickerPosition(albumId, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) },
                            onLongClick = { onLongPress(uri) },
                        ),
                    side = 256,
                )
            }
        }
        GridFastScroller(state, Modifier.align(Alignment.CenterEnd))
    }
}

/** Campo de búsqueda de la barra superior (se abre con el teclado ya fuera). */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.tags_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

/**
 * Búsqueda por etiquetas en todas las fotos: arriba las etiquetas (se pueden marcar varias y
 * salen las fotos que tienen todas) y debajo las fotos que además tienen alguna etiqueta que
 * contiene lo escrito. Las etiquetas que se ofrecen son las de las fotos que quedan, para afinar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagSearch(query: String, onQuery: (String) -> Unit, onPick: (Uri) -> Unit, onLongPress: (Uri) -> Unit) {
    val context = LocalContext.current
    val store = remember { PhotoTags.get(context) }
    val tags by store.tags.collectAsStateWithLifecycle()
    val all = remember(tags) { store.all().filter { it.second > 0 } }
    // "Sin etiquetas": las fotos nuevas de las carpetas ya etiquetadas, para ponérselas.
    var untagged by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(query) { if (query.isNotBlank()) untagged = false }
    val filtering = query.isNotBlank() || selected.isNotEmpty()
    // Fotos (por nombre) que pasan el filtro y las etiquetas que tienen, para seguir afinando.
    val matching = remember(tags, selected, query) {
        if (filtering) tags.filterValues { PhotoTags.matches(it, selected, query) } else emptyMap()
    }
    val chips = remember(all, matching, selected, filtering) {
        if (!filtering) {
            all
        } else {
            PhotoTags.selectedFirst(PhotoTags.counts(matching.values), selected)
        }
    }
    var photos by remember { mutableStateOf<List<Uri>?>(emptyList()) }
    LaunchedEffect(matching, untagged) {
        photos = when {
            untagged -> store.untagged()
            !filtering -> emptyList()
            else -> store.photosNamed(matching.keys)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (all.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "#untagged") {
                    FilterChip(
                        selected = untagged,
                        onClick = {
                            onQuery("")
                            selected = emptySet()
                            untagged = !untagged
                        },
                        label = { Text(stringResource(R.string.tags_untagged)) },
                    )
                }
                items(chips, key = { it.first }) { (tag, count) ->
                    val on = tag in selected
                    FilterChip(
                        selected = on,
                        onClick = {
                            untagged = false
                            selected = if (on) selected - tag else selected + tag
                        },
                        label = { Text("$tag · $count") },
                    )
                }
            }
        }
        val list = photos
        val message = when {
            all.isEmpty() -> stringResource(R.string.tags_empty)
            !filtering && !untagged -> stringResource(R.string.tags_pick)
            list != null && list.isEmpty() -> stringResource(R.string.tags_no_results)
            else -> null
        }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
            return
        }
        if (list == null) return
        Text(
            pluralStringResource(R.plurals.tags_results, list.size, list.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val gridState = key(selected, query, untagged) { rememberLazyGridState() }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = PhotoGridCells,
                state = gridState,
                // A la derecha queda sitio para la barra de desplazamiento rápido.
                contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 24.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.toString() }) { uri ->
                    UriImage(
                        uri.toString(),
                        Modifier
                            .aspectRatio(1f)
                            .combinedClickable(onClick = { onPick(uri) }, onLongClick = { onLongPress(uri) }),
                        side = 256,
                    )
                }
            }
            GridFastScroller(gridState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = AlbumGridCells,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            AlbumCell(stringResource(R.string.picker_all), albums.sumOf { it.count }, albums.firstOrNull()?.cover) { onOpen(ALL) }
        }
        items(albums, key = { it.id }) { album ->
            AlbumCell(album.name, album.count, album.cover) { onOpen(album.id) }
        }
    }
}

/** Las últimas fotos editadas, la más reciente primero. */
@Composable
private fun RecentGrid(items: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = PhotoGridCells,
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it }) { uri ->
            UriImage(uri, Modifier.aspectRatio(1f).clickable { onPick(uri) }, side = 256)
        }
    }
}

@Composable
private fun AlbumCell(name: String, count: Int, cover: Uri?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            if (cover != null) {
                UriImage(cover.toString(), Modifier.fillMaxSize(), side = 320)
            } else {
                Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionRequest(onGrant: () -> Unit, onSystemPicker: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.picker_permission), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.picker_grant)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSystemPicker, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.picker_system)) }
    }
}

/** Identificador interno de "todas las fotos". */
private const val ALL = ""

/** Identificador interno de "editadas hace poco" (los de las carpetas reales son números). */
private const val RECENT = "#recent"

private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Acceso completo a las fotos (con acceso parcial de Android 14 solo se ven las elegidas). */
fun hasPhotoPermission(context: android.content.Context): Boolean {
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            has(Manifest.permission.READ_MEDIA_IMAGES) || has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> has(Manifest.permission.READ_MEDIA_IMAGES)
        else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/** Columnas de la cuadrícula de fotos. */
private const val COLUMNS = 4
