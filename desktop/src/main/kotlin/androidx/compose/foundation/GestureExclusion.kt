package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

/** Los gestos de borde del sistema solo existen en los móviles. */
fun Modifier.systemGestureExclusion(exclusion: (LayoutCoordinates) -> Rect): Modifier = this
