package com.wallpaperfit.app.storage

import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Colecciones de MediaStore que usa la app. */
object MediaCollections {

    /** Imágenes de todo el almacenamiento externo (en Android 10+, de todos los volúmenes). */
    val images: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
}
