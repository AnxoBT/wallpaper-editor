package android.content

import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.wallpaperfit.app.desktop.PhotoLibrary
import com.wallpaperfit.app.desktop.Strings
import java.awt.Desktop
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class ActivityNotFoundException : RuntimeException()

class IntentSender

open class Intent(val action: String? = null, val data: Uri? = null) {
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
    }
}

/** La galería de Android, leída de las carpetas de fotos del PC ([PhotoLibrary]). */
class ContentResolver {

    fun openInputStream(uri: Uri): InputStream? = uri.toFile()?.inputStream()

    fun openOutputStream(uri: Uri): OutputStream? = uri.toFile()?.outputStream()

    fun getType(uri: Uri): String? = PhotoLibrary.mimeOf(uri.toString())

    /** Solo en Android 10+: aquí no se llega a usar. */
    fun loadThumbnail(uri: Uri, size: android.util.Size, signal: Any?): android.graphics.Bitmap = throw UnsupportedOperationException()

    fun takePersistableUriPermission(uri: Uri, flags: Int) = Unit

    fun registerContentObserver(uri: Uri, descendants: Boolean, observer: ContentObserver) = Unit

    fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? {
        val columns = projection.orEmpty()
        val text = uri.toString()
        val files = when {
            uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> {
                val bucket = if (selection?.contains(MediaStore.Images.Media.BUCKET_ID) == true) args?.firstOrNull() else null
                PhotoLibrary.photos().filter { bucket == null || PhotoLibrary.bucketOf(it) == bucket }
            }
            text.endsWith(DocumentsContract.CHILDREN_SUFFIX) ->
                Uri.parse(text.removeSuffix(DocumentsContract.CHILDREN_SUFFIX)).toFile()?.let(PhotoLibrary::photosIn).orEmpty()
            else -> uri.toFile()?.takeIf(File::exists)?.let(::listOf).orEmpty()
        }
        return Cursor(files.map { file -> columns.map { value(file, it) } })
    }

    private fun value(file: File, column: String): Any? = when (column) {
        MediaStore.Images.Media._ID -> PhotoLibrary.idOf(file)
        OpenableColumns.DISPLAY_NAME -> file.name
        MediaStore.Images.Media.BUCKET_ID -> PhotoLibrary.bucketOf(file)
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> file.parentFile?.name ?: file.path
        MediaStore.Images.Media.DATE_MODIFIED -> file.lastModified() / 1000
        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> file.path
        DocumentsContract.Document.COLUMN_MIME_TYPE -> PhotoLibrary.mimeOf(file.name)
        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
        else -> null
    }
}

object ContentUris {
    /** Las fotos se identifican por su ruta: el número solo existe para las consultas. */
    fun withAppendedId(collection: Uri, id: Long): Uri = Uri.fromFile(PhotoLibrary.fileOf(id))

    fun parseId(uri: Uri): Long = PhotoLibrary.idOf(uri.toFile() ?: throw NumberFormatException(uri.toString()))
}

open class Context {
    open val applicationContext: Context get() = this
    val contentResolver = ContentResolver()
    val resources = Resources()
    val packageName = "com.wallpaperfit.app"
    val packageManager = PackageManager()

    fun getString(id: Int): String = Strings.get(id)

    fun getString(id: Int, vararg args: Any?): String = Strings.get(id, *args)

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = DesktopPreferences.get(name)

    fun startActivity(intent: Intent) {
        intent.data?.let { Desktop.getDesktop().browse(URI(it.toString())) }
    }

    companion object {
        const val MODE_PRIVATE = 0
    }
}

class Resources {
    fun openRawResource(id: Int): InputStream = Strings.raw(id)
}

interface SharedPreferences {
    fun getString(key: String, default: String?): String?

    fun getInt(key: String, default: Int): Int

    fun getBoolean(key: String, default: Boolean): Boolean

    fun getLong(key: String, default: Long): Long

    fun contains(key: String): Boolean

    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor

        fun putInt(key: String, value: Int): Editor

        fun putBoolean(key: String, value: Boolean): Editor

        fun putLong(key: String, value: Long): Editor

        fun remove(key: String): Editor

        fun apply()

        fun commit(): Boolean
    }
}
