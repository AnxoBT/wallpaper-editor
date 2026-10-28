package android.provider

import android.net.Uri
import java.io.File

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
}

object MediaStore {
    const val AUTHORITY = "media"
    const val VOLUME_EXTERNAL = "external"

    object Images {
        object Media {
            const val _ID = "_id"
            const val DISPLAY_NAME = OpenableColumns.DISPLAY_NAME
            const val BUCKET_ID = "bucket_id"
            const val BUCKET_DISPLAY_NAME = "bucket_display_name"
            const val DATE_MODIFIED = "date_modified"
            const val RELATIVE_PATH = "relative_path"
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/images/media")

            fun getContentUri(volume: String): Uri = EXTERNAL_CONTENT_URI
        }
    }
}

/**
 * Las carpetas de Windows, con la forma de las carpetas de Android: la "ruta" del documento lleva
 * el prefijo "primary:", que es como EditorViewModel sabe en qué carpeta guardar las copias.
 */
object DocumentsContract {
    object Document {
        const val COLUMN_DOCUMENT_ID = "document_id"
        const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_LAST_MODIFIED = "last_modified"
        const val COLUMN_DISPLAY_NAME = OpenableColumns.DISPLAY_NAME
    }

    internal const val CHILDREN_SUFFIX = "?children"

    fun isDocumentUri(context: android.content.Context, uri: Uri): Boolean = uri.toFile() != null

    fun getDocumentId(uri: Uri): String = "primary:" + uri.toFile()!!.path.replace('\\', '/')

    fun getTreeDocumentId(tree: Uri): String = tree.toString()

    fun buildChildDocumentsUriUsingTree(tree: Uri, documentId: String): Uri = Uri.parse(tree.toString() + CHILDREN_SUFFIX)

    fun buildDocumentUriUsingTree(tree: Uri, documentId: String?): Uri = Uri.fromFile(File(documentId.orEmpty()))
}
