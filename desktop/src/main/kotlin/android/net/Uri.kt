package android.net

import java.io.File
import java.net.URI

/** En Windows todas las fotos son archivos: sus URI son siempre "file:/C:/…". */
class Uri private constructor(private val text: String) {
    val scheme: String? get() = text.substringBefore(':', "").ifEmpty { null }
    val authority: String? get() = if (scheme == "content") text.substringAfter("://").substringBefore('/') else null
    val path: String? get() = text.substringAfter(':')
    val lastPathSegment: String? get() = text.trimEnd('/').substringAfterLast('/').ifEmpty { null }
    val pathSegments: List<String> get() = path.orEmpty().split('/').filter { it.isNotEmpty() }

    /** El archivo detrás de esta URI, o null si no es un archivo. */
    fun toFile(): File? = if (scheme == "file") File(URI(text)) else null

    override fun toString() = text

    override fun equals(other: Any?) = other is Uri && other.text == text

    override fun hashCode() = text.hashCode()

    companion object {
        fun parse(text: String) = Uri(text)

        fun fromFile(file: File) = Uri(file.toURI().toString())
    }
}
