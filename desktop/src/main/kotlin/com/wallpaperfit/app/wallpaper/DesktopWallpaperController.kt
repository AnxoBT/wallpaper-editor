package com.wallpaperfit.app.wallpaper

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wallpaperfit.app.model.FitSettings
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.FrameTransform
import com.wallpaperfit.app.model.WallpaperTarget
import com.wallpaperfit.app.processing.BitmapScaler
import com.wallpaperfit.app.processing.BlurEngine
import com.wallpaperfit.app.processing.Geometry
import com.wallpaperfit.app.processing.ImageLoader
import com.wallpaperfit.app.processing.ImageProcessor
import com.wallpaperfit.app.storage.WallpaperSetter
import com.wallpaperfit.app.ui.DeviceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Pase de fondos de Windows: mismas fuentes, orden y horarios que en el móvil. Windows no tiene
 * fondos dinámicos, así que cada cambio prepara una imagen a la medida de la pantalla y la pone de
 * fondo. Entre cambios no hace nada: solo un temporizador mientras la app sigue abierta.
 */
object WallpaperController {
    private const val PREFERENCES = "desktop_wallpaper"
    private const val KEY_ACTIVE = "active"
    private const val TAG = "WallpaperController"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changing = Mutex()
    private var timer: Job? = null
    private lateinit var app: Context

    private val _active = MutableStateFlow(false)

    /** El pase está en marcha (se recuerda de una vez para otra). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Al arrancar la app. */
    fun start(context: Context) {
        app = context.applicationContext
        if (app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)) setActive(context, true)
    }

    fun isLiveWallpaperActive(context: Context): Boolean = _active.value

    fun setActive(context: Context, active: Boolean) {
        app = context.applicationContext
        _active.value = active
        app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, active).apply()
        timer?.cancel()
        timer = if (active) scope.launch { runTimer() } else null
    }

    private suspend fun runTimer() {
        val store = WallpaperStore.get(app)
        while (true) {
            val wait = Rotation.millisUntilAutoChange(store.state.value, store.config.value, System.currentTimeMillis())
            // Revisa cada minuto para enterarse de los cambios de ajustes sin más avisos.
            delay((wait ?: 60_000L).coerceIn(1_000L, 60_000L))
            val now = System.currentTimeMillis()
            if (store.config.value.sources.isNotEmpty() && Rotation.isAutoChangeDue(store.state.value, store.config.value, now)) next(app)
        }
    }

    /** Devuelve false si no hay fotos o no se ha podido poner. */
    suspend fun next(context: Context): Boolean = change(context) { state, images, config, now ->
        Rotation.next(state, images, config, Random.Default, now)
    }

    /** Devuelve false si no hay un fondo anterior. */
    suspend fun previous(context: Context): Boolean = change(context) { state, images, config, now ->
        Rotation.previous(state, images, config, now)
    }

    suspend fun show(context: Context, uri: String): Boolean = change(context) { state, _, _, now ->
        state.copy(
            current = uri,
            history = (state.history + listOfNotNull(state.current)).takeLast(WallpaperConfig.MAX_HISTORY),
            forward = emptyList(),
            changedAt = now,
        )
    }

    private suspend fun change(
        context: Context,
        step: (RotationState, List<String>, WallpaperConfig, Long) -> RotationState?,
    ): Boolean = changing.withLock {
        val store = WallpaperStore.get(context)
        val config = store.config.value
        val images = ImageCatalog.activeImages(context, config)
        val state = step(store.state.value, images, config, System.currentTimeMillis()) ?: return false
        val uri = state.current ?: return false
        try {
            apply(context, uri, config)
            store.saveState(state)
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo poner $uri", e)
            false
        }
    }

    /** Si la foto de fondo no cumple el filtro de etiquetas u horario, se cambia a una que sí. */
    suspend fun followFilter(context: Context) {
        val store = WallpaperStore.get(context)
        val images = ImageCatalog.activeImages(context, store.config.value)
        if (images.isNotEmpty() && store.state.value.current !in images) next(context)
    }

    /** Tras editar una foto: si es la de fondo, se vuelve a poner. */
    fun onPhotosChanged() {
        ImageCatalog.invalidate()
        if (!_active.value) return
        scope.launch {
            val store = WallpaperStore.get(app)
            store.state.value.current?.let { show(app, it) }
        }
    }

    private suspend fun apply(context: Context, uri: String, config: WallpaperConfig) {
        val photo = ImageLoader.load(context, Uri.parse(uri))
        val size = DeviceScreen.size(context)
        val transform = if (config.fit == WallpaperFit.FIT_BLUR) {
            FrameTransform(scale = Geometry.fitScale(photo.width, photo.height, size.width.toFloat(), size.height.toFloat(), 0f))
        } else {
            FrameTransform()
        }
        val output = BlurEngine(context).use { blur ->
            ImageProcessor(blur).render(photo, size.width, size.height, FitSettings(size, FrameFill.BLUR, transform))
        }
        photo.recycle()
        try {
            WallpaperSetter.apply(context, output, WallpaperTarget.HOME)
        } finally {
            output.recycle()
        }
    }
}

/** El reloj de bloqueo es cosa de los móviles vivo: aquí solo hace falta el tipo que usa ClockConfig. */
object LockClockFont {
    class LockStyle(val colorFollowsWallpaper: Boolean, val align: ClockAlign?)
}
