package com.wallpaperfit.app.wallpaper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.wallpaperfit.app.ui.DeviceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Acciones sobre el pase de fondos, compartidas por los gestos del fondo dinámico y los botones
 * de la app: cambiar al siguiente / anterior o a una foto concreta.
 *
 * Para que el cambio sea instantáneo, tras cada cambio se preparan ya a tamaño de pantalla el
 * fondo que saldría con "siguiente" y el de "anterior" (caché de pocas imágenes, solo en
 * memoria). El trabajo extra (pantalla de bloqueo, colores del reloj) se hace después, sin
 * retrasar el cambio. No hay nada en marcha mientras no se cambia de fondo.
 */
object WallpaperController {

    private val mutex = Mutex()
    private val random = Random(System.nanoTime())
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var followUp: Job? = null

    /** Tamaño real de la superficie del fondo (lo fija el fondo dinámico al dibujarse). */
    @Volatile
    var surfaceWidth = 0

    @Volatile
    var surfaceHeight = 0

    /** Fotos ya preparadas a tamaño de pantalla: la actual, la siguiente y las anteriores. */
    private val frames = object : LruCache<String, Bitmap>(CACHE_FRAMES) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    private val _revision = MutableStateFlow(0)

    /**
     * Sube cuando la app cambia fotos en el disco (reemplazar una original desde el editor,
     * borrar): el fondo dinámico vuelve a cargar la actual en vez de seguir con la copia en memoria.
     */
    val revision: StateFlow<Int> = _revision

    /**
     * Si la foto actual es de una fuente que ya no toca (por ejemplo, ha pasado de día a noche),
     * se cambia a una que sí. Lo llama el fondo dinámico al volver a verse.
     */
    fun followSchedule(context: Context) {
        val app = context.applicationContext
        val store = WallpaperStore.get(app)
        val config = store.config.value
        if (config.sources.none { it.schedule != SourceSchedule.ALWAYS }) return
        followSources(app)
    }

    /**
     * Si la foto actual ya no entra en el pase, se cambia a una que sí. Lo llama la pantalla de
     * Fondos cuando cambian las fuentes o las etiquetas: una foto puesta a mano fuera del filtro
     * sigue de fondo hasta entonces (o hasta el siguiente cambio).
     */
    fun followSources(context: Context) {
        val app = context.applicationContext
        val store = WallpaperStore.get(app)
        background.launch {
            val active = ImageCatalog.activeImages(app, store.config.value)
            val current = store.state.value.current
            if (active.isNotEmpty() && current !in active) next(app)
        }
    }

    fun onPhotosChanged() {
        frames.evictAll()
        pendingLock = pendingLock?.copy(jpeg = null)
        _revision.value++
    }

    suspend fun next(context: Context): Boolean = change(context) { state, images, config, now ->
        Rotation.next(state, images, config, random, now)
    }

    suspend fun previous(context: Context): Boolean = change(context) { state, images, config, now ->
        Rotation.previous(state, images, config, now)
    }

    /** Una foto cualquiera del pase (distinta de la actual), sin seguir el orden. */
    suspend fun random(context: Context): Boolean = change(context) { state, images, config, now ->
        images.filter { it != state.current }.randomOrNull(random)?.let { uri ->
            state.copy(
                current = uri,
                history = (state.history + listOfNotNull(state.current)).takeLast(config.historySize),
                forward = emptyList(),
                changedAt = now,
            )
        }
    }

    /** Pausa o reanuda el cambio automático; al reanudar, el plazo empieza de nuevo. */
    fun toggleAutoChange(context: Context) {
        val store = WallpaperStore.get(context)
        store.updateConfig { it.copy(autoPaused = !it.autoPaused) }
        if (!store.config.value.autoPaused) store.saveState(store.state.value.copy(changedAt = System.currentTimeMillis()))
    }

    /** Lo que hace un gesto del escritorio. */
    suspend fun perform(context: Context, action: TapAction) {
        when (action) {
            TapAction.NEXT -> next(context)
            TapAction.PREVIOUS -> previous(context)
            TapAction.RANDOM -> random(context)
            TapAction.TOGGLE_AUTO -> toggleAutoChange(context)
            TapAction.NONE -> Unit
        }
    }

    suspend fun show(context: Context, uri: String): Boolean = change(context) { state, _, config, now ->
        if (uri == state.current) {
            state
        } else {
            state.copy(
                current = uri,
                history = (state.history + listOfNotNull(state.current)).takeLast(config.historySize),
                forward = emptyList(),
                changedAt = now,
            )
        }
    }

    /** Aplica [step]; si cambia el fondo, lo guarda (el fondo dinámico lo pinta al momento). */
    private suspend fun change(
        context: Context,
        step: (RotationState, List<String>, WallpaperConfig, Long) -> RotationState?,
    ): Boolean = mutex.withLock {
        val app = context.applicationContext
        val store = WallpaperStore.get(app)
        val config = store.config.value
        val images = ImageCatalog.activeImages(app, config)
        val state = store.state.value
        var next = step(state, images, config, System.currentTimeMillis()) ?: return@withLock false
        if (next.current == state.current) return@withLock false
        // Modo aleatorio: se baraja ya la siguiente vuelta para saber qué foto preparar.
        if (config.order == RotationOrder.RANDOM && next.pending.isEmpty() && images.size > 1) {
            next = next.copy(pending = images.filter { it != next.current }.shuffled(random))
        }
        store.saveState(next)
        scheduleFollowUp(app, next, images, config)
        true
    }

    /** Bloqueo, colores del reloj y precarga del siguiente / anterior, sin retrasar el cambio. */
    private fun scheduleFollowUp(context: Context, state: RotationState, images: List<String>, config: WallpaperConfig) {
        followUp?.cancel()
        followUp = background.launch {
            val current = state.current ?: return@launch
            try {
                val (width, height) = screenSize(context)
                val bitmap = render(context, current, width, height, config.fit)
                // El color de vivo, si ya lo calculó antes para esta foto; si no, la aproximación.
                val colors = WallpaperRenderer.adaptiveColors(bitmap).let { approx ->
                    WallpaperStore.get(context).vivoColor(current)?.let { approx.copy(vivo = it) } ?: approx
                }
                WallpaperStore.get(context).saveClockColors(colors)
                ClockWidget.updateAll(context)
                if (config.place == WallpaperPlace.HOME_AND_LOCK) {
                    // Cambiar el fondo de bloqueo hace que el escritorio de vivo deje de pasar los
                    // toques al fondo hasta volver a Inicio: con el fondo dinámico puesto, se
                    // aplica al apagar la pantalla (y así solo una vez aunque se cambie varias).
                    // En vivo, wallpaperInfo no siempre es fiable: cuenta también el motor en marcha.
                    val live = isLiveWallpaperActive(context)
                    if (live) pendingLock = PendingLock(current) else setLock(context, bitmap, current)
                }
                // Precarga: lo que saldría con doble toque y con triple toque.
                predictedNext(state, images, config)?.let { render(context, it, width, height, config.fit) }
                state.history.lastOrNull()?.let { render(context, it, width, height, config.fit) }
                // Lo último (si con toques seguidos llega otro cambio, este se cancela antes): la foto
                // del bloqueo se deja ya comprimida, y al apagar la pantalla solo hay que entregarla.
                if (pendingLock?.uri == current) {
                    ensureActive()
                    val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, LOCK_QUALITY, it) }.toByteArray()
                    pendingLock?.takeIf { it.uri == current }?.let { pendingLock = it.copy(jpeg = jpeg) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo completar el cambio a $current", e)
            }
        }
    }

    private fun predictedNext(state: RotationState, images: List<String>, config: WallpaperConfig): String? {
        state.forward.lastOrNull()?.let { return it }
        return when (config.order) {
            RotationOrder.SEQUENTIAL -> images.getOrNull((images.indexOf(state.current) + 1).mod(images.size.coerceAtLeast(1)))
            RotationOrder.RANDOM -> state.pending.firstOrNull()
        }
    }

    fun screenSize(context: Context): Pair<Int, Int> {
        if (surfaceWidth > 0 && surfaceHeight > 0) return surfaceWidth to surfaceHeight
        val size = DeviceScreen.size(context)
        return size.width to size.height
    }

    /** Foto a tamaño de pantalla, desde la caché si ya está preparada. */
    fun render(context: Context, uri: String, width: Int, height: Int, fit: WallpaperFit): Bitmap {
        val key = "$uri|$width|$height|$fit"
        frames.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        // Una sola decodificación a la vez: si el fondo y la precarga piden la misma foto, la
        // segunda la encuentra ya hecha.
        synchronized(this) {
            frames.get(key)?.takeIf { !it.isRecycled }?.let { return it }
            val bitmap = WallpaperRenderer.render(context, Uri.parse(uri), width, height, fit)
            frames.put(key, bitmap)
            return bitmap
        }
    }

    fun cached(uri: String, width: Int, height: Int, fit: WallpaperFit): Bitmap? =
        frames.get("$uri|$width|$height|$fit")?.takeIf { !it.isRecycled }

    /** Foto pendiente de poner en la pantalla de bloqueo y, si ya está, comprimida en JPEG. */
    private data class PendingLock(val uri: String, val jpeg: ByteArray? = null)

    /** Se aplica al apagar la pantalla (o al salir del escritorio). */
    @Volatile
    private var pendingLock: PendingLock? = null

    /** Lo llama el fondo dinámico al apagarse la pantalla. */
    fun applyPendingLock(context: Context) {
        val pending = pendingLock ?: return
        pendingLock = null
        background.launch {
            try {
                val config = WallpaperStore.get(context).config.value
                if (config.place != WallpaperPlace.HOME_AND_LOCK) return@launch
                val jpeg = pending.jpeg
                if (jpeg != null) {
                    setLock(context, jpeg, pending.uri)
                } else {
                    val (width, height) = screenSize(context)
                    setLock(context, render(context, pending.uri, width, height, config.fit), pending.uri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo poner el fondo de bloqueo", e)
            }
        }
    }

    private fun setLock(context: Context, bitmap: Bitmap, uri: String? = null) {
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        WallpaperStore.get(context).saveLockUri(uri)
    }

    private fun setLock(context: Context, jpeg: ByteArray, uri: String) {
        WallpaperManager.getInstance(context).setStream(ByteArrayInputStream(jpeg), null, true, WallpaperManager.FLAG_LOCK)
        WallpaperStore.get(context).saveLockUri(uri)
    }

    /**
     * vivo ha recalculado el color de su reloj de bloqueo: si en el bloqueo está la misma foto que
     * en el escritorio, el reloj del escritorio pasa a usar exactamente ese color.
     */
    fun onVivoLockColorChanged(context: Context) {
        val store = WallpaperStore.get(context)
        val color = VivoLockColor.read(context) ?: return
        val lock = store.lockUri ?: return
        // Se recuerda para esa foto: la próxima vez que salga, el reloj ya tiene el color exacto.
        store.saveVivoColor(lock, color)
        if (store.state.value.current != lock) return
        store.saveClockColors(store.clockColors.value.copy(vivo = color))
        ClockWidget.updateAll(context)
    }

    /**
     * El fondo dinámico de la app está puesto. En vivo `wallpaperInfo` no es fiable (tras actualizar
     * la app devuelve null aunque siga puesto), así que manda que el motor esté funcionando.
     */
    fun isLiveWallpaperActive(context: Context): Boolean = SlideshowWallpaperService.engineRunning ||
        WallpaperManager.getInstance(context).wallpaperInfo?.component == ComponentName(context, SlideshowWallpaperService::class.java)

    private const val TAG = "WallpaperController"

    /** Calidad del JPEG del fondo de bloqueo (a esta calidad no se distingue de la foto). */
    private const val LOCK_QUALITY = 95

    /** Actual, siguiente, anterior y un margen (cada una ~10 MB a 1080 × 2400). */
    private const val CACHE_FRAMES = 4
}
