package com.wallpaperfit.app.wallpaper

import com.wallpaperfit.app.R

// Generado por gen_clock_layouts.py: no editar a mano.

/** Sombra concreta de un diseño del reloj: ninguna, oscura (texto claro) o clara (texto oscuro). */
enum class ShadowVariant { NONE, DARK, LIGHT }

/** Diseño del widget para una fuente y una sombra (los widgets no permiten cambiar la fuente por código). */
fun clockLayout(font: ClockFont, shadow: ShadowVariant): Int = when (font) {
    ClockFont.SANS_LIGHT -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_sans_light_none
        ShadowVariant.DARK -> R.layout.widget_clock_sans_light_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_sans_light_light
    }
    ClockFont.SANS_THIN -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_sans_thin_none
        ShadowVariant.DARK -> R.layout.widget_clock_sans_thin_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_sans_thin_light
    }
    ClockFont.SANS_MEDIUM -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_sans_medium_none
        ShadowVariant.DARK -> R.layout.widget_clock_sans_medium_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_sans_medium_light
    }
    ClockFont.CONDENSED -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_condensed_none
        ShadowVariant.DARK -> R.layout.widget_clock_condensed_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_condensed_light
    }
    ClockFont.SERIF -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_serif_none
        ShadowVariant.DARK -> R.layout.widget_clock_serif_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_serif_light
    }
    ClockFont.MONO -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_mono_none
        ShadowVariant.DARK -> R.layout.widget_clock_mono_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_mono_light
    }
    ClockFont.BEBAS -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_bebas_none
        ShadowVariant.DARK -> R.layout.widget_clock_bebas_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_bebas_light
    }
    ClockFont.ORBITRON -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_orbitron_none
        ShadowVariant.DARK -> R.layout.widget_clock_orbitron_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_orbitron_light
    }
    ClockFont.RIGHTEOUS -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_righteous_none
        ShadowVariant.DARK -> R.layout.widget_clock_righteous_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_righteous_light
    }
    ClockFont.QUICKSAND -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_quicksand_none
        ShadowVariant.DARK -> R.layout.widget_clock_quicksand_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_quicksand_light
    }
    ClockFont.PLAYFAIR -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_playfair_none
        ShadowVariant.DARK -> R.layout.widget_clock_playfair_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_playfair_light
    }
    ClockFont.PACIFICO -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_pacifico_none
        ShadowVariant.DARK -> R.layout.widget_clock_pacifico_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_pacifico_light
    }
    ClockFont.VIVO_SERIF -> when (shadow) {
        ShadowVariant.NONE -> R.layout.widget_clock_vivo_serif_none
        ShadowVariant.DARK -> R.layout.widget_clock_vivo_serif_dark
        ShadowVariant.LIGHT -> R.layout.widget_clock_vivo_serif_light
    }
}
