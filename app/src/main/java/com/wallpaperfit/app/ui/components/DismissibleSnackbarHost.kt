package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * Avisos que no hace falta esperar: se quitan deslizándolos a un lado o tocándolos, para poder
 * ver al momento lo que tapan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissibleSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        // Un estado de deslizamiento nuevo por aviso: si no, el siguiente saldría ya apartado.
        key(data) {
            val swipe = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
                    true
                },
            )
            SwipeToDismissBox(state = swipe, backgroundContent = {}) {
                Snackbar(data, modifier = Modifier.clickable { data.dismiss() })
            }
        }
    }
}
