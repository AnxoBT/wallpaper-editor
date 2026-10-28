package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.storage.TagFilter
import com.wallpaperfit.app.storage.TagState

private val TagState.icon: ImageVector?
    get() = when (this) {
        TagState.NONE -> null
        TagState.INCLUDED -> Icons.Outlined.Check
        TagState.EXCLUDED -> Icons.Outlined.Block
    }

/** Texto de una etiqueta del filtro: las excluidas no llevan número, porque no queda ninguna foto con ellas. */
@Composable
fun tagFilterLabel(tag: String, count: Int, state: TagState): String =
    if (state == TagState.EXCLUDED) stringResource(R.string.tags_excluded, tag) else "$tag · $count"

/** Etiqueta de un filtro: cada toque incluye, excluye o la quita (ver [TagFilter.cycled]). */
@Composable
fun TagFilterChip(tag: String, count: Int, state: TagState, onClick: () -> Unit) {
    val colors = if (state == TagState.EXCLUDED) {
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    FilterChip(
        selected = state != TagState.NONE,
        onClick = onClick,
        label = { Text(tagFilterLabel(tag, count, state), maxLines = 1) },
        leadingIcon = state.icon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        colors = colors,
    )
}

/** Las etiquetas que ofrecer: las del filtro primero, y las que contienen lo escrito (sin mirar mayúsculas ni tildes). */
fun offeredTags(counts: List<Pair<String, Int>>, filter: TagFilter, query: String): List<Pair<String, Int>> {
    val wanted = PhotoTags.fold(query)
    return PhotoTags.selectedFirst(counts, filter.included, filter.excluded)
        .filter { (tag, _) -> wanted.isEmpty() || filter.state(tag) != TagState.NONE || PhotoTags.fold(tag).contains(wanted) }
}

/**
 * Elegir etiquetas para un filtro: se escribe para encontrarla y se toca. [options] son las
 * etiquetas con sus fotos reales (null mientras se cuentan).
 */
@Composable
fun TagFilterDialog(
    options: List<Pair<String, Int>>?,
    filter: TagFilter,
    emptyMessage: String,
    onCycle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(options, filter, query) { options?.let { offeredTags(it, filter, query) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wp_add_tag)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            if (!filter.isEmpty) TextButton(onClick = onClear) { Text(stringResource(R.string.tags_filter_clear)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
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
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.tags_cycle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    shown == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    shown.isEmpty() -> Text(
                        if (query.isBlank()) emptyMessage else stringResource(R.string.tags_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(shown, key = { it.first }) { (tag, count) -> TagFilterRow(tag, count, filter.state(tag)) { onCycle(tag) } }
                    }
                }
            }
        },
    )
}

@Composable
private fun TagFilterRow(tag: String, count: Int, state: TagState, onClick: () -> Unit) {
    val tint = when (state) {
        TagState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        TagState.INCLUDED -> MaterialTheme.colorScheme.primary
        TagState.EXCLUDED -> MaterialTheme.colorScheme.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Icon(state.icon ?: Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(tagFilterLabel(tag, count, state), style = MaterialTheme.typography.bodyLarge)
    }
}
