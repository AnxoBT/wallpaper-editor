package com.wallpaperfit.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copia en una carpeta nueva de Imágenes (por ejemplo "Pictures/Favoritas") todas las fotos con
 * una etiqueta, para usarlas en otra app. Las originales no se tocan. Las copias conservan el
 * nombre y la fecha, así salen en el mismo orden; las que ya están en la carpeta no se repiten.
 */
object TagFolderCopy {

    /** Resultado: cuántas se copiaron, cuántas ya estaban y cuántas fallaron. */
    data class Result(val copied: Int, val skipped: Int, val failed: Int)

    /** Carpeta de destino para una etiqueta (ver [PhotoTags.folderName]). */
    fun folderFor(tag: String): String = "${Environment.DIRECTORY_PICTURES}/${PhotoTags.folderName(tag)}"

    suspend fun copy(context: Context, tags: PhotoTags, tag: String): Result = withContext(Dispatchers.IO) {
        val folder = folderFor(tag)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext Result(0, 0, 0)
        val photos = tags.photosMatching(TagFilter(included = setOf(tag)), "")
        val existing = namesIn(context, folder)
        var copied = 0
        var skipped = 0
        var failed = 0
        for (photo in photos) {
            val info = info(context, photo)
            if (info == null) {
                failed++
                continue
            }
            // Las que ya están en la carpeta (incluidas las propias copias de otra vez) no se repiten.
            if (existing[info.name] == info.size || info.path?.let { File(it).parentFile?.path?.endsWith(folder) } == true) {
                skipped++
                continue
            }
            if (copyOne(context, photo, info, folder)) {
                existing[info.name] = info.size
                copied++
            } else {
                failed++
            }
        }
        Result(copied, skipped, failed)
    }

    private class Info(val name: String, val mime: String?, val modified: Long, val taken: Long?, val path: String?, val size: Long)

    private fun info(context: Context, uri: Uri): Info? = try {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst() || c.isNull(0)) return null
            Info(c.getString(0), c.getString(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3), c.getString(4), c.getLong(5))
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer $uri", e)
        null
    }

    /** Fotos que ya hay en la carpeta: nombre y tamaño (el mismo nombre con otro tamaño es otra foto). */
    private fun namesIn(context: Context, folder: String): MutableMap<String, Long> {
        val names = HashMap<String, Long>()
        try {
            context.contentResolver.query(
                MediaCollections.images,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf("$folder/"),
                null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { names[it] = c.getLong(1) } }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mirar $folder", e)
        }
        return names
    }

    private fun copyOne(context: Context, source: Uri, info: Info, folder: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, info.name)
            info.mime?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = try {
            resolver.insert(MediaCollections.images, values)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear la copia de ${info.name}", e)
            null
        } ?: return false
        return try {
            resolver.openInputStream(source)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            keepDate(context, target, info)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo copiar ${info.name}", e)
            resolver.delete(target, null, null)
            false
        }
    }

    /** La copia con la fecha de la original, para que las apps de galería la pongan en su sitio. */
    private fun keepDate(context: Context, target: Uri, info: Info) {
        val path = context.contentResolver.query(target, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        // Sin fecha de captura, la de modificación: así la ordenan igual todas las galerías.
        val dates = OriginalReplacer.Dates(info.modified, info.taken ?: (info.modified * 1000), path)
        OriginalReplacer.restoreDates(context, target, dates, offsetSeconds = 0)
    }

    private const val TAG = "TagFolderCopy"
}
