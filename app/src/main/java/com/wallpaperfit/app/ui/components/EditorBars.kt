package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.wallpaperfit.app.model.ToolGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.ui.icon
import com.wallpaperfit.app.ui.label
import com.wallpaperfit.app.ui.theme.CanvasBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    activeTool: Tool?,
    canUndo: Boolean,
    canRedo: Boolean,
    onOpenImage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHistory: () -> Unit,
    onCompare: (Boolean) -> Unit,
    onExport: () -> Unit,
    exportEnabled: Boolean = true,
) {
    TopAppBar(
        title = {
            if (activeTool != null) Text(stringResource(activeTool.label))
        },
        navigationIcon = {
            if (activeTool == null) {
                IconButton(onClick = onOpenImage) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.action_open))
                }
            }
        },
        actions = {
            if (activeTool == null) {
                IconButton(onClick = onHistory, enabled = canUndo || canRedo) {
                    Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.action_history))
                }
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.action_undo))
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.action_redo))
                }
            }
            CompareButton(onCompare)
            if (activeTool == null) {
                FilledTonalButton(onClick = onExport, enabled = exportEnabled, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                    Text(stringResource(R.string.action_export))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBackground),
    )
}

/** Mantener pulsado muestra la foto sin ajustes, como el botón comparar de Snapseed. */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit) {
    val currentOnCompare by rememberUpdatedState(onCompare)
    val description = stringResource(R.string.action_compare)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentOnCompare(true)
                        try {
                            awaitRelease()
                        } finally {
                            currentOnCompare(false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Compare, contentDescription = null)
    }
}

/**
 * Barra inferior de herramientas, agrupada como en Snapseed en pestañas (Composición, Ajustes,
 * Efectos) para que cada grupo quepa sin apenas desplazarse.
 */
@Composable
fun ToolBar(
    group: ToolGroup,
    onGroupSelected: (ToolGroup) -> Unit,
    onToolSelected: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolGroup.entries.forEach { option ->
                val selected = option == group
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onGroupSelected(option) })
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ToolRow(tools = Tool.entries.filter { it.group == group }, onToolSelected = onToolSelected)
    }
}

@Composable
private fun ToolRow(tools: List<Tool>, onToolSelected: (Tool) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        tools.forEach { tool ->
            Column(
                modifier = Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToolSelected(tool) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tool.icon, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(tool.label),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Fila inferior de los paneles de herramienta: ✕ · nombre · restablecer · ✓. */
@Composable
fun ToolActionRow(
    title: String,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onReset) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.action_reset))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onApply) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.action_apply),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
