package com.wallpaperfit.app.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.wallpaperfit.app.model.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda imágenes en la galería pública (Imágenes/WallpaperFit) a través de MediaStore. */
object GallerySaver {

    private const val ALBUM = "Wallpaper Editor"

    /**
     * Guarda en [folder] (carpeta elegida por el usuario con el selector del sistema) o, si es
     * null, en la carpeta predeterminada de la galería.
     */
    suspend fun save(
        context: Context,
        bitmap: Bitmap,
        format: ExportFormat,
        folder: Uri?,
        /** Carpeta de la galería ("Pictures/…/") donde guardar si no hay [folder] elegida. */
        relativePath: String? = null,
    ): Uri = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "WallpaperEditor_${bitmap.width}x${bitmap.height}_$timestamp.${format.extension}"
        if (folder != null) {
            saveToFolder(context, bitmap, format, displayName, folder)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, bitmap, format, displayName, relativePath ?: "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        } else {
            saveLegacy(context, bitmap, format, displayName)
        }
    }

    /** Android 10+: sin permisos; IS_PENDING oculta el archivo hasta que está completo. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(context: Context, bitmap: Bitmap, format: ExportFormat, displayName: String, relativePath: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore no creó la entrada")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir $uri")
            stream.use { writeBitmap(bitmap, format, it) }
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Android 8.0 - 9: requiere WRITE_EXTERNAL_STORAGE (solicitado en tiempo de ejecución). */
    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, format: ExportFormat, displayName: String): Uri {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("No se pudo crear ${directory.absolutePath}")
        val file = File(directory, displayName)
        try {
            FileOutputStream(file).use { writeBitmap(bitmap, format, it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(file)
    }

    /** Carpeta elegida con el selector del sistema (Storage Access Framework). */
    private fun saveToFolder(context: Context, bitmap: Bitmap, format: ExportFormat, displayName: String, folder: Uri): Uri {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val document = DocumentsContract.createDocument(resolver, parent, format.mimeType, displayName)
            ?: throw FolderNotWritableException()
        try {
            val stream = resolver.openOutputStream(document) ?: throw FolderNotWritableException()
            stream.use { writeBitmap(bitmap, format, it) }
            return document
        } catch (e: Exception) {
            DocumentsContract.deleteDocument(resolver, document)
            throw e
        }
    }

    /** Nombre visible de una carpeta elegida con el selector del sistema, o null si ya no hay acceso. */
    fun folderName(context: Context, folder: Uri): String? = try {
        val document = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    class FolderNotWritableException : IOException("No se puede escribir en la carpeta elegida")

    private fun writeBitmap(bitmap: Bitmap, format: ExportFormat, stream: OutputStream) {
        if (!bitmap.compress(format.compressFormat, format.quality, stream)) {
            throw IOException("Bitmap.compress falló")
        }
        stream.flush()
    }
}
