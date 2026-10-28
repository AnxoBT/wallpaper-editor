package com.wallpaperfit.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Últimas fotos abiertas en el editor (la más reciente primero), para volver a ellas desde el
 * inicio. Solo se guardan sus URIs en este móvil; las que ya no se pueden abrir (borradas o sin
 * permiso) se quitan solas al mostrar la lista.
 */
class RecentEdits private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    /** Apunta [uri] como la última editada e intenta conservar el permiso para leerla luego. */
    fun add(uri: Uri) {
        try {
            // Las fotos del selector del sistema solo se pueden leer mientras la app está abierta
            // salvo que se conserve el permiso (las de MediaStore o carpetas ya lo tienen).
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // No todas las URIs lo admiten; esas se verán mientras la app pueda leerlas.
        }
        save((listOf(uri.toString()) + _items.value.filter { it != uri.toString() }).take(MAX_ITEMS))
    }

    /** Quita de la lista las fotos que ya no se pueden abrir. */
    suspend fun prune() {
        val readable = withContext(Dispatchers.IO) { _items.value.filter(::canRead) }
        if (readable != _items.value) save(readable)
    }

    private fun canRead(uri: String): Boolean = try {
        context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { it.moveToFirst() } == true
    } catch (e: Exception) {
        false
    }

    private fun save(list: List<String>) {
        _items.value = list
        prefs.edit().putString(KEY_ITEMS, JSONArray(list).toString()).apply()
    }

    private fun read(): List<String> = try {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val PREFERENCES = "recent_edits"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 8

        @Volatile
        private var instance: RecentEdits? = null

        fun get(context: Context): RecentEdits =
            instance ?: synchronized(this) { instance ?: RecentEdits(context.applicationContext).also { instance = it } }
    }
}
