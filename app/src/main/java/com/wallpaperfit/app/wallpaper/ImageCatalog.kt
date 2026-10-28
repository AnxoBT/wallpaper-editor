package com.wallpaperfit.app.wallpaper

import com.wallpaperfit.app.storage.PhotoTags
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Lista de imágenes de las fuentes: cada carpeta se lee entera (así entran solas las fotos nuevas
 * que se guarden en ella), las fotos sueltas se usan tal cual y las etiquetas filtran lo anterior
 * (ver [selectImages]). Leer una carpeta de cientos de
 * fotos tarda más de medio segundo, así que la lista se guarda y se renueva en segundo plano.
 */
object ImageCatalog {

    private class Cached(
        val sources: List<WallpaperSource>,
        /** Fotos de cada fuente, en el mismo orden que [sources]. */
        val bySource: List<List<String>>,
        /** Nombres de archivo con cada etiqueta de las fuentes, y nombre de cada foto de carpeta. */
        val tagged: Map<String, Set<String>>,
        val names: Map<String, String>,
        val at: Long,
    ) {
        val images: List<String> = selectImages(sources, bySource, tagged, names, sources.toSet())
    }

    /**
     * Fotos del pase. Las etiquetas filtran: con carpetas o fotos elegidas, solo pasan las que tienen
     * todas las etiquetas [active]; sin ellas, salen las de la galería que tengan todas.
     * Si una carpeta o etiqueta está fuera de horario, no cuenta: fuera de horario, una etiqueta no
     * filtra y las carpetas siguen limitando la búsqueda aunque ninguna toque.
     */
    internal fun selectImages(
        sources: List<WallpaperSource>,
        bySource: List<List<String>>,
        tagged: Map<String, Set<String>>,
        names: Map<String, String>,
        active: Set<WallpaperSource>,
    ): List<String> {
        fun photos(picked: List<Int>) = picked.flatMap { bySource[it] }.distinct()
        val tagIndexes = sources.indices.filter { sources[it].tag != null && sources[it] in active }
        val plainIndexes = sources.indices.filter { sources[it].tag == null }
        val activePlain = plainIndexes.filter { sources[it] in active }.ifEmpty { plainIndexes }
        if (activePlain.isEmpty()) {
            val first = tagIndexes.firstOrNull() ?: return emptyList()
            return tagIndexes.drop(1).fold(bySource[first].distinct()) { kept, index -> kept.filter { it in bySource[index] } }
        }
        if (tagIndexes.isEmpty()) return photos(activePlain)
        val required = tagIndexes.map { tagged[sources[it].tag].orEmpty() }
        return photos(activePlain).filter { photo -> required.all { names[photo] in it } }
    }

    @Volatile
    private var cache: Cached? = null

    suspend fun images(context: Context, sources: List<WallpaperSource>): List<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cache?.takeIf { it.sources == sources }
        if (cached != null) {
            // Se responde ya con la lista guardada (el cambio de fondo no espera a leer la
            // carpeta) y, si tiene más de un minuto, se relee en segundo plano.
            if (now - cached.at >= CACHE_MS) refreshLater(context.applicationContext, sources)
            return@withContext cached.images
        }
        load(context, sources)
    }

    private val refresher = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var refreshing = false

    private fun refreshLater(context: Context, sources: List<WallpaperSource>) {
        if (refreshing) return
        refreshing = true
        refresher.launch {
            try {
                load(context, sources)
            } finally {
                refreshing = false
            }
        }
    }

    private fun load(context: Context, sources: List<WallpaperSource>): List<String> {
        val tags = PhotoTags.get(context)
        val bySource = sources.map { source ->
            val tag = source.tag
            when {
                source.isFolder -> folderImages(context, Uri.parse(source.uri))
                tag != null -> tags.queryNamed(tags.withTag(tag)).map { it.toString() }
                else -> listOf(source.uri)
            }
        }
        val filtering = sources.any { it.tag != null } && sources.any { it.tag == null }
        val tagged = sources.mapNotNull { it.tag }.associateWith { tags.withTag(it) }
        val names = if (filtering) tags.queryNames(sources.indices.filter { sources[it].tag == null }.flatMap { bySource[it] }) else emptyMap()
        val previous = cache
        val loaded = Cached(sources, bySource, tagged, names, System.currentTimeMillis())
        cache = loaded
        if (previous != null && previous.images != loaded.images) _version.value++
        return loaded.images
    }

    /**
     * Fotos que tocan ahora en el pase según el horario de cada fuente (si ninguna toca, todas).
     * Usa la misma lista guardada que [images].
     */
    suspend fun activeImages(context: Context, config: WallpaperConfig): List<String> {
        val all = images(context, config.sources)
        val loaded = cache?.takeIf { it.sources == config.sources } ?: return all
        val active = config.activeSources().toSet()
        return selectImages(loaded.sources, loaded.bySource, loaded.tagged, loaded.names, active).ifEmpty { all }
    }

    fun invalidate() {
        cache = null
    }

    /**
     * Las fotos de las carpetas y fotos elegidas sin aplicar el filtro de etiquetas, para poder
     * verlas todas. Vacía si no hay ninguna (solo etiquetas: no hay nada que filtrar).
     */
    suspend fun unfiltered(context: Context, sources: List<WallpaperSource>): List<String> {
        images(context, sources)
        val loaded = cache?.takeIf { it.sources == sources } ?: return emptyList()
        return selectImages(loaded.sources, loaded.bySource, loaded.tagged, loaded.names, loaded.sources.filter { it.tag == null }.toSet())
    }

    /** Vuelve a leer las carpetas ya (botón «Actualizar»). */
    suspend fun reload(context: Context, sources: List<WallpaperSource>): List<String> =
        withContext(Dispatchers.IO) { load(context, sources) }

    private val _version = MutableStateFlow(0)

    /** Sube cada vez que la lista cambia (fotos nuevas o borradas), para refrescar la pantalla. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Vigila la galería mientras el fondo dinámico está puesto: cuando se guarda o se borra una
     * foto, las carpetas se releen unos segundos después (una vez aunque lleguen muchos avisos).
     * Solo despierta a la app cuando de verdad cambia la galería: no gasta nada en reposo.
     */
    fun watch(context: Context, sources: () -> List<WallpaperSource>): ContentObserver {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val reload = Runnable { refreshLater(app, sources()) }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(reload)
                handler.postDelayed(reload, WATCH_DELAY_MS)
            }
        }
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        return observer
    }

    /** Imágenes de una carpeta elegida con el selector del sistema, de la más nueva a la más antigua. */
    private fun folderImages(context: Context, tree: Uri): List<String> = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, Long>>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(1) ?: continue
                if (!mime.startsWith("image/")) continue
                // Ocultos: la papelera de Android deja las fotos borradas en la misma carpeta como
                // ".trashed-…" durante 30 días, y las que se están guardando como ".pending-…".
                if (cursor.getString(3)?.startsWith(".") == true) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                found += uri.toString() to cursor.getLong(2)
            }
        }
        found.sortedByDescending { it.second }.map { it.first }
    } catch (e: Exception) {
        // Carpeta borrada o permiso retirado: se ignora sin romper el resto.
        Log.w("ImageCatalog", "No se puede leer la carpeta $tree", e)
        emptyList()
    }

    private const val CACHE_MS = 60_000L
    private const val WATCH_DELAY_MS = 3_000L
}
