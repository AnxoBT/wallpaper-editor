package com.wallpaperfit.app.storage

import com.wallpaperfit.app.R
import android.provider.DocumentsContract
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer

/**
 * Etiquetas de las fotos ("ghibli", "series", "one piece"…), para buscarlas en el selector y
 * usarlas como fuente del fondo dinámico. Se guardan en la app, sin tocar las fotos, por nombre de
 * archivo: el nombre no cambia al reinstalar ni al pasar las fotos a otro móvil, así que entran en
 * la copia de seguridad y se pueden exportar e importar como JSON ({"foto.jpg": ["etiqueta"]}).
 */
class PhotoTags private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val _tags = MutableStateFlow(read())

    /** Etiquetas de cada foto, por nombre de archivo. */
    val tags: StateFlow<Map<String, Set<String>>> = _tags.asStateFlow()

    private val _created = MutableStateFlow(readCreated())

    /** Etiquetas creadas a mano: existen aunque aún no las tenga ninguna foto. */
    val created: StateFlow<Set<String>> = _created.asStateFlow()

    fun of(name: String): Set<String> = _tags.value[name].orEmpty()

    /** Todas las etiquetas con cuántas fotos tiene cada una, la más usada primero (las creadas sin fotos, con 0). */
    fun all(): List<Pair<String, Int>> {
        val used = counts(_tags.value.values)
        val known = used.mapTo(HashSet()) { it.first }
        return used + _created.value.filter { it !in known }.sorted().map { it to 0 }
    }

    /** Crea una etiqueta sin fotos. Si ya hay una igual (sin mirar mayúsculas ni tildes), no hace nada. */
    @Synchronized
    fun create(tag: String) {
        val clean = clean(tag)
        val wanted = fold(clean)
        if (clean.isEmpty() || all().any { fold(it.first) == wanted }) return
        saveCreated(_created.value + clean)
    }

    @Synchronized
    fun set(name: String, tags: Set<String>) {
        val clean = tags.map(::clean).filter { it.isNotEmpty() }.toSortedSet()
        save(if (clean.isEmpty()) _tags.value - name else _tags.value + (name to clean))
    }

    /** Etiqueta de las favoritas ("favoritas" o "favorites", según el idioma de la app). */
    val favoriteTag: String get() = clean(context.getString(R.string.tag_favorites))

    /** Cambia el nombre de una etiqueta en todas sus fotos (si ya existe [new], se juntan). */
    @Synchronized
    fun rename(old: String, new: String) {
        val clean = clean(new)
        if (clean.isEmpty() || clean == old) return
        if (old in _created.value) saveCreated(_created.value - old + clean)
        save(_tags.value.mapValues { (_, set) -> if (old in set) (set - old + clean).toSortedSet() else set })
    }

    /** Quita una etiqueta de todas las fotos. */
    @Synchronized
    fun delete(tag: String) {
        if (tag in _created.value) saveCreated(_created.value - tag)
        save(_tags.value.mapValues { (_, set) -> set - tag }.filterValues { it.isNotEmpty() })
    }

    /** La copia guardada de una foto se queda con sus etiquetas. */
    @Synchronized
    fun copy(from: String, to: String) {
        val tags = of(from)
        if (tags.isNotEmpty()) save(_tags.value + (to to (of(to) + tags).toSortedSet()))
    }

    /**
     * Fotos sin etiquetas de las carpetas donde ya hay alguna etiquetada (las nuevas que faltan
     * por etiquetar), de la más reciente a la más antigua.
     */
    suspend fun untagged(): List<Uri> = withContext(Dispatchers.IO) {
        val collection = MediaCollections.images
        val rows = ArrayList<Triple<Uri, String, String?>>()
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    rows += Triple(ContentUris.withAppendedId(collection, cursor.getLong(0)), name, cursor.getString(2))
                }
            }
        } catch (e: Exception) {
            // Sin permiso de fotos no hay nada que buscar.
        }
        val tagged = _tags.value
        val buckets = rows.filter { it.second in tagged }.mapTo(HashSet()) { it.third }
        rows.filter { it.third in buckets && it.second !in tagged }.map { it.first }
    }

    /** Fotos con la etiqueta exacta [tag] (fuente "etiqueta" del fondo dinámico). */
    fun withTag(tag: String): Set<String> = _tags.value.filterValues { tag in it }.keys

    /** Añade las etiquetas de un JSON exportado; las fotos que ya tenían etiquetas las sustituyen. Devuelve cuántas fotos. */
    @Synchronized
    fun import(input: InputStream): Int {
        val json = JSONObject(input.bufferedReader().use { it.readText() })
        readTags(json.optJSONArray(JSON_CREATED)).forEach(::create)
        val imported = json.keys().asSequence().filter { it != JSON_CREATED }.associateWith { name ->
            val array = json.optJSONArray(name) ?: JSONArray()
            (0 until array.length()).map { clean(array.getString(it)) }.filter { it.isNotEmpty() }.toSortedSet()
        }.filterValues { it.isNotEmpty() }
        save(_tags.value + imported)
        return imported.size
    }

    fun export(output: OutputStream) {
        output.bufferedWriter().use {
            val json = toJson(_tags.value)
            if (_created.value.isNotEmpty()) json.put(JSON_CREATED, JSONArray(_created.value.toList()))
            it.write(json.toString(2))
        }
    }

    private fun saveCreated(value: Set<String>) {
        _created.value = value
        prefs.edit().putString(KEY_CREATED, JSONArray(value.toList()).toString()).apply()
    }

    private fun readCreated(): Set<String> = try {
        readTags(JSONArray(prefs.getString(KEY_CREATED, "[]")))
    } catch (e: Exception) {
        emptySet()
    }

    private fun readTags(array: JSONArray?): Set<String> =
        if (array == null) emptySet() else (0 until array.length()).mapTo(sortedSetOf()) { array.getString(it) }

    private fun save(value: Map<String, Set<String>>) {
        _tags.value = value
        prefs.edit().putString(KEY_TAGS, toJson(value).toString()).apply()
    }

    private fun toJson(value: Map<String, Set<String>>) = JSONObject().apply {
        value.toSortedMap().forEach { (name, tags) -> put(name, JSONArray(tags.toList())) }
    }

    private fun read(): Map<String, Set<String>> = try {
        val json = JSONObject(prefs.getString(KEY_TAGS, "{}") ?: "{}")
        json.keys().asSequence().associateWith { name ->
            val array = json.getJSONArray(name)
            (0 until array.length()).map { array.getString(it) }.toSortedSet() as Set<String>
        }
    } catch (e: Exception) {
        emptyMap()
    }

    /**
     * Nombre de archivo de cada foto del pase. El de las fotos de carpetas sale del propio
     * documento ("primary:Pictures/Fondos/foto.jpg") sin consultar nada; el resto se pregunta.
     */
    suspend fun namesOf(uris: List<String>): Map<String, String> = withContext(Dispatchers.IO) { queryNames(uris) }

    /** Como [namesOf], en el hilo que llama (que no debe ser el principal). */
    fun queryNames(uris: List<String>): Map<String, String> =
        uris.mapNotNull { uri ->
            val parsed = Uri.parse(uri)
            // Solo los de carpetas llevan la ruta en el identificador (los del selector, "image:123").
            val name = if (parsed.authority == EXTERNAL_STORAGE_DOCUMENTS) {
                DocumentsContract.getDocumentId(parsed).substringAfterLast('/').substringAfterLast(':')
            } else {
                queryName(parsed)
            }
            name?.let { uri to it }
        }.toMap()

    /** Nombre de archivo de una foto (el de MediaStore o el del documento). */
    suspend fun nameOf(uri: Uri): String? = withContext(Dispatchers.IO) { queryName(uri) }

    private fun queryName(uri: Uri): String? =
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Fotos de la galería cuyos nombres están en [names], de la más reciente a la más antigua.
     * Si hay varias con el mismo nombre (copias), salen todas.
     */
    suspend fun photosNamed(names: Set<String>): List<Uri> = withContext(Dispatchers.IO) { queryNamed(names) }

    /** Como [photosNamed], en el hilo que llama (que no debe ser el principal). */
    fun queryNamed(names: Set<String>): List<Uri> {
        if (names.isEmpty()) return emptyList()
        val collection = MediaCollections.images
        val found = ArrayList<Uri>()
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) in names) found += ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }
        } catch (e: Exception) {
            // Sin permiso de fotos no hay nada que buscar.
        }
        return found
    }

    companion object {
        private const val PREFERENCES = "photo_tags"
        private const val KEY_TAGS = "tags"
        private const val KEY_CREATED = "created"
        private const val EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents"

        /** Clave de las etiquetas sin fotos en el JSON exportado (ninguna foto se llama así). */
        private const val JSON_CREATED = "#created"

        /** Etiqueta guardada: en minúsculas y sin espacios de más. */
        fun clean(tag: String): String = tag.trim().replace(Regex("\\s+"), " ").lowercase()

        /**
         * Filtro de varias etiquetas a la vez: la foto tiene todas las de [selected] y, si hay
         * texto, alguna etiqueta que lo contiene.
         */
        fun matches(photoTags: Set<String>, selected: Set<String>, query: String): Boolean {
            if (!photoTags.containsAll(selected)) return false
            val wanted = fold(query)
            return wanted.isEmpty() || photoTags.any { fold(it).contains(wanted) }
        }

        /** Etiquetas de unas fotos con cuántas tiene cada una, la más usada primero. */
        fun counts(sets: Collection<Set<String>>): List<Pair<String, Int>> = sets.flatten()
            .groupingBy { it }.eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

        /**
         * Etiquetas que ofrecer al filtrar: las marcadas primero y siempre, aunque ya no quede
         * ninguna foto con ellas (si no, no habría forma de desmarcarlas).
         */
        fun selectedFirst(counts: List<Pair<String, Int>>, selected: Set<String>): List<Pair<String, Int>> {
            val byTag = counts.toMap()
            return selected.sorted().map { it to (byTag[it] ?: 0) } + counts.filter { it.first !in selected }
        }

        /** Para comparar sin mayúsculas ni tildes ("Animación" = "animacion"). */
        fun fold(text: String): String =
            Normalizer.normalize(clean(text), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        @Volatile
        private var instance: PhotoTags? = null

        fun get(context: Context): PhotoTags =
            instance ?: synchronized(this) { instance ?: PhotoTags(context.applicationContext).also { instance = it } }
    }
}
