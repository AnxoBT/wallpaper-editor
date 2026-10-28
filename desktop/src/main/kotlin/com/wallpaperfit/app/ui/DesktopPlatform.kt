package com.wallpaperfit.app.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.desktop.FileDialogs
import com.wallpaperfit.app.desktop.PhotoLibrary

/** Lo que cambia en Windows dentro de las pantallas comunes (ver el Platform.kt de Android). */
object PlatformFeatures {
    /** El modelo de IA y el reconocimiento de texto solo existen para móviles. */
    const val AI_TOOLS = false

    /** Windows no deja cambiar la pantalla de bloqueo igual que el escritorio. */
    const val LOCK_SCREEN = false
}

/** La ventana es ancha: tantas columnas como quepan, en vez de las fijas del móvil. */
val PhotoGridCells: GridCells = GridCells.Adaptive(150.dp)
val AlbumGridCells: GridCells = GridCells.Adaptive(220.dp)

/** En el PC no hay una galería: el usuario dice en qué carpetas tiene las fotos. */
@Composable
fun GalleryLibraryAction(onChanged: () -> Unit) {
    IconButton(
        onClick = {
            FileDialogs.chooseFolder()?.let {
                PhotoLibrary.addFolder(it)
                onChanged()
            }
        },
    ) {
        Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.desktop_add_library))
    }
}
