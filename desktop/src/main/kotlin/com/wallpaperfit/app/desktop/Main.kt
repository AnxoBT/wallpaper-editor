package com.wallpaperfit.app.desktop

import android.app.Activity
import androidx.activity.compose.BackDispatcher
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.EditorViewModel
import com.wallpaperfit.app.ui.WallpaperFitApp
import com.wallpaperfit.app.ui.settings.AppLanguage
import com.wallpaperfit.app.ui.theme.WallpaperFitTheme
import com.wallpaperfit.app.wallpaper.WallpaperController
import kotlinx.coroutines.launch
import javax.swing.UIManager

/** La app: una ventana, y un icono en la bandeja mientras el pase de fondos está en marcha. */
fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val app = Activity()
    AppLanguage.restore(app)
    WallpaperController.start(app)
    application {
        val viewModel = remember { EditorViewModel(app) }
        val active by WallpaperController.active.collectAsState()
        val scope = rememberCoroutineScope()
        var visible by remember { mutableStateOf(true) }
        var confirmClose by remember { mutableStateOf(false) }
        // Al cambiar de idioma se crean de nuevo las pantallas (como la actividad en Android).
        var languageVersion by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { AppLanguage.onChanged = { languageVersion++ } }

        val hide = {
            // Con el pase en marcha, cerrar la ventana no lo para: sigue desde la bandeja.
            if (active) visible = false else exitApplication()
        }
        Window(
            visible = visible,
            onCloseRequest = { if (viewModel.uiState.value.hasUnsavedChanges) confirmClose = true else hide() },
            title = "Wallpaper Editor",
            icon = painterResource("res/icon.png"),
            state = rememberWindowState(placement = WindowPlacement.Maximized),
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyUp && event.key == Key.Escape && BackDispatcher.onBack()
            },
        ) {
            FileDialogs.window = window
            CompositionLocalProvider(LocalContext provides app) {
                WallpaperFitTheme {
                    key(languageVersion) { WallpaperFitApp(viewModel = viewModel) }
                    if (confirmClose) UnsavedDialog(onKeep = { confirmClose = false }, onDiscard = { confirmClose = false; hide() })
                }
            }
        }
        if (active) {
            Tray(
                icon = painterResource("res/icon.png"),
                tooltip = "Wallpaper Editor",
                onAction = { visible = true },
                menu = {
                    Item(Strings.get(R.string.desktop_tray_open), onClick = { visible = true })
                    Item(Strings.get(R.string.wp_next), onClick = { scope.launch { WallpaperController.next(app) } })
                    Item(Strings.get(R.string.desktop_tray_exit), onClick = ::exitApplication)
                },
            )
        }
    }
}

@Composable
private fun UnsavedDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = { Text(stringResource(R.string.unsaved_text)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.unsaved_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.unsaved_keep)) } },
    )
}
