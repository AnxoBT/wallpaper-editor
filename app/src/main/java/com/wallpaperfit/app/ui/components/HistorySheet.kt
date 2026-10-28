package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.ui.icon
import com.wallpaperfit.app.ui.label

/** Lista de pasos confirmados; tocar uno vuelve a ese punto (como "Ver ediciones" de Snapseed). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    steps: List<Tool?>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.history_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                // El más reciente arriba, como una pila.
                itemsIndexed(steps.reversed()) { reversedIndex, tool ->
                    val index = steps.lastIndex - reversedIndex
                    val selected = index == current
                    val future = index > current
                    val color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tool?.icon ?: Icons.Outlined.Image, contentDescription = null, tint = color)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = tool?.let { stringResource(it.label) } ?: stringResource(R.string.history_original),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}
