"""Genera los diseños del widget de reloj (una variante por fuente y sombra) y su tabla en Kotlin."""
import os

# Uso: python tools/gen_clock_layouts.py (desde cualquier carpeta).
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main')
FONTS = [
    ('SANS_LIGHT', 'sans-serif-light'),
    ('SANS_THIN', 'sans-serif-thin'),
    ('SANS_MEDIUM', 'sans-serif-medium'),
    ('CONDENSED', 'sans-serif-condensed'),
    ('SERIF', 'serif'),
    ('MONO', 'monospace'),
    ('BEBAS', '@font/bebas_neue'),
    ('ORBITRON', '@font/orbitron'),
    ('RIGHTEOUS', '@font/righteous'),
    ('QUICKSAND', '@font/quicksand'),
    ('PLAYFAIR', '@font/playfair_display'),
    ('PACIFICO', '@font/pacifico'),
    # Estilo del reloj de bloqueo de vivo (Playfair Display Black); la fecha va en la del sistema, como en vivo.
    ('VIVO_SERIF', '@font/playfair_display'),
]
# Ajustes extra por fuente: familia de la fecha y variación de la fuente variable.
DATE_FAMILY = {'VIVO_SERIF': 'sans-serif'}
VARIATION = {'VIVO_SERIF': "'wght' 900"}
SHADOWS = {
    'none': '',
    'dark': '\n        android:shadowColor="#AA000000"\n        android:shadowDx="0"\n        android:shadowDy="2"\n        android:shadowRadius="10"',
    'light': '\n        android:shadowColor="#AAFFFFFF"\n        android:shadowDx="0"\n        android:shadowDy="2"\n        android:shadowRadius="10"',
}

TEMPLATE = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por gen_clock_layouts.py: widget de reloj, fuente {font}, sombra {shadow}. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/clock_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="0dp">

    <!-- Fecha encima de la hora (como el reloj de bloqueo de vivo) o debajo: se muestra una u otra. -->
    <TextClock
        android:id="@+id/clock_date_top"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="{date_family}"
        android:format12Hour="EEE, d MMM"
        android:format24Hour="EEE, d MMM"
        android:gravity="center"
        android:includeFontPadding="false"
        android:maxLines="1"
        android:textColor="#FFFFFFFF"
        android:textSize="16sp"
        android:visibility="gone"{shadow_attrs} />

    <TextClock
        android:id="@+id/clock_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="{family}"{variation}
        android:format12Hour="h:mm"
        android:format24Hour="HH:mm"
        android:gravity="center"
        android:includeFontPadding="false"
        android:maxLines="1"
        android:textColor="#FFFFFFFF"
        android:textSize="64sp"{shadow_attrs} />

    <TextClock
        android:id="@+id/clock_date"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="{date_family}"
        android:format12Hour="EEEE, d MMMM"
        android:format24Hour="EEEE, d MMMM"
        android:gravity="center"
        android:maxLines="1"
        android:textColor="#FFFFFFFF"
        android:textSize="16sp"{shadow_attrs} />
</LinearLayout>
'''

layout_dir = os.path.join(ROOT, 'res', 'layout')
os.makedirs(layout_dir, exist_ok=True)
for name, family in FONTS:
    for shadow, attrs in SHADOWS.items():
        path = os.path.join(layout_dir, f'widget_clock_{name.lower()}_{shadow}.xml')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            variation = ('\n        android:fontVariationSettings="' + VARIATION[name] + '"') if name in VARIATION else ''
            f.write(TEMPLATE.format(font=name, shadow=shadow, family=family, date_family=DATE_FAMILY.get(name, family),
                                    variation=variation, shadow_attrs=attrs))

lines = []
for name, _ in FONTS:
    lines.append(f'    ClockFont.{name} -> when (shadow) {{')
    for shadow in SHADOWS:
        lines.append(f'        ShadowVariant.{shadow.upper()} -> R.layout.widget_clock_{name.lower()}_{shadow}')
    lines.append('    }')

kotlin = '''package com.wallpaperfit.app.wallpaper

import com.wallpaperfit.app.R

// Generado por gen_clock_layouts.py: no editar a mano.

/** Sombra concreta de un diseño del reloj: ninguna, oscura (texto claro) o clara (texto oscuro). */
enum class ShadowVariant { NONE, DARK, LIGHT }

/** Diseño del widget para una fuente y una sombra (los widgets no permiten cambiar la fuente por código). */
fun clockLayout(font: ClockFont, shadow: ShadowVariant): Int = when (font) {
''' + '\n'.join(lines) + '\n}\n'
with open(os.path.join(ROOT, 'java', 'com', 'wallpaperfit', 'app', 'wallpaper', 'ClockLayouts.kt'), 'w', encoding='utf-8', newline='\n') as f:
    f.write(kotlin)
print('ok', len(FONTS) * len(SHADOWS), 'layouts')
