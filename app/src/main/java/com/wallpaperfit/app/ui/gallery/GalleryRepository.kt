package com.wallpaperfit.app.ui.gallery

import com.wallpaperfit.app.storage.OriginalReplacer
import com.wallpaperfit.app.storage.MediaCollections
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carpeta de fotos del móvil (un "bucket" de MediaStore). */
data class Album(val id: String, val name: String, val count: Int, val cover: Uri)

/** Dónde se quedó el selector la última vez: carpeta y posición de desplazamiento. */
data class PickerPosition(val albumId: String?, val index: Int, val offset: Int)

/**
 * Fotos del móvil por carpetas, leídas de MediaStore (necesita el permiso de fotos). Recuerda la
 * última carpeta y hasta dónde se había bajado, para volver a esa altura al entrar en ella.
 */
class GalleryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("gallery_picker", Context.MODE_PRIVATE)

    private val collection: Uri = MediaCollections.images

    /** Carpetas con fotos, la que tiene la foto más reciente primero. */
    suspend fun albums(): List<Album> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Triple<String, Int, Uri>>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val bucket = cursor.getString(1) ?: continue
                val previous = found[bucket]
                found[bucket] = if (previous == null) {
                    Triple(cursor.getString(2) ?: "", 1, ContentUris.withAppendedId(collection, cursor.getLong(0)))
                } else {
                    previous.copy(second = previous.second + 1)
                }
            }
        }
        found.map { (id, value) -> Album(id, value.first, value.second, value.third) }
    }

    /** Fotos de una carpeta (o de todas si [albumId] es null), de la más reciente a la más antigua. */
    suspend fun photos(albumId: String?): List<Uri> = withContext(Dispatchers.IO) {
        val photos = ArrayList<Uri>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            if (albumId != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null,
            if (albumId != null) arrayOf(albumId) else null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) photos += ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
        photos
    }

    /**
     * Carpeta (bucket) y número de MediaStore de una foto, para abrir el selector justo en ella.
     * Sirve con cualquier URI que dé la app (selector de fotos, carpeta de Fondos, MediaStore).
     */
    suspend fun locate(uri: Uri): Pair<String, Long>? = withContext(Dispatchers.IO) {
        try {
            val media = OriginalReplacer.resolve(context, uri)?.uri ?: return@withContext null
            context.contentResolver.query(media, arrayOf(MediaStore.Images.Media.BUCKET_ID), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let { it to ContentUris.parseId(media) } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun lastPosition(): PickerPosition = PickerPosition(
        albumId = prefs.getString(KEY_ALBUM, null),
        index = prefs.getInt(KEY_INDEX, 0),
        offset = prefs.getInt(KEY_OFFSET, 0),
    )

    fun savePosition(position: PickerPosition) {
        prefs.edit()
            .putString(KEY_ALBUM, position.albumId)
            .putInt(KEY_INDEX, position.index)
            .putInt(KEY_OFFSET, position.offset)
            .apply()
    }

    private companion object {
        const val KEY_ALBUM = "album"
        const val KEY_INDEX = "index"
        const val KEY_OFFSET = "offset"
    }
}
