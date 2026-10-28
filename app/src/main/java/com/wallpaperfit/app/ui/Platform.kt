package com.wallpaperfit.app.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.wallpaperfit.app.ui.wallpapers.WidgetPreview

/**
 * Lo que cambia entre plataformas dentro de las pantallas comunes. La versión de Windows
 * (desktop/) compila estas mismas pantallas con su propio archivo Platform.kt.
 */
object PlatformFeatures {
    /** Relleno con IA del Corrector y detección de texto. */
    const val AI_TOOLS = true

    /** Poner la foto en la pantalla de bloqueo. */
    const val LOCK_SCREEN = true

    /** Ver la foto editada con el reloj y los widgets del bloqueo y del escritorio. */
    const val WIDGET_PREVIEW = true
}

/** Columnas de las cuadrículas de fotos y de carpetas del selector. */
val PhotoGridCells: GridCells = GridCells.Fixed(3)
val AlbumGridCells: GridCells = GridCells.Fixed(2)

/** Acción extra de la barra del selector de fotos ([onChanged]: han cambiado las carpetas). */
@Composable
fun GalleryLibraryAction(onChanged: () -> Unit) = Unit

/** La foto editada a pantalla completa con los widgets del móvil encima (ver [WidgetPreview]). */
@Composable
fun PhoneWidgetPreview(preview: ImageBitmap, sourceUri: String?, onClose: () -> Unit) = WidgetPreview(preview, sourceUri, onClose)
