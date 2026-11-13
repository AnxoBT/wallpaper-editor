package com.wallpaperfit.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wallpaperfit.app.R

/**
 * Novedades al actualizar: la primera vez que se abre una versión nueva se enseña un resumen corto
 * (strings_whats_new.xml, el de la versión actual, en inglés y español). En una instalación nueva
 * no sale: ya está la guía de bienvenida.
 */
object WhatsNew {

    /** Versión instalada ("1.2.0"), o vacía si no se puede leer. */
    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** Si hay que enseñar las novedades: la última versión abierta es otra (y no es la primera vez). */
    fun shouldShow(lastSeen: String?, current: String): Boolean = lastSeen != null && current.isNotEmpty() && lastSeen != current

    /** Al abrir la app: true si toca enseñarlas; desde ese momento la versión cuenta como vista. */
    fun checkOnLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val current = currentVersion(context)
        val show = shouldShow(prefs.getString(KEY_LAST, null), current)
        if (current.isNotEmpty()) prefs.edit().putString(KEY_LAST, current).apply()
        return show
    }

    private const val PREFERENCES = "whats_new"
    private const val KEY_LAST = "last_version"
}

/** Resumen de lo nuevo en esta versión (también desde Ajustes > Novedades). */
@Composable
fun WhatsNewDialog(version: String, onDismiss: () -> Unit) {
    // Un punto por línea (la versión de Windows no lee listas de textos de Android).
    val items = stringResource(R.string.whats_new_items).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title, version)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok)) } },
    )
}
