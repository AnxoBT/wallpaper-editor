package com.wallpaperfit.app.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.components.AppLogo
import com.wallpaperfit.app.ui.settings.AppLanguage
import com.wallpaperfit.app.ui.theme.CanvasBackground

/** Si ya se ha visto la guía de bienvenida (se guarda al terminarla o saltarla). */
object Welcome {
    fun pending(context: Context): Boolean = !prefs(context).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) = prefs(context).edit().putBoolean(KEY_DONE, true).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "welcome"
    private const val KEY_DONE = "done"
}

/** Un paso de la guía: icono, título y explicación. */
private class Page(val icon: ImageVector, @StringRes val title: Int, @StringRes val text: Int)

/**
 * Guía de la primera vez: elegir idioma y tres pasos con lo básico (editor, fondos y reloj). Se
 * puede saltar y volver a ver desde Ajustes. En Windows no hay widget de reloj: ese paso no sale.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pages = remember {
        listOfNotNull(
            Page(Icons.Outlined.Crop, R.string.welcome_edit_title, R.string.welcome_edit_text),
            Page(Icons.Outlined.Wallpaper, R.string.welcome_wallpapers_title, R.string.welcome_wallpapers_text),
            Page(Icons.Outlined.Schedule, R.string.welcome_clock_title, R.string.welcome_clock_text).takeIf { PlatformFeatures.WIDGET_PREVIEW },
        )
    }
    // 0 = idioma; luego, los pasos. Se guarda: al cambiar el idioma se vuelve a crear la pantalla.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = step == pages.size
    val finish = {
        Welcome.markDone(context)
        onDone()
    }
    BackHandler(enabled = step > 0) { step-- }

    // Surface: fondo y color de texto del tema (claro sobre el fondo oscuro).
    Surface(color = CanvasBackground, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (!last) {
                    TextButton(onClick = finish, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(R.string.welcome_skip))
                    }
                }
            }
            // En pantallas pequeñas el contenido se desplaza en vez de montarse sobre los botones.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (step == 0) {
                    LanguageStep(context)
                } else {
                    val page = pages[step - 1]
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(page.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages.size + 1) { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { if (last) finish() else step++ }, modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 48.dp)) {
                Text(stringResource(if (last) R.string.welcome_start else R.string.welcome_next))
            }
        }
    }
}

/** Primer paso: bienvenida y elegir idioma (por defecto, el del dispositivo). */
@Composable
private fun LanguageStep(context: Context) {
    val activity = context as? Activity
    val language = remember { AppLanguage.current(context) }
    AppLogo(modifier = Modifier.size(96.dp))
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.welcome_language), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
            AppLanguage.Option.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(
                            selected = option == language,
                            role = Role.RadioButton,
                            onClick = { if (option != language && activity != null) AppLanguage.set(activity, option) },
                        )
                        .padding(horizontal = 16.dp),
                ) {
                    RadioButton(selected = option == language, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.welcome_language_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
