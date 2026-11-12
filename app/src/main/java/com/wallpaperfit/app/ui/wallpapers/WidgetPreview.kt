package com.wallpaperfit.app.ui.wallpapers

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperfit.app.R
import com.wallpaperfit.app.wallpaper.ClockAlign
import com.wallpaperfit.app.wallpaper.ClockConfig
import com.wallpaperfit.app.wallpaper.ClockWidget
import com.wallpaperfit.app.wallpaper.DatePosition
import com.wallpaperfit.app.wallpaper.LockClockFont
import com.wallpaperfit.app.wallpaper.WallpaperRenderer
import com.wallpaperfit.app.wallpaper.WallpaperStore
import kotlinx.coroutines.delay
import com.wallpaperfit.app.wallpaper.AdaptiveColors
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Date
import java.util.Locale

/**
 * La foto editada a pantalla completa con el reloj encima: el del escritorio (el widget de la app,
 * con sus ajustes) y, en los móviles que dejan leer el estilo de su reloj de bloqueo (vivo), también
 * ese. Tocar cambia entre los dos; atrás cierra.
 *
 * Las medidas son píxeles de una pantalla de 1080 de ancho y se escalan al lado corto de esta.
 */
@Composable
fun WidgetPreview(preview: ImageBitmap, sourceUri: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = WallpaperStore.get(context)
    val clock by store.clock.collectAsStateWithLifecycle()
    // Fuera del hilo principal: colores de la foto y si se sabe cómo es el reloj de bloqueo.
    val loaded by produceState<Loaded?>(null, preview, sourceUri) {
        value = withContext(Dispatchers.Default) {
            // Como el widget del reloj: el color exacto de vivo si ya lo calculó para esta foto; si no, el aproximado.
            val approx = WallpaperRenderer.adaptiveColors(preview.asAndroidBitmap())
            Loaded(
                colors = sourceUri?.let { store.vivoColorFor(it) }?.let { approx.copy(vivo = it) } ?: approx,
                // El bloqueo solo se enseña si se sabe cómo es su reloj; en otros móviles no hay forma de leerlo.
                hasLock = LockClockFont.lockStyle(context, refresh = true) != null,
            )
        }
    }
    val now = rememberMinuteTick()
    val hasLock = loaded?.hasLock == true
    var lock by remember(hasLock) { mutableStateOf(hasLock) }
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(lock) {
        hint = true
        delay(2500)
        hint = false
    }

    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (hasLock) lock = !lock },
    ) {
        Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val colors = loaded?.colors
        when {
            colors == null -> Unit
            lock -> LockClock(clock.likeLockScreen(clock.use24h), Color(colors.vivo), now)
            else -> HomeClock(clock, Color(ClockWidget.textColor(clock, colors)), now)
        }
        if (hint && loaded != null) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center)) {
                Text(
                    stringResource(
                        when {
                            !hasLock -> R.string.widget_preview_home_only
                            lock -> R.string.widget_preview_lock
                            else -> R.string.widget_preview_home
                        },
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Lo que tarda en calcularse al abrir la vista previa. */
private class Loaded(val colors: AdaptiveColors, val hasLock: Boolean)

@Composable
private fun LockClock(lockClock: ClockConfig, color: Color, now: Date) {
    val context = LocalContext.current
    val (time, shortDate) = lockClock.format(now)
    // Reloj de bloqueo de vivo (estilo clásico): su fuente; alto de las cifras y líneas base medidos en el móvil.
    ClockText(
        timeTypeface = ClockWidget.clockTypeface(context, lockClock.font) ?: Typeface.DEFAULT_BOLD,
        dateTypeface = Typeface.DEFAULT,
        color = color,
        shadow = null,
        align = ClockAlign.CENTER,
        // En el bloqueo, la fecha empieza en mayúscula ("Jue, 12 nov").
        date = shortDate.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        dateDigits = 42f,
        dateBaseline = 322f,
        time = time,
        timeDigits = 192f,
        timeBaseline = 576f,
    )
}

@Composable
private fun HomeClock(clock: ClockConfig, color: Color, now: Date) {
    val context = LocalContext.current
    val (time, date) = clock.format(now)
    // El widget con el estilo de bloqueo mide 186 (hora) y 43 (fecha); los demás tamaños, en proporción.
    val timeDigits = 186f * clock.timeSize / ClockConfig.VIVO_TIME_SIZE
    val dateDigits = 43f * clock.dateSize / ClockConfig.VIVO_DATE_SIZE
    val top = 252f
    val above = clock.datePosition == DatePosition.ABOVE
    val timeBaseline = if (above && clock.showDate) top + dateDigits + timeDigits * 1.15f else top + timeDigits
    val timeTypeface = ClockWidget.clockTypeface(context, clock.font) ?: Typeface.DEFAULT
    ClockText(
        timeTypeface = timeTypeface,
        dateTypeface = if (clock.font.dateUsesSystemFont) Typeface.DEFAULT else timeTypeface,
        color = color,
        shadow = ClockWidget.shadowColor(ClockWidget.shadowVariant(clock, color.toArgb())),
        align = clock.align,
        date = date.takeIf { clock.showDate },
        dateDigits = dateDigits,
        dateBaseline = if (above) top + dateDigits else timeBaseline + timeDigits * 0.3f + dateDigits,
        time = time,
        timeDigits = timeDigits,
        timeBaseline = timeBaseline,
    )
}

/**
 * Fecha y hora con la fuente exacta: el tamaño se elige para que las cifras midan lo mismo que en la
 * captura ([dateDigits], [timeDigits] px de alto), con la línea base en el mismo sitio.
 */
@Composable
private fun ClockText(
    timeTypeface: Typeface,
    dateTypeface: Typeface,
    color: Color,
    shadow: Int?,
    align: ClockAlign,
    date: String?,
    dateDigits: Float,
    dateBaseline: Float,
    time: String,
    timeDigits: Float,
    timeBaseline: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        // Escala por el lado corto: en una tablet en horizontal, el reloj no crece con el ancho.
        val k = minOf(size.width, size.height) / 1080f
        val argb = color.toArgb()
        fun paint(typeface: Typeface, digits: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = argb
            textSize = 100f
            val bounds = Rect().also { getTextBounds("0123456789", 0, 10, it) }
            textSize = 100f * digits * k / bounds.height().coerceAtLeast(1)
            shadow?.let { setShadowLayer(10f * k, 0f, 4f * k, it) }
        }
        fun x(paint: Paint, text: String): Float = when (align) {
            ClockAlign.LEFT -> 70f * k
            ClockAlign.CENTER -> (size.width - paint.measureText(text)) / 2f
            ClockAlign.RIGHT -> size.width - 70f * k - paint.measureText(text)
        }
        drawIntoCanvas { canvas ->
            val timePaint = paint(timeTypeface, timeDigits)
            canvas.nativeCanvas.drawText(time, x(timePaint, time), timeBaseline * k, timePaint)
            if (date != null) {
                val datePaint = paint(dateTypeface, dateDigits)
                canvas.nativeCanvas.drawText(date, x(datePaint, date), dateBaseline * k, datePaint)
            }
        }
    }
}
