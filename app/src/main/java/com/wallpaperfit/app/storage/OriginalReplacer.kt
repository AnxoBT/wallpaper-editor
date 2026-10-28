package com.wallpaperfit.app.storage

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Sobrescribe la foto original con el resultado. Solo es posible con fotos del propio móvil
 * (no de la nube) y Android pide permiso al usuario con su propio diálogo antes de modificarla.
 */
object OriginalReplacer {

    /** Foto de la galería que se puede reemplazar. */
    class Target(val uri: Uri, val displayName: String, val mimeType: String)

    /**
     * Entrada de MediaStore que hay detrás de la imagen elegida. El selector de fotos devuelve
     * URIs propias (…/picker/…/media/<id>) cuyo número es el de MediaStore en las fotos locales.
     */
    fun resolve(context: Context, picked: Uri): Target? {
        // Cada tipo de URI da solo el número de MediaStore; la URI se monta siempre en la colección
        // de imágenes, porque createWriteRequest / createTrashRequest rechazan la genérica ".../file/ID"
        // (la que da Android para las fotos de una carpeta).
        val id = try {
            when {
                picked.authority == MediaStore.AUTHORITY && picked.pathSegments.any { it.startsWith("picker") } ->
                    picked.lastPathSegment?.toLongOrNull()
                picked.authority == MediaStore.AUTHORITY -> ContentUris.parseId(picked)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && DocumentsContract.isDocumentUri(context, picked) ->
                    MediaStore.getMediaUri(context, picked)?.let(ContentUris::parseId)
                else -> null
            }
        } catch (e: Exception) {
            null
        } ?: return null
        val media = ContentUris.withAppendedId(MediaCollections.images, id)
        // Sin permiso de galería no se puede consultar MediaStore: el nombre y el tipo se leen de
        // la URI que sí tenemos (la del selector). El permiso de escritura lo da el usuario luego.
        return try {
            val name = context.contentResolver.query(picked, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: return null
            Target(media, name, context.contentResolver.getType(picked) ?: "image/jpeg")
        } catch (e: Exception) {
            null
        }
    }

    /** Android 11+: diálogo del sistema para conceder permiso de modificación sobre la foto. */
    fun writeRequest(context: Context, target: Target): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(target.uri)).intentSender
        } else {
            null
        }

    /**
     * Escribe [bitmap] encima de la foto, en su mismo formato. En Android 10 puede lanzar
     * RecoverableSecurityException, cuya acción pide el permiso al usuario.
     */
    suspend fun write(context: Context, target: Target, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (format, quality) = when (target.mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG to 100
            "image/webp" -> webp() to 95
            else -> Bitmap.CompressFormat.JPEG to 95
        }
        // Lo que decide el orden en la galería: fechas de MediaStore y fechas EXIF de la foto.
        val dates = queryDates(context, target.uri)
        val exif = readExif(context, target.uri)

        val stream = resolver.openOutputStream(target.uri, "wt") ?: throw IOException("No se pudo abrir ${target.uri}")
        stream.use {
            if (!bitmap.compress(format, quality, it)) throw IOException("Bitmap.compress falló")
        }

        if (exif.isNotEmpty() && format != Bitmap.CompressFormat.PNG) writeExif(context, target.uri, exif)
        if (dates != null) restoreDates(context, target.uri, dates)
    }

    /** Fechas de la foto en MediaStore: modificación (segundos), captura (ms) y ruta del archivo. */
    private class Dates(val modified: Long, val taken: Long?, val path: String?)

    private fun queryDates(context: Context, uri: Uri): Dates? = try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN, MediaStore.MediaColumns.DATA),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            Dates(c.getLong(0), if (c.isNull(1)) null else c.getLong(1), c.getString(2))
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron leer las fechas de $uri", e)
        null
    }

    /** Etiquetas EXIF que se conservan: fechas, cámara y ubicación. */
    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    private fun readExif(context: Context, uri: Uri): Map<String, String> = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            EXIF_TAGS.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()
        }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer el EXIF de $uri", e)
        emptyMap()
    }

    private fun writeExif(context: Context, uri: Uri, tags: Map<String, String>) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { fd ->
                val exif = ExifInterface(fd.fileDescriptor)
                tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
                // La imagen nueva ya está girada: no debe volver a girarse al abrirla.
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo copiar el EXIF a $uri", e)
        }
    }

    /**
     * Devuelve a la foto su fecha para que no cambie de sitio en la galería. Se pone un segundo
     * después de la original: con la fecha idéntica, la galería cree que la foto no ha cambiado
     * y sigue mostrando la miniatura antigua. Un segundo no altera el orden en la práctica.
     */
    private fun restoreDates(context: Context, uri: Uri, dates: Dates) {
        val modified = dates.modified + 1
        val fileRestored = dates.path?.let { path ->
            try {
                File(path).setLastModified(modified * 1000)
            } catch (e: Exception) {
                false
            }
        } ?: false
        // Que el sistema vuelva a leer la foto (tamaño nuevo, miniaturas).
        dates.path?.let { path ->
            val latch = java.util.concurrent.CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> latch.countDown() }
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_MODIFIED, modified)
            dates.taken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
        }
        val updated = try {
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore no aceptó las fechas de $uri", e)
            -1
        }
        Log.i(TAG, "Fechas restauradas: archivo=$fileRestored, mediastore=$updated ($modified, ${dates.taken})")
    }

    private const val TAG = "OriginalReplacer"

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
}
