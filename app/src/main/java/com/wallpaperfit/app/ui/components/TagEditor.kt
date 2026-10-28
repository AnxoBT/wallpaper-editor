package com.wallpaperfit.app.ui.components

import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.rememberCoroutineScope
import com.wallpaperfit.app.storage.OpenInGallery
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperfit.app.R
import com.wallpaperfit.app.storage.PhotoTags

/**
 * Etiquetas de una foto: se quitan con la ✕, se añaden escribiéndolas o tocando una de las que ya
 * se usan. Cada cambio se guarda al momento. Arriba, abrir la foto en la galería y marcarla como
 * favorita.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PhotoTags.get(context) }
    val all by store.tags.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) { name = store.nameOf(uri) }
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val current = name?.let { all[it] }.orEmpty()
    fun add(tag: String) {
        val clean = PhotoTags.clean(tag)
        if (clean.isNotEmpty()) name?.let { store.set(it, current + clean) }
        text = ""
    }
    // Sugerencias: las etiquetas ya usadas que no tiene, las que coinciden con lo escrito primero.
    val created by store.created.collectAsStateWithLifecycle()
    val suggestions = remember(all, created, current, text) {
        val wanted = PhotoTags.fold(text)
        // Las creadas sin fotos primero: si no, quedarían fuera de las que caben.
        store.all().sortedBy { it.second > 0 }.map { it.first }
            .filter { it !in current && (wanted.isEmpty() || PhotoTags.fold(it).contains(wanted)) }
            .take(MAX_SUGGESTIONS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tags_title), modifier = Modifier.weight(1f))
                // Abrir la foto en la galería (por ejemplo, para marcarla allí como favorita).
                IconButton(onClick = { scope.launch { OpenInGallery.open(context, uri) } }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.open_in_gallery),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Favorita: una etiqueta más, a mano con el corazón.
                val favorite = store.favoriteTag in current
                IconButton(onClick = { name?.let { store.set(it, if (favorite) current - store.favoriteTag else current + store.favoriteTag) } }, enabled = name != null) {
                    Icon(
                        if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.tags_favorite),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current.isEmpty()) {
                    Text(
                        stringResource(R.string.tags_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { name?.let { store.set(it, current - tag) } },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.wp_remove), modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.tags_add_hint)) },
                        singleLine = true,
                        enabled = name != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add(text) }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { add(text) }, enabled = name != null && text.isNotBlank()) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tags_add))
                    }
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { tag ->
                            AssistChip(onClick = { add(tag) }, label = { Text(tag) })
                        }
                    }
                }
            }
        },
    )
}

private const val MAX_SUGGESTIONS = 16
