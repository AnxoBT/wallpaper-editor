package com.wallpaperfit.app.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Fondo dinámico estilo Wallpaper Engine, pensado para no gastar batería:
 * - Solo dibuja al cambiar de foto: un fundido corto entre la anterior y la nueva y nada más.
 * - El cambio por tiempo solo cuenta mientras el fondo se ve (escritorio en pantalla): con la
 *   pantalla apagada o una app abierta no hay temporizadores en marcha.
 * - Doble toque en un hueco del escritorio: siguiente. Triple toque: el anterior.
 */
class SlideshowWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SlideshowEngine()

    private inner class SlideshowEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val handler = Handler(Looper.getMainLooper())
        private val store = WallpaperStore.get(this@SlideshowWallpaperService)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private var width = 0
        private var height = 0
        private var visible = false
        private var frame: Bitmap? = null
        private var frameKey: String? = null
        private var hardwareOk = true

        // Fundido entre fondos.
        private var fadingFrom: Bitmap? = null
        private var fadeStart = 0L
        private val fadeCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                draw()
                if (fadingFrom != null) Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // Toques: el lanzador avisa de cada toque en un hueco (COMMAND_TAP). Si no lo hace, se
        // usan los eventos táctiles directos.
        private var taps = 0
        private var lastTapAt = 0L
        private var launcherSendsTaps = false
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        // Margen generoso: un toque real con el pulgar se mueve algo más que el mínimo del sistema.
        private val tapSlop = ViewConfiguration.get(this@SlideshowWallpaperService).scaledTouchSlop * 2

        private val evaluateTaps = Runnable { actOnTaps(taps) }

        // "Al encender la pantalla": se marca al encenderse y se cambia al verse el fondo. El
        // receptor solo existe mientras el fondo está puesto y no despierta al móvil.
        private var screenTurnedOn = false
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> screenTurnedOn = true
                    Intent.ACTION_SCREEN_OFF -> WallpaperController.applyPendingLock(context)
                    // Reloj dibujado como imagen: se renueva cada minuto (el sistema no envía este
                    // aviso con la pantalla apagada, así que no gasta nada en reposo).
                    Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                        ClockWidget.refreshDrawnClock(context)
                }
            }
        }

        private var vivoObserver: android.database.ContentObserver? = null
        private var galleryObserver: android.database.ContentObserver? = null

        private val autoChange = Runnable { scope.launch { WallpaperController.next(this@SlideshowWallpaperService) } }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
            if (!isPreview) {
                runningEngines.incrementAndGet()
                vivoObserver = VivoLockColor.observe(this@SlideshowWallpaperService) {
                    WallpaperController.onVivoLockColorChanged(this@SlideshowWallpaperService)
                }
                // Al arrancar, se lee ya el color que vivo tenga calculado para el bloqueo actual.
                WallpaperController.onVivoLockColorChanged(this@SlideshowWallpaperService)
                galleryObserver = ImageCatalog.watch(this@SlideshowWallpaperService) {
                    WallpaperStore.get(this@SlideshowWallpaperService).config.value.sources
                }
                registerReceiver(
                    screenReceiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_TIME_TICK)
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    },
                )
                // El reloj con fuente propia se dibuja como imagen solo mientras hay un motor que lo
                // renueve cada minuto: al arrancar o parar el motor, el widget cambia de modo.
                ClockWidget.updateAll(this@SlideshowWallpaperService)
            }
            // Redibuja cuando cambia la foto actual o el ajuste (desde los gestos o desde la app),
            // y vuelve a programar el cambio por tiempo.
            scope.launch {
                var lastRevision = WallpaperController.revision.value
                combine(store.state, store.config, WallpaperController.revision) { state, config, revision ->
                    Triple(state.current to revision, config.fit, state.changedAt to config.intervalMs)
                }
                    .collectLatest { (current, fit, _) ->
                        val (uri, revision) = current
                        if (revision != lastRevision) {
                            // La foto cambió en el disco: se descarta la que había en memoria.
                            lastRevision = revision
                            frameKey = null
                        }
                        scheduleAutoChange()
                        load(uri, fit)
                    }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            if (!isPreview) {
                WallpaperController.surfaceWidth = width
                WallpaperController.surfaceHeight = height
            }
            scope.launch { load(store.state.value.current, store.config.value.fit) }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (!visible) {
                handler.removeCallbacks(autoChange)
                // Al salir del escritorio (abrir una app o apagar la pantalla) ya se puede poner la
                // foto en el bloqueo sin estropear los toques: así el bloqueo se actualiza antes.
                if (!isPreview) WallpaperController.applyPendingLock(this@SlideshowWallpaperService)
                return
            }
            if (isPreview) return
            WallpaperController.followSchedule(this@SlideshowWallpaperService)
            // Al volver al escritorio, el reloj dibujado se pone al minuto (si no lo está ya).
            ClockWidget.refreshDrawnClock(this@SlideshowWallpaperService)
            val state = store.state.value
            val config = store.config.value
            val due = when (config.autoChange) {
                AutoChange.EVERY_UNLOCK -> screenTurnedOn && !config.autoPaused
                else -> Rotation.isAutoChangeDue(state, config, System.currentTimeMillis())
            }
            screenTurnedOn = false
            if (state.current == null || due) {
                scope.launch { WallpaperController.next(this@SlideshowWallpaperService) }
            } else {
                scheduleAutoChange()
            }
        }

        /** Programa el próximo cambio por tiempo, solo mientras el fondo se ve. */
        private fun scheduleAutoChange() {
            handler.removeCallbacks(autoChange)
            if (!visible || isPreview) return
            val wait = Rotation.millisUntilAutoChange(store.state.value, store.config.value, System.currentTimeMillis()) ?: return
            handler.postDelayed(autoChange, wait)
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) {
                launcherSendsTaps = true
                registerTap(0L)
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (launcherSendsTaps) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = event.eventTime
                    // Mientras el dedo vuelve a bajar no se da el gesto por terminado.
                    handler.removeCallbacks(evaluateTaps)
                }
                MotionEvent.ACTION_UP -> {
                    val still = abs(event.x - downX) < tapSlop && abs(event.y - downY) < tapSlop
                    if (still && event.eventTime - downAt < MAX_PRESS_MS) {
                        registerTap(event.eventTime - downAt)
                    } else if (taps > 0) {
                        // Un arrastre en medio cancela el gesto.
                        taps = 0
                    }
                }
                MotionEvent.ACTION_CANCEL -> taps = 0
            }
        }

        /**
         * [pressMs]: lo que duró el toque. El hueco entre toques se mide de levantar el dedo a
         * volver a levantarlo, así que se descuenta la duración de la pulsación.
         */
        private fun registerTap(pressMs: Long) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapAt - pressMs > TAP_GAP_MS) taps = 0
            lastTapAt = now
            taps++
            handler.removeCallbacks(evaluateTaps)
            val config = store.config.value
            when {
                // Sin triple toque no hay que esperar: el doble actúa al instante.
                taps == 2 && config.tripleTapAction == TapAction.NONE -> actOnTaps(2)
                taps >= 3 -> actOnTaps(3)
                // Se espera un poco por si llega un tercer toque.
                else -> handler.postDelayed(evaluateTaps, TAP_GAP_MS)
            }
        }

        private fun actOnTaps(count: Int) {
            handler.removeCallbacks(evaluateTaps)
            taps = 0
            val config = store.config.value
            val action = when {
                count >= 3 -> config.tripleTapAction
                count >= 2 -> config.doubleTapAction
                else -> TapAction.NONE
            }
            if (action != TapAction.NONE) {
                scope.launch { WallpaperController.perform(this@SlideshowWallpaperService, action) }
            }
        }

        private suspend fun load(uri: String?, fit: WallpaperFit) {
            if (width <= 0 || height <= 0) return
            if (uri == null) {
                show(null, null)
                return
            }
            val key = "$uri|$width|$height|$fit"
            if (key == frameKey && frame != null) {
                draw()
                return
            }
            // Normalmente ya está preparada (precarga) y no hay espera.
            val bitmap = WallpaperController.cached(uri, width, height, fit) ?: withContext(Dispatchers.IO) {
                try {
                    WallpaperController.render(this@SlideshowWallpaperService, uri, width, height, fit)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo cargar $uri", e)
                    null
                }
            }
            show(bitmap, if (bitmap != null) key else null)
        }

        /** Cambia al nuevo fondo con un fundido corto (sin fundido si aún no había ninguno o no se ve). */
        private fun show(bitmap: Bitmap?, key: String?) {
            val previous = frame
            frame = bitmap
            frameKey = key
            if (previous != null && bitmap != null && previous !== bitmap && visible) {
                fadingFrom = previous
                fadeStart = SystemClock.uptimeMillis()
                Choreographer.getInstance().removeFrameCallback(fadeCallback)
                Choreographer.getInstance().postFrameCallback(fadeCallback)
            } else {
                fadingFrom = null
                draw()
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            // Con GPU el fundido es más fluido; si la superficie no lo admite, se dibuja por software.
            val canvas = (if (hardwareOk) {
                try {
                    holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    Log.i(TAG, "Sin dibujo por GPU en este fondo: ${e.message}")
                    hardwareOk = false
                    null
                }
            } else {
                null
            }) ?: try {
                holder.lockCanvas()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo dibujar el fondo", e)
                null
            } ?: return
            try {
                val dst = Rect(0, 0, canvas.width, canvas.height)
                val bitmap = frame?.takeIf { !it.isRecycled }
                val from = fadingFrom?.takeIf { !it.isRecycled }
                val progress = ((SystemClock.uptimeMillis() - fadeStart).toFloat() / FADE_MS).coerceIn(0f, 1f)
                when {
                    bitmap == null -> canvas.drawColor(Color.BLACK)
                    from != null && progress < 1f -> {
                        canvas.drawBitmap(from, null, dst, paint)
                        fadePaint.alpha = (progress * progress * (3f - 2f * progress) * 255f).toInt()
                        canvas.drawBitmap(bitmap, null, dst, fadePaint)
                    }
                    else -> {
                        fadingFrom = null
                        canvas.drawBitmap(bitmap, null, dst, paint)
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(evaluateTaps)
            handler.removeCallbacks(autoChange)
            Choreographer.getInstance().removeFrameCallback(fadeCallback)
            if (!isPreview) {
                try {
                    unregisterReceiver(screenReceiver)
                } catch (e: IllegalArgumentException) {
                    // No estaba registrado.
                }
                runningEngines.decrementAndGet()
                ClockWidget.updateAll(this@SlideshowWallpaperService)
            }
            vivoObserver?.let { contentResolver.unregisterContentObserver(it) }
            galleryObserver?.let { applicationContext.contentResolver.unregisterContentObserver(it) }
            scope.cancel()
            frame = null
            fadingFrom = null
            super.onDestroy()
        }
    }

    companion object {
        // El sistema puede crear el motor nuevo antes de destruir el viejo: se cuentan, no basta
        // con un sí/no.
        private val runningEngines = java.util.concurrent.atomic.AtomicInteger()

        /** Hay un fondo dinámico real (no la vista previa) en marcha en este proceso. */
        val engineRunning: Boolean get() = runningEngines.get() > 0

        private const val TAG = "SlideshowWallpaper"
        /**
         * Tiempo máximo entre un toque y el siguiente (de levantar el dedo a volver a tocar) en un
         * mismo doble/triple toque. Algo más que el del sistema (300 ms) para que no falle.
         */
        const val TAP_GAP_MS = 420L
        /** Duración máxima de cada toque. */
        const val MAX_PRESS_MS = 350L
        /** Duración del fundido entre fondos. */
        const val FADE_MS = 220L
    }
}
