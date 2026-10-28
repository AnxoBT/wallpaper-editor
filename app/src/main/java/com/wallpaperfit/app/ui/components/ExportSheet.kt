package com.wallpaperfit.app.ui.components

import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.PlatformFeatures
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.wallpaperfit.app.model.ExportFormat
import com.wallpaperfit.app.model.SaveMode
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.WallpaperTarget
import com.wallpaperfit.app.ui.label

private val WallpaperTarget.icon: ImageVector
    get() = when (this) {
        WallpaperTarget.HOME -> Icons.Outlined.Home
        WallpaperTarget.LOCK -> Icons.Outlined.Lock
        WallpaperTarget.BOTH -> Icons.Outlined.PhoneAndroid
    }

private val SaveMode.label: Int
    get() = when (this) {
        SaveMode.COPY -> R.string.save_mode_copy
        SaveMode.REPLACE -> R.string.save_mode_replace
    }

private val WallpaperTarget.label: Int
    get() = when (this) {
        WallpaperTarget.HOME -> R.string.wallpaper_home
        WallpaperTarget.LOCK -> R.string.wallpaper_lock
        WallpaperTarget.BOTH -> R.string.wallpaper_both
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    size: TargetSize,
    format: ExportFormat,
    isExporting: Boolean,
    saveFolderName: String?,
    sourceFolderName: String?,
    saveMode: SaveMode,
    originalName: String?,
    onFormatSelected: (ExportFormat) -> Unit,
    onSaveModeSelected: (SaveMode) -> Unit,
    onChangeFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onSaveToGallery: () -> Unit,
    onReplaceOriginal: () -> Unit,
    onSetWallpaper: (WallpaperTarget) -> Unit,
    liveWallpaperActive: Boolean,
    showOnWallpaper: Boolean,
    onShowOnWallpaperChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.export_title, size.width, size.height),
                style = MaterialTheme.typography.titleLarge,
            )

            if (saveMode == SaveMode.COPY) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { option ->
                    FilterChip(
                        selected = option == format,
                        onClick = { onFormatSelected(option) },
                        label = { Text(stringResource(option.label)) },
                        enabled = !isExporting,
                    )
                }
            }

            val modes = SaveMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == saveMode,
                        onClick = { onSaveModeSelected(mode) },
                        enabled = !isExporting && (mode == SaveMode.COPY || originalName != null),
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(stringResource(mode.label), maxLines = 1) },
                    )
                }
            }

            if (saveMode == SaveMode.COPY) {
                if (originalName == null) {
                    Text(
                        text = stringResource(R.string.save_replace_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Nombre de la carpeta en su propia línea: con los botones al lado se cortaba.
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.save_folder,
                                saveFolderName
                                    ?: sourceFolderName?.let { stringResource(R.string.save_folder_original, it) }
                                    ?: stringResource(R.string.save_folder_default),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (saveFolderName != null) {
                            TextButton(onClick = onResetFolder, enabled = !isExporting) { Text(stringResource(R.string.save_folder_reset)) }
                        }
                        TextButton(onClick = onChangeFolder, enabled = !isExporting) { Text(stringResource(R.string.save_folder_change)) }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.save_replace_warning, size.width, size.height),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = if (saveMode == SaveMode.REPLACE) onReplaceOriginal else onSaveToGallery,
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (saveMode == SaveMode.REPLACE) {
                        stringResource(R.string.save_replace_button)
                    } else {
                        stringResource(R.string.save_gallery)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (liveWallpaperActive) {
                // Con el fondo dinámico puesto, lo natural es que la foto guardada pase a ser su fondo.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = showOnWallpaper, enabled = !isExporting, role = Role.Checkbox, onValueChange = onShowOnWallpaperChange),
                ) {
                    Checkbox(checked = showOnWallpaper, onCheckedChange = null, enabled = !isExporting)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_show_on_wallpaper), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.set_wallpaper_title), style = MaterialTheme.typography.titleMedium)
            if (liveWallpaperActive) {
                Text(
                    stringResource(R.string.set_wallpaper_replaces_live),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperTarget.entries.filter { it == WallpaperTarget.HOME || PlatformFeatures.LOCK_SCREEN }.forEach { target ->
                    OutlinedButton(
                        onClick = { onSetWallpaper(target) },
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(target.icon, contentDescription = null)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(target.label), maxLines = 1)
                        }
                    }
                }
            }

            if (isExporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.exporting), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
