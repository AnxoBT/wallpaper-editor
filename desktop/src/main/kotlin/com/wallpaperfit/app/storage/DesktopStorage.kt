package com.wallpaperfit.app.storage

import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import com.wallpaperfit.app.desktop.AppFiles
import com.wallpaperfit.app.desktop.ImageEncoder
import com.wallpaperfit.app.desktop.PhotoLibrary
import com.wallpaperfit.app.desktop.WindowsWallpaper
import com.wallpaperfit.app.model.ExportFormat
import com.wallpaperfit.app.model.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda copias: en la carpeta elegida, junto a la original o, si no, en Imágenes\Wallpaper Editor. */
object GallerySaver {
    private const val ALBUM = "Wallpaper Editor"

    class FolderNotWritableException : Exception()

    suspend fun save(context: Context, bitmap: Bitmap, format: ExportFormat, folder: Uri?, relativePath: String? = null): Uri =
        withContext(Dispatchers.IO) {
            val target = folder?.toFile()
                ?: relativePath?.let(::File)
                ?: File(File(System.getProperty("user.home"), "Pictures"), ALBUM)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(target, "WallpaperEditor_${bitmap.width}x${bitmap.height}_$timestamp.${format.extension}")
            try {
                target.mkdirs()
                file.writeBytes(ImageEncoder.encode(bitmap, format.compressFormat, format.quality))
            } catch (e: IOException) {
                throw FolderNotWritableException()
            }
            PhotoLibrary.invalidate()
            Uri.fromFile(file)
        }

    fun folderName(context: Context, folder: Uri): String? = folder.toFile()?.takeIf(File::isDirectory)?.let { it.name.ifEmpty { it.path } }
}

/** Sobrescribe la foto original conservando su nombre y sus fechas (la carpeta sigue en el mismo orden). */
object OriginalReplacer {
    class Target(val uri: Uri, val displayName: String, val mimeType: String)

    fun resolve(context: Context, picked: Uri): Target? {
        val file = picked.toFile()?.takeIf(File::isFile) ?: return null
        return Target(picked, file.name, PhotoLibrary.mimeOf(file.name))
    }

    /** Windows no pide permiso para escribir en las fotos del usuario. */
    fun writeRequest(context: Context, target: Target): IntentSender? = null

    suspend fun write(context: Context, target: Target, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = target.uri.toFile() ?: throw IOException("La foto no es un archivo")
        val format = when (target.mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val view = Files.getFileAttributeView(file.toPath(), BasicFileAttributeView::class.java)
        val dates = view.readAttributes()
        // Primero a un temporal en la misma carpeta: un fallo a medias no puede estropear la foto.
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        try {
            temporary.writeBytes(ImageEncoder.encode(bitmap, format, 100))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AccessDeniedException) {
            temporary.delete()
            throw SecurityException(e)
        } finally {
            temporary.delete()
        }
        Files.getFileAttributeView(file.toPath(), BasicFileAttributeView::class.java)
            .setTimes(dates.lastModifiedTime(), FileTime.fromMillis(System.currentTimeMillis()), dates.creationTime())
        PhotoLibrary.invalidate()
    }
}

/** Muestra la foto en el Explorador de archivos, ya seleccionada en su carpeta. */
object OpenInGallery {
    suspend fun open(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = uri.toFile()?.takeIf(File::exists) ?: return@withContext false
        try {
            ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
            true
        } catch (e: IOException) {
            false
        }
    }
}

/** Pone la imagen de fondo del escritorio (la pantalla de bloqueo no se ofrece en Windows). */
object WallpaperSetter {
    suspend fun apply(context: Context, bitmap: Bitmap, target: WallpaperTarget) = withContext(Dispatchers.IO) {
        // Un nombre nuevo cada vez: Windows guarda en caché la imagen por su ruta.
        val file = File(AppFiles.wallpapers, "wallpaper_${System.currentTimeMillis()}.jpg")
        file.writeBytes(ImageEncoder.encode(bitmap, Bitmap.CompressFormat.JPEG, 100))
        WindowsWallpaper.set(file)
        AppFiles.wallpapers.listFiles()?.filter { it != file }?.forEach { it.delete() }
    }
}
