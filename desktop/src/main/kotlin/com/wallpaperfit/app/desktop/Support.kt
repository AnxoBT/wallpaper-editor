package com.wallpaperfit.app.desktop

import android.graphics.Bitmap
import android.content.DesktopPreferences
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.wallpaperfit.app.R
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.json.JSONArray
import org.w3c.dom.Element
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JFileChooser
import javax.xml.parsers.DocumentBuilderFactory

/** Carpetas de datos de la app: ajustes y etiquetas, y la imagen que se pone de fondo. */
object AppFiles {
    /** [we.data]: otra carpeta, para que las pruebas no toquen los datos reales. */
    private val root = System.getProperty("we.data")?.let(::File)
        ?: File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "WallpaperEditor")
    val preferences = File(root, "preferences").apply { mkdirs() }
    val wallpapers = File(root, "wallpapers").apply { mkdirs() }
}

/**
 * Textos de la app, de los mismos XML que en Android; encima, los de strings_desktop.xml (donde
 * el móvil dice "móvil"). Sin traducir un texto, sale en inglés, igual que en Android.
 */
object Strings {
    private const val FILES_BASE = "strings.xml,strings_features.xml,strings_settings.xml,strings_desktop.xml"

    /** "es", "en" o null para seguir el idioma de Windows. */
    @Volatile
    var language: String? = null
        set(value) {
            field = value
            load()
        }

    private var texts = emptyMap<String, String>()
    private var plurals = emptyMap<String, Map<String, String>>()

    init {
        load()
    }

    private fun load() {
        val useSpanish = (language ?: Locale.getDefault().language) == "es"
        val newTexts = HashMap<String, String>()
        val newPlurals = HashMap<String, Map<String, String>>()
        val folders = if (useSpanish) listOf("values", "values-es") else listOf("values")
        for (folder in folders) {
            for (file in FILES_BASE.split(',')) {
                val stream = javaClass.getResourceAsStream("/res/$folder/$file") ?: continue
                val root = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }.documentElement
                val children = root.childNodes
                for (i in 0 until children.length) {
                    val item = children.item(i) as? Element ?: continue
                    val name = item.getAttribute("name")
                    if (item.tagName == "string") {
                        newTexts[name] = unescape(item.textContent)
                    } else if (item.tagName == "plurals") {
                        val forms = item.getElementsByTagName("item")
                        newPlurals[name] = (0 until forms.length).associate {
                            val form = forms.item(it) as Element
                            form.getAttribute("quantity") to unescape(form.textContent)
                        }
                    }
                }
            }
        }
        texts = newTexts
        plurals = newPlurals
    }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        val trimmed = text.trim()
        while (i < trimmed.length) {
            val c = trimmed[i++]
            if (c == '\\' && i < trimmed.length) {
                when (val next = trimmed[i++]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(next)
                }
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    private fun name(id: Int) = R.names[id].substringAfter('/')

    fun get(id: Int): String = texts[name(id)] ?: name(id)

    fun get(id: Int, vararg args: Any?): String = if (args.isEmpty()) get(id) else get(id).format(*args)

    fun plural(id: Int, count: Int, vararg args: Any?): String {
        val forms = plurals[name(id)] ?: return name(id)
        val form = forms[if (count == 1) "one" else "other"] ?: forms["other"] ?: forms.values.first()
        return if (args.isEmpty()) form else form.format(*args)
    }

    /** Ruta del recurso de un dibujo. */
    fun path(id: Int) = "res/${R.names[id]}.xml"

    fun raw(id: Int): InputStream =
        javaClass.getResourceAsStream("/res/${R.names[id]}.txt") ?: throw IOException("No existe ${R.names[id]}")
}

/**
 * Lo que en Android es la galería: las fotos de las carpetas que elige el usuario (al principio,
 * la carpeta Imágenes de Windows), con sus subcarpetas. Cada carpeta con fotos es un álbum.
 */
object PhotoLibrary {
    private const val KEY_FOLDERS = "folders"
    private const val CACHE_MS = 3_000L
    private const val MAX_DEPTH = 8
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    private val prefs by lazy { DesktopPreferences.get("library") }
    private val files = ConcurrentHashMap<Long, File>()

    @Volatile
    private var cache: Pair<Long, List<File>>? = null

    fun folders(): List<File> = prefs.getString(KEY_FOLDERS, null)?.let { saved ->
        val array = JSONArray(saved)
        (0 until array.length()).map { File(array.getString(it)) }
    } ?: listOf(File(System.getProperty("user.home"), "Pictures"))

    /** Añade una carpeta, salvo que ya esté dentro de otra de la biblioteca. */
    fun addFolder(folder: File?) {
        val path = folder?.canonicalFile ?: return
        val current = folders()
        if (current.any { path.path == it.path || path.path.startsWith(it.path + File.separator) }) return
        prefs.edit().putString(KEY_FOLDERS, JSONArray((current + path).map { it.path }).toString()).apply()
        invalidate()
    }

    fun invalidate() {
        cache = null
    }

    /** Todas las fotos, de la más reciente a la más antigua. */
    fun photos(): List<File> {
        val now = System.currentTimeMillis()
        cache?.takeIf { now - it.first < CACHE_MS }?.let { return it.second }
        val found = ArrayList<File>()
        folders().forEach { collect(it, 0, found) }
        val sorted = found.map { it to it.lastModified() }.sortedByDescending { it.second }.map { it.first }
        cache = now to sorted
        return sorted
    }

    private fun collect(folder: File, depth: Int, into: MutableList<File>) {
        val children = folder.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                if (depth < MAX_DEPTH) collect(child, depth + 1, into)
            } else if (isImage(child)) {
                into += child
            }
        }
    }

    /** Fotos de una sola carpeta, sin subcarpetas. */
    fun photosIn(folder: File): List<File> =
        folder.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") && isImage(it) }

    private fun isImage(file: File) = file.extension.lowercase() in EXTENSIONS

    fun bucketOf(file: File): String = file.absoluteFile.parent.orEmpty()

    /** Número estable por ruta (FNV-1a de 64 bits, positivo). */
    fun idOf(file: File): Long {
        var hash = -0x340d631b7bdddcdbL
        file.absolutePath.forEach { hash = (hash xor it.code.toLong()) * 0x100000001b3L }
        val id = hash and Long.MAX_VALUE
        files[id] = file
        return id
    }

    fun fileOf(id: Long): File = files[id] ?: throw IllegalArgumentException("Foto desconocida: $id")

    fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}

/** Diálogos de archivos de Windows. */
object FileDialogs {
    private const val IMAGE_FILTER = "*.jpg;*.jpeg;*.png;*.webp;*.bmp;*.gif"

    /** Ventana de la app, para que los diálogos salgan encima de ella. */
    var window: Frame? = null

    private fun dialog(mode: Int, filter: String?): FileDialog =
        FileDialog(window, "", mode).apply { filter?.let { file = it } }

    fun openImage(): File? = open(IMAGE_FILTER)

    fun openImages(): List<File> {
        val dialog = dialog(FileDialog.LOAD, IMAGE_FILTER)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        return dialog.files.toList()
    }

    fun open(filter: String): File? = chosen(dialog(FileDialog.LOAD, filter))

    fun save(name: String): File? = chosen(dialog(FileDialog.SAVE, name))

    private fun chosen(dialog: FileDialog): File? {
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    fun chooseFolder(): File? {
        val chooser = JFileChooser(System.getProperty("user.home")).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}

/** Codifica un Bitmap con Skia. */
object ImageEncoder {
    fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val encoded = when (format) {
            Bitmap.CompressFormat.PNG -> EncodedImageFormat.PNG
            Bitmap.CompressFormat.JPEG -> EncodedImageFormat.JPEG
            else -> EncodedImageFormat.WEBP
        }
        val data = Image.makeFromBitmap(bitmap.skia).use { it.encodeToData(encoded, quality) }
            ?: throw IOException("No se pudo codificar la imagen")
        return data.bytes
    }
}

/** La llamada de Windows que cambia el fondo del escritorio. */
object WindowsWallpaper {
    private const val SPI_SETDESKWALLPAPER = 0x14
    private const val SPIF_UPDATEINIFILE_SENDCHANGE = 3

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun SystemParametersInfoW(action: Int, param: Int, value: WString, flags: Int): Boolean
    }

    private val user32 by lazy { Native.load("user32", User32::class.java) }

    fun set(file: File) {
        if (!user32.SystemParametersInfoW(SPI_SETDESKWALLPAPER, 0, WString(file.absolutePath), SPIF_UPDATEINIFILE_SENDCHANGE)) {
            throw IOException("Windows no aceptó el fondo")
        }
    }
}
