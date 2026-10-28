package com.wallpaperfit.app.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Versión de Windows: copia las fotos con una etiqueta a una carpeta nueva dentro de Imágenes
 * (por ejemplo "Imágenes\Favoritas"), con su nombre y su fecha. Las originales no se tocan.
 */
object TagFolderCopy {

    data class Result(val copied: Int, val skipped: Int, val failed: Int)

    fun folderFor(tag: String): String = "Pictures/${PhotoTags.folderName(tag)}"

    suspend fun copy(context: Context, tags: PhotoTags, tag: String): Result = withContext(Dispatchers.IO) {
        val folder = File(System.getProperty("user.home"), folderFor(tag))
        folder.mkdirs()
        var copied = 0
        var skipped = 0
        var failed = 0
        for (photo in tags.photosMatching(TagFilter(included = setOf(tag)), "")) {
            val file = photo.toFile()?.takeIf(File::isFile)
            if (file == null) {
                failed++
                continue
            }
            val target = File(folder, file.name)
            if (target.exists() || file.parentFile?.canonicalFile == folder.canonicalFile) {
                skipped++
                continue
            }
            try {
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
                copied++
            } catch (e: Exception) {
                failed++
            }
        }
        Result(copied, skipped, failed)
    }
}
