package android.content

import com.wallpaperfit.app.desktop.AppFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * SharedPreferences en un archivo por nombre, en la carpeta de datos de la app. Se escribe al
 * instante y de forma atómica (primero a un temporal): las etiquetas no se pueden perder a medias.
 */
class DesktopPreferences private constructor(private val file: File) : SharedPreferences {

    private val values = Properties().apply { if (file.exists()) file.inputStream().use(::load) }

    @Synchronized
    override fun getString(key: String, default: String?): String? = values.getProperty(key) ?: default

    override fun getInt(key: String, default: Int) = getString(key, null)?.toIntOrNull() ?: default

    override fun getBoolean(key: String, default: Boolean) = getString(key, null)?.toBooleanStrictOrNull() ?: default

    override fun getLong(key: String, default: Long) = getString(key, null)?.toLongOrNull() ?: default

    @Synchronized
    override fun contains(key: String) = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = ArrayList<() -> Unit>()

        override fun putString(key: String, value: String?) = apply {
            changes += { if (value == null) values.remove(key) else values.setProperty(key, value) }
        }

        override fun putInt(key: String, value: Int) = putString(key, value.toString())

        override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

        override fun putLong(key: String, value: Long) = putString(key, value.toString())

        override fun remove(key: String) = putString(key, null)

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            synchronized(this@DesktopPreferences) {
                changes.forEach { it() }
                val temporary = File(file.path + ".tmp")
                temporary.outputStream().use { values.store(it, null) }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
            return true
        }
    }

    companion object {
        private val open = ConcurrentHashMap<String, DesktopPreferences>()

        fun get(name: String): SharedPreferences = open.getOrPut(name) { DesktopPreferences(File(AppFiles.preferences, "$name.properties")) }
    }
}
