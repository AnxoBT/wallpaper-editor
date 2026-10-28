package com.wallpaperfit.app.ui.wallpapers

import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.wallpaper.LockClockFont
import com.wallpaperfit.app.wallpaper.TapAction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.wallpaperfit.app.wallpaper.SourceSchedule
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.wallpaperfit.app.storage.OriginalReplacer
import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.WallpaperTarget
import com.wallpaperfit.app.storage.WallpaperSetter
import com.wallpaperfit.app.ui.DeviceScreen
import com.wallpaperfit.app.wallpaper.AutoChange
import com.wallpaperfit.app.wallpaper.ClockAlign
import com.wallpaperfit.app.wallpaper.ClockColorMode
import com.wallpaperfit.app.wallpaper.ClockConfig
import com.wallpaperfit.app.wallpaper.ClockFont
import com.wallpaperfit.app.wallpaper.ClockShadow
import com.wallpaperfit.app.wallpaper.ClockWidget
import com.wallpaperfit.app.wallpaper.ImageCatalog
import com.wallpaperfit.app.wallpaper.RotationOrder
import com.wallpaperfit.app.wallpaper.SlideshowWallpaperService
import com.wallpaperfit.app.wallpaper.WallpaperConfig
import com.wallpaperfit.app.wallpaper.WallpaperController
import com.wallpaperfit.app.wallpaper.WallpaperFit
import com.wallpaperfit.app.wallpaper.WallpaperPlace
import com.wallpaperfit.app.wallpaper.WallpaperSource
import com.wallpaperfit.app.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpapersViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WallpaperStore.get(application)

    val config = store.config
    val state = store.state
    val clock = store.clock
    val clockColors = store.clockColors
    val screenSize = DeviceScreen.size(application)

    private val _images = MutableStateFlow<List<String>>(emptyList())
    val images: StateFlow<List<String>> = _images.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            var opening = true
            store.config.map { it.sources }.distinctUntilChanged().collect { sources ->
                ImageCatalog.invalidate()
                _images.value = ImageCatalog.images(getApplication(), sources)
                // Al abrir la pantalla no ha cambiado nada: se respeta la foto puesta a mano.
                if (!opening) WallpaperController.followSources(getApplication())
                opening = false
            }
        }
        // Etiquetas cambiadas (en el selector o al importarlas): las fuentes de etiqueta se releen.
        viewModelScope.launch {
            PhotoTags.get(application).tags.drop(1).collect {
                val sources = store.config.value.sources
                if (sources.any { it.tag != null }) {
                    _images.value = ImageCatalog.reload(getApplication(), sources)
                    WallpaperController.followSources(getApplication())
                }
            }
        }
        // Fotos nuevas detectadas por el fondo dinámico: la lista de la pantalla se pone al día.
        viewModelScope.launch {
            ImageCatalog.version.drop(1).collect {
                _images.value = ImageCatalog.images(getApplication(), store.config.value.sources)
            }
        }
        refreshActive()
    }

    /** Se llama al volver a la pantalla (por ejemplo, tras el selector de fondos del sistema). */
    fun refreshActive() {
        _isActive.value = WallpaperController.isLiveWallpaperActive(getApplication())
    }

    /** Las fotos de las fuentes sin el filtro de etiquetas ("Ver todas" deja quitarlo). */
    suspend fun unfilteredImages(): List<String> = ImageCatalog.unfiltered(getApplication(), store.config.value.sources)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Al volver a la pantalla: la lista guardada al momento (se relee sola en segundo plano si es
     * antigua, y el vigilante de la galería ya avisa de fotos nuevas).
     */
    fun syncImages() {
        viewModelScope.launch { _images.value = ImageCatalog.images(getApplication(), store.config.value.sources) }
    }

    /** Vuelve a leer las carpetas ya (botón «Actualizar»); [announce]: avisar de cuántas fotos nuevas hay. */
    fun refreshImages(announce: Boolean = true) {
        if (_refreshing.value || store.config.value.sources.isEmpty()) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val before = _images.value.toSet()
                // La lista de la pantalla la pone al día el aviso de ImageCatalog.version.
                val found = ImageCatalog.reload(getApplication(), store.config.value.sources)
                if (announce) {
                    val added = found.count { it !in before }
                    val app = getApplication<Application>()
                    _messages.tryEmit(
                        if (added > 0) app.resources.getQuantityString(R.plurals.wp_new_photos, added, added)
                        else app.getString(R.string.wp_no_new_photos),
                    )
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Intent del selector del sistema con nuestro fondo dinámico ya elegido. */
    fun liveWallpaperIntent(): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(getApplication(), SlideshowWallpaperService::class.java),
        )

    // region Fotos

    /** Carpeta elegida con el selector del sistema (permiso persistente ya concedido). */
    fun addFolder(tree: Uri) {
        val name = folderName(tree) ?: getApplication<Application>().getString(R.string.wp_folder_unnamed)
        addSources(listOf(WallpaperSource(tree.toString(), isFolder = true, name = name)))
    }

    fun addPhotos(uris: List<Uri>) {
        addSources(uris.map { WallpaperSource(it.toString(), isFolder = false, name = displayName(it) ?: "") })
    }

    fun addTag(tag: String) {
        addSources(listOf(WallpaperSource.forTag(tag)))
    }

    /** Etiquetas que hay, la más usada primero, con cuántas fotos tiene cada una. */
    fun allTags(): List<Pair<String, Int>> = PhotoTags.get(getApplication()).all().filter { it.second > 0 }

    private fun addSources(new: List<WallpaperSource>) {
        store.updateConfig { config ->
            val known = config.sources.map { it.uri }.toSet()
            config.copy(sources = config.sources + new.filter { it.uri !in known })
        }
        // Primera foto: que el fondo tenga algo que enseñar ya.
        if (store.state.value.current == null) next()
    }

    fun removeSource(source: WallpaperSource) {
        store.updateConfig { it.copy(sources = it.sources - source) }
    }

    /** Foto cuya forma no es la de la pantalla (se recortaría o dejaría bandas). */
    data class Misaligned(val uri: String, val width: Int, val height: Int)

    private val _misaligned = MutableStateFlow<List<Misaligned>?>(null)

    /** Resultado de la última revisión (null = revisando o sin revisar). */
    val misaligned: StateFlow<List<Misaligned>?> = _misaligned.asStateFlow()

    /**
     * Busca las fotos del pase cuya proporción se aparta más de un 1 % de la de la pantalla. Solo
     * lee el tamaño de cada foto: no cambia ninguna.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun checkMisaligned(keepCurrent: Boolean = false) {
        // Al volver del editor se deja la lista anterior a la vista mientras se revisa de nuevo.
        if (!keepCurrent || _misaligned.value == null) _misaligned.value = null
        viewModelScope.launch {
            val app = getApplication<Application>()
            val (width, height) = WallpaperController.screenSize(app)
            val screen = width.toFloat() / height
            // Varias a la vez: con cientos de fotos, leerlas una a una tarda demasiado.
            val io = Dispatchers.IO.limitedParallelism(MISALIGNED_PARALLELISM)
            _misaligned.value = coroutineScope {
                _images.value.map { uri ->
                    async(io) {
                        val size = imageSize(app, Uri.parse(uri)) ?: return@async null
                        val ratio = size.first.toFloat() / size.second
                        if (kotlin.math.abs(ratio - screen) / screen > MISALIGNED_TOLERANCE) Misaligned(uri, size.first, size.second) else null
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    /** Ancho y alto tal y como se ve la foto (girada según su EXIF), sin decodificarla. */
    private fun imageSize(context: Context, uri: Uri): Pair<Int, Int>? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val rotated = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
                -> true
                else -> false
            }
        } ?: false
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            null
        } else if (rotated) {
            options.outHeight to options.outWidth
        } else {
            options.outWidth to options.outHeight
        }
    } catch (e: Exception) {
        null
    }

    fun setSourceSchedule(source: WallpaperSource, schedule: SourceSchedule) {
        store.updateConfig { config ->
            config.copy(sources = config.sources.map { if (it == source) it.copy(schedule = schedule) else it })
        }
        WallpaperController.followSchedule(getApplication())
    }

    private fun folderName(tree: Uri): String? = try {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        getApplication<Application>().contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun displayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    // endregion

    // region Cambiar de fondo

    fun next() = act { WallpaperController.next(getApplication()) }

    fun previous() = act {
        if (!WallpaperController.previous(getApplication())) message(R.string.wp_no_previous)
    }

    fun show(uri: String) = act { WallpaperController.show(getApplication(), uri) }

    /** Diálogo del sistema para confirmar que una foto va a la papelera (la UI lo lanza). */
    private val _deleteRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteRequests: SharedFlow<IntentSender> = _deleteRequests.asSharedFlow()

    /** Foto cuyo borrado espera la confirmación de Android. */
    private var pendingDelete: String? = null

    /**
     * Mover una foto a la papelera del móvil (se puede recuperar durante 30 días). Android pide
     * confirmación al usuario; el resultado llega a [onDeleteResult].
     */
    fun deleteImage(uri: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val media = withContext(Dispatchers.IO) { OriginalReplacer.resolve(app, Uri.parse(uri))?.uri }
            if (media == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                message(R.string.wp_delete_unsupported)
                return@launch
            }
            val sender = MediaStore.createTrashRequest(app.contentResolver, listOf(media), true).intentSender
            pendingDelete = uri
            _deleteRequests.tryEmit(sender)
        }
    }

    fun onDeleteResult(deleted: Boolean) {
        val uri = pendingDelete ?: return
        pendingDelete = null
        if (!deleted) return
        viewModelScope.launch {
            // Las fotos sueltas salen de la lista; si era la de fondo, se pasa a la siguiente (con
            // la lista ya releída, para que no vuelva a salir).
            store.updateConfig { config -> config.copy(sources = config.sources.filterNot { !it.isFolder && it.uri == uri }) }
            WallpaperController.onPhotosChanged()
            ImageCatalog.reload(getApplication(), store.config.value.sources)
            if (store.state.value.current == uri) WallpaperController.next(getApplication())
            message(R.string.wp_deleted)
        }
    }

    /** Pone una foto solo en la pantalla de bloqueo, como fondo fijo. */
    fun setLockOnly(uri: String) = act {
        val app = getApplication<Application>()
        val bitmap = withContext(Dispatchers.IO) {
            val (width, height) = WallpaperController.screenSize(app)
            WallpaperController.render(app, uri, width, height, store.config.value.fit)
        }
        WallpaperSetter.apply(app, bitmap, WallpaperTarget.LOCK)
        message(R.string.wp_lock_set)
    }

    private fun act(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                message(R.string.wp_error)
            } finally {
                _busy.value = false
            }
        }
    }

    // endregion

    // region Ajustes del pase

    fun setOrder(order: RotationOrder) = store.updateConfig { it.copy(order = order) }

    fun setAutoChange(value: AutoChange) = store.updateConfig { it.copy(autoChange = value) }

    fun setCustomMinutes(minutes: Int) = store.updateConfig {
        it.copy(customMinutes = minutes.coerceIn(WallpaperConfig.MIN_CUSTOM_MINUTES, WallpaperConfig.MAX_CUSTOM_MINUTES))
    }

    fun setDoubleTapAction(action: TapAction) = store.updateConfig { it.copy(doubleTapAction = action) }

    fun setTripleTapAction(action: TapAction) = store.updateConfig { it.copy(tripleTapAction = action) }

    fun toggleAutoPaused() = WallpaperController.toggleAutoChange(getApplication())

    fun setHistorySize(size: Int) = store.updateConfig {
        it.copy(historySize = size.coerceIn(WallpaperConfig.MIN_HISTORY, WallpaperConfig.MAX_HISTORY))
    }

    fun setFit(fit: WallpaperFit) = store.updateConfig { it.copy(fit = fit) }

    fun setPlace(place: WallpaperPlace) {
        store.updateConfig { it.copy(place = place) }
        // Al activar "también en bloqueo" se pone ya la foto actual.
        val current = store.state.value.current
        if (place == WallpaperPlace.HOME_AND_LOCK && current != null) setLockOnly(current)
    }

    // endregion

    // region Reloj

    fun requestClockWidget() {
        if (!ClockWidget.requestPin(getApplication())) message(R.string.wp_widget_manual)
    }

    fun setClockFont(font: ClockFont) = updateClock { it.copy(font = font) }

    fun setClockTimeSize(size: Float) = updateClock { it.copy(timeSize = size) }

    fun setClockDateSize(size: Float) = updateClock { it.copy(dateSize = size) }

    fun setClockShowDate(show: Boolean) = updateClock { it.copy(showDate = show) }

    fun setClock24h(use24h: Boolean) = updateClock { it.copy(use24h = use24h) }

    fun setClockAlign(align: ClockAlign) = updateClock { it.copy(align = align) }

    fun setClockColorMode(mode: ClockColorMode) = updateClock { it.copy(colorMode = mode) }

    fun setClockFixedColor(color: Int) = updateClock { it.copy(colorMode = ClockColorMode.FIXED, fixedColor = color) }

    fun setClockShadow(shadow: ClockShadow) = updateClock { it.copy(shadow = shadow) }

    fun setClockDatePosition(position: com.wallpaperfit.app.wallpaper.DatePosition) = updateClock { it.copy(datePosition = position) }

    fun setClockDateFormat(format: com.wallpaperfit.app.wallpaper.DateFormat) = updateClock { it.copy(dateFormat = format) }

    /** Todo como el reloj de la pantalla de bloqueo (formato de hora: el del sistema). */
    fun matchLockClock() {
        // Se vuelve a leer: puede haber cambiado el estilo del bloqueo desde la última vez.
        val detected = LockClockFont.lockStyle(getApplication(), refresh = true)
        updateClock { it.likeLockScreen(android.text.format.DateFormat.is24HourFormat(getApplication()), detected) }
    }

    private fun updateClock(change: (ClockConfig) -> ClockConfig) {
        store.updateClock(change)
        ClockWidget.updateAll(getApplication())
    }

    // endregion

    private fun message(resId: Int) {
        _messages.tryEmit(getApplication<Application>().getString(resId))
    }

    private companion object {
        /** Diferencia de proporción a partir de la cual una foto no encaja (1 %). */
        const val MISALIGNED_TOLERANCE = 0.01f

        /** Fotos que se leen a la vez al revisar. */
        const val MISALIGNED_PARALLELISM = 8
    }
}
