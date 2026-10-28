package com.wallpaperfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Fondo grafito del logo (mismo degradado que el icono adaptativo). */
val LogoTop = Color(0xFF2A2B36)
val LogoBottom = Color(0xFF111217)

/** Fondo del lienzo de edición, más oscuro que las superficies para que la foto destaque. */
val CanvasBackground = Color(0xFF0B0C0E)

// Editor siempre oscuro, como Snapseed en modo noche: los colores de la foto no se contaminan.
private val EditorColors = darkColorScheme(
    primary = Color(0xFFFF8F70),
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF5C2A1C),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFC7B3FF),
    onSecondary = Color(0xFF2B1B5A),
    tertiary = Color(0xFFFFD166),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainer = Color(0xFF1C1E22),
    surfaceContainerHigh = Color(0xFF26282D),
    surfaceContainerHighest = Color(0xFF313338),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
)

@Composable
fun WallpaperFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EditorColors, content = content)
}
