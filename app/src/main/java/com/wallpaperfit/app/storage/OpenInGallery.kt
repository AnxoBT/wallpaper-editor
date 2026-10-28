package com.wallpaperfit.app.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wallpaperfit.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abre una foto en la galería que elija el usuario (Google Fotos, la del móvil…), para hacer allí lo
 * que la app no puede, como marcarla como favorita en Google Fotos.
 */
object OpenInGallery {

    /** Devuelve false si no hay ninguna app que pueda abrirla. */
    suspend fun open(context: Context, uri: Uri): Boolean {
        // Las galerías entienden mejor la URI de MediaStore que la de una carpeta elegida (SAF).
        val target = withContext(Dispatchers.IO) {
            try {
                OriginalReplacer.resolve(context, uri)?.uri
            } catch (e: Exception) {
                null
            }
        } ?: uri
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(target, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, context.getString(R.string.open_in_gallery))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
