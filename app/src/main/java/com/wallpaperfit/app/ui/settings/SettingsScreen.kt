package com.wallpaperfit.app.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.content.Context
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import com.wallpaperfit.app.wallpaper.WallpaperSource
import com.wallpaperfit.app.wallpaper.WallpaperStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.ui.components.DismissibleSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.theme.CanvasBackground

/**
 * Ajustes generales de la app: idioma, etiquetas de las fotos (importar / exportar) e información
 * (versión, código fuente, licencias).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val language = remember { AppLanguage.current(context) }
    var showLicenses by remember { mutableStateOf(false) }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })

    val tags = remember { PhotoTags.get(context) }
    val tagged by tags.tags.collectAsStateWithLifecycle()
    val createdTags by tags.created.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val importTags = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use(tags::import)
                } catch (e: Exception) {
                    null
                }
            }
            snackbar.showSnackbar(
                if (count != null) context.getString(R.string.settings_tags_imported, count) else context.getString(R.string.settings_tags_error),
            )
        }
    }
    var managing by remember { mutableStateOf(false) }
    if (managing) ManageTagsDialog(tags, onDismiss = { managing = false })
    val exportTags = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use(tags::export) != null
                } catch (e: Exception) {
                    false
                }
            }
            snackbar.showSnackbar(
                if (saved) context.getString(R.string.settings_tags_exported, tags.tags.value.size) else context.getString(R.string.settings_tags_error),
            )
        }
    }

    Scaffold(
        containerColor = CanvasBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { DismissibleSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_language)) }
            item {
                SettingsCard {
                    Column(Modifier.selectableGroup()) {
                        AppLanguage.Option.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .selectable(
                                        selected = option == language,
                                        role = Role.RadioButton,
                                        onClick = { if (option != language && activity != null) AppLanguage.set(activity, option) },
                                    )
                                    .padding(horizontal = 8.dp),
                            ) {
                                RadioButton(selected = option == language, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item { SectionTitle(stringResource(R.string.settings_tags)) }
            item {
                SettingsCard {
                    LinkRow(
                        Icons.AutoMirrored.Outlined.Label,
                        stringResource(R.string.settings_tags_manage),
                        stringResource(R.string.settings_tags_manage_desc, remember(tagged, createdTags) { tags.all().size }),
                    ) { managing = true }
                    LinkRow(Icons.Outlined.FileDownload, stringResource(R.string.settings_tags_import), stringResource(R.string.settings_tags_import_desc)) {
                        importTags.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                    LinkRow(Icons.Outlined.FileUpload, stringResource(R.string.settings_tags_export), stringResource(R.string.settings_tags_export_desc, tagged.size)) {
                        exportTags.launch("etiquetas.json")
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_about)) }
            item {
                SettingsCard {
                    LinkRow(Icons.Outlined.Info, stringResource(R.string.settings_version), version, onClick = null)
                    LinkRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.settings_source_code), SOURCE_URL.removePrefix("https://")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    }
                    LinkRow(Icons.Outlined.Description, stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_desc)) {
                        showLicenses = true
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Todas las etiquetas con cuántas fotos tiene cada una; tocar una permite renombrarla o borrarla.
 * El campo de arriba busca y, si lo escrito no existe, ofrece crearla.
 */
@Composable
private fun ManageTagsDialog(tags: PhotoTags, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all by tags.tags.collectAsStateWithLifecycle()
    // Buscador: solo las etiquetas que contienen lo escrito (sin mirar mayúsculas ni tildes).
    var query by remember { mutableStateOf("") }
    val created by tags.created.collectAsStateWithLifecycle()
    val wanted = PhotoTags.fold(query)
    val list = remember(all, created, wanted) { tags.all().filter { PhotoTags.fold(it.first).contains(wanted) } }
    val canCreate = wanted.isNotEmpty() && list.none { PhotoTags.fold(it.first) == wanted }
    var editing by remember { mutableStateOf<String?>(null) }
    editing?.let { tag ->
        var name by remember(tag) { mutableStateOf(tag) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.tags_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tags_rename_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTag(context, tags, tag, name)
                        editing = null
                    },
                    enabled = PhotoTags.clean(name).isNotEmpty(),
                ) { Text(stringResource(R.string.tags_rename_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameTag(context, tags, tag, null)
                        editing = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.tags_delete)) }
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.settings_tags_manage)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.tags_manage_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tags_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canCreate) tags.create(query) }),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                if (canCreate) {
                    // Al crearla se queda en la lista, con 0 fotos, y esta fila desaparece.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tags.create(query) }
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.tags_create, PhotoTags.clean(query)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                LazyColumn {
                    items(list, key = { it.first }) { (tag, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = tag }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(tag, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

/**
 * Renombra [old] (o la borra si [new] es null) en las fotos y en las fuentes del fondo dinámico
 * que la usan, para que el pase siga igual.
 */
private fun renameTag(context: Context, tags: PhotoTags, old: String, new: String?) {
    val renamed = new?.let(PhotoTags::clean)
    if (renamed == null) tags.delete(old) else tags.rename(old, renamed)
    WallpaperStore.get(context).updateConfig { config ->
        val sources = config.sources.mapNotNull { source ->
            when {
                source.tag != old -> source
                renamed == null -> null
                else -> WallpaperSource.forTag(renamed).copy(schedule = source.schedule)
            }
        }.distinctBy { it.uri }
        config.copy(sources = sources)
    }
}

/** Licencias de las fuentes y del resto del código abierto que lleva la app. */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        listOf(R.raw.open_source_notices, R.raw.font_licenses).joinToString("\n\n") { id ->
            context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

private const val SOURCE_URL = "https://github.com/AnxoBT/wallpaper-editor"
