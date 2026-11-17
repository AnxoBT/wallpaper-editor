package com.wallpaperfit.app.ui.wallpapers

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperfit.app.R
import com.wallpaperfit.app.wallpaper.AdaptiveColors
import com.wallpaperfit.app.wallpaper.ClockAlign
import com.wallpaperfit.app.wallpaper.ClockConfig
import com.wallpaperfit.app.wallpaper.ClockWidget
import com.wallpaperfit.app.wallpaper.DatePosition
import com.wallpaperfit.app.wallpaper.LockClockFont
import com.wallpaperfit.app.wallpaper.WallpaperRenderer
import com.wallpaperfit.app.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La foto editada a pantalla completa con lo que se le pondrá encima: el escritorio (el widget del
 * reloj de la app, los puntos de página y el dock con las apps de verdad del móvil) y, en los móviles
 * que dejan leer el estilo de su reloj de bloqueo (vivo), también el bloqueo (su reloj, el candado y
 * los accesos de abajo). Tocar cambia entre bloqueo y escritorio; atrás cierra.
 *
 * Ajuste oculto: mantener pulsado en el bloqueo enseña o quita los widgets que el móvil tiene en su
 * bloqueo (si deja leerlos, vivo). Apagado de serie y no se anuncia: es para quien lo quiera.
 *
 * Las medidas son píxeles de una pantalla de 1080 × 2400; se escalan al ancho de esta y lo de abajo
 * se mide desde el borde inferior.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetPreview(preview: ImageBitmap, sourceUri: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = WallpaperStore.get(context)
    val clock by store.clock.collectAsStateWithLifecycle()
    // Fuera del hilo principal: colores de la foto, si se sabe cómo es el reloj de bloqueo, apps y widgets.
    val loaded by produceState<Loaded?>(null, preview, sourceUri) {
        value = withContext(Dispatchers.Default) {
            // Como el widget del reloj: el color exacto de vivo si ya lo calculó para esta foto; si no, el aproximado.
            val approx = WallpaperRenderer.adaptiveColors(preview.asAndroidBitmap())
            Loaded(
                colors = sourceUri?.let { store.vivoColorFor(it) }?.let { approx.copy(vivo = it) } ?: approx,
                // El bloqueo solo se enseña si se sabe cómo es su reloj; en otros móviles no hay forma de leerlo.
                hasLock = LockClockFont.lockStyle(context, refresh = true) != null,
                dock = dockApps(context),
                lockWidgets = lockWidgets(context),
                alarm = nextAlarm(context),
            )
        }
    }
    val now = rememberMinuteTick()
    val hasLock = loaded?.hasLock == true
    var lock by remember(hasLock) { mutableStateOf(hasLock) }
    var showWidgets by remember { mutableStateOf(PreviewPrefs.widgets(context)) }
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(lock) {
        hint = true
        delay(2500)
        hint = false
    }

    BackHandler(onBack = onClose)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = {
                    if (lock && loaded?.lockWidgets?.isNotEmpty() == true) {
                        showWidgets = !showWidgets
                        PreviewPrefs.setWidgets(context, showWidgets)
                    }
                },
                onClick = { if (hasLock) lock = !lock },
            ),
    ) {
        val scale = Scale(maxWidth / 1080f, maxHeight, LocalDensity.current.fontScale)
        Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val l = loaded
        when {
            l == null -> Unit
            lock -> {
                val color = Color(l.colors.vivo)
                LockClock(clock.likeLockScreen(clock.use24h), color, now)
                LockExtras(scale, color, if (showWidgets) l.lockWidgets else emptyList(), l.alarm)
            }
            else -> {
                HomeClock(clock, Color(ClockWidget.textColor(clock, l.colors)), now)
                HomeExtras(scale, l.dock)
            }
        }
        if (hint && l != null) {
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
private class Loaded(
    val colors: AdaptiveColors,
    val hasLock: Boolean,
    val dock: List<DockSlot>,
    val lockWidgets: List<LockWidgetInfo>,
    val alarm: String?,
)

/** Si se enseñan los widgets de bloqueo en la vista previa (ajuste oculto, apagado de serie). */
private object PreviewPrefs {
    private const val PREFERENCES = "widget_preview"
    private const val KEY_WIDGETS = "lock_widgets_on"

    fun widgets(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_WIDGETS, false)

    fun setWidgets(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_WIDGETS, on).apply()
}

/** Paso de píxeles de una pantalla de 1080 × 2400 a esta. */
private class Scale(val unit: Dp, val height: Dp, val fontScale: Float) {
    fun dp(px: Float): Dp = unit * px

    /** Distancia desde arriba de algo que en 1080 × 2400 está en [px], medida desde abajo. */
    fun fromBottom(px: Float): Dp = height - unit * (2400f - px)

    /** Tamaño de letra de [px]. */
    fun font(px: Float) = (px * unit.value / fontScale).sp
}

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

/** Candado, accesos de abajo (teléfono y cámara) y, si hay, los widgets de bloqueo. */
@Composable
private fun LockExtras(s: Scale, color: Color, widgets: List<LockWidgetInfo>, alarm: String?) {
    Placed(s, 540f, s.dp(168f), 56f) { Icon(Icons.Filled.LockOpen, null, tint = Color.White, modifier = Modifier.fillMaxSize()) }
    // Widgets: fila de 4 casillas de 246 empezando en x = 88; un círculo de 172 por casilla.
    var cell = 0
    widgets.forEach { widget ->
        if (cell + widget.cells > 4) return@forEach
        LockWidget(s, widget, 88f + cell * 246f, color, alarm)
        cell += widget.cells
    }
    Placed(s, 94f, s.fromBottom(2304f), 66f) { Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.fillMaxSize()) }
    Placed(s, 986f, s.fromBottom(2304f), 62f) { Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.fillMaxSize()) }
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

/** Puntos de página y el dock con los iconos de las apps del móvil. */
@Composable
private fun HomeExtras(s: Scale, dock: List<DockSlot>) {
    // Indicador de páginas: la de la izquierda (≡) y tres puntos, la última activa.
    Placed(s, 474f, s.fromBottom(1908f), 16f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.absoluteOffset(y = -s.dp(3f)).size(s.dp(16f), s.dp(3f)).background(Color.White.copy(alpha = 0.55f)))
            Box(Modifier.absoluteOffset(y = s.dp(3f)).size(s.dp(16f), s.dp(3f)).background(Color.White.copy(alpha = 0.55f)))
        }
    }
    listOf(518f, 562f, 606f).forEachIndexed { i, x ->
        Placed(s, x, s.fromBottom(1908f), 12f) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = if (i == 2) 1f else 0.55f)))
        }
    }
    // Centrados y separados 198, como en un dock de 5.
    val first = 540f - (dock.size - 1) * 99f
    dock.forEachIndexed { i, slot ->
        Placed(s, first + i * 198f, s.fromBottom(2195f), 148f) {
            when (slot) {
                // Cuadrado con las esquinas redondeadas (36 de 148), como los iconos del escritorio.
                is DockSlot.App -> Image(
                    slot.icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(s.dp(36f))),
                )
                DockSlot.Folder -> Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(s.dp(36f)))
                        .background(Color(0xFF2B2B2B).copy(alpha = 0.85f))
                        .padding(s.dp(17f)),
                ) {
                    // Carpeta de ejemplo: 9 apps genéricas en miniatura, en cuadrícula de 3 × 3.
                    FOLDER_APPS.forEachIndexed { k, (icon, tint) ->
                        Box(
                            Modifier
                                .absoluteOffset(s.dp(k % 3 * 39f), s.dp(k / 3 * 39f))
                                .size(s.dp(36f))
                                .clip(RoundedCornerShape(s.dp(9f)))
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(s.dp(26f)))
                        }
                    }
                }
            }
        }
    }
}

/** Un hueco del dock: una app o la carpeta. */
private sealed interface DockSlot {
    class App(val icon: ImageBitmap) : DockSlot

    data object Folder : DockSlot
}

/** Apps de ejemplo de la carpeta del dock (genéricas, no las del móvil). */
private val FOLDER_APPS = listOf(
    Icons.Filled.Email to Color(0xFFE5483B),
    Icons.Filled.Map to Color(0xFF34A853),
    Icons.Filled.PlayArrow to Color(0xFF1E88E5),
    Icons.Filled.Cloud to Color(0xFFF9AB00),
    Icons.Filled.Photo to Color(0xFFEC407A),
    Icons.Filled.Event to Color(0xFF4285F4),
    Icons.Filled.MusicNote to Color(0xFF8E44AD),
    Icons.Filled.Person to Color(0xFF00897B),
    Icons.Filled.Settings to Color(0xFF757575),
)

/** Algo cuadrado de [size] con el centro en ([x], [y]). */
@Composable
private fun Placed(s: Scale, x: Float, y: Dp, size: Float, content: @Composable () -> Unit) {
    Box(Modifier.absoluteOffset(s.dp(x - size / 2), y - s.dp(size / 2)).size(s.dp(size))) { content() }
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

/**
 * El dock típico: teléfono, mensajes, navegador, una carpeta con otras apps y la cámara; las apps
 * son las predeterminadas de este móvil, con su icono y su forma. Las que no haya se saltan.
 */
private fun dockApps(context: Context): List<DockSlot> {
    val pm = context.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )
    val size = (148f * context.resources.displayMetrics.widthPixels / 1080f).toInt().coerceAtLeast(48)
    val apps = intents.map { intent ->
        try {
            // Sin app predeterminada sale el selector del sistema: no es una app, se salta.
            pm.resolveActivity(intent, 0)?.activityInfo?.takeIf { it.packageName != "android" }
                ?.let { DockSlot.App(squareIcon(it.loadIcon(pm), size)) }
        } catch (e: Exception) {
            null
        }
    }
    return apps.take(3).filterNotNull() + DockSlot.Folder + listOfNotNull(apps.getOrNull(3))
}

/**
 * El icono llenando todo el cuadrado (luego se recorta con esquinas redondeadas): los adaptativos,
 * con su fondo y su dibujo a sangre (la zona visible son los 2/3 del centro); los demás, sobre blanco.
 */
private fun squareIcon(drawable: Drawable, size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
        val pad = size / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach {
            it.setBounds(-pad, -pad, size + pad, size + pad)
            it.draw(canvas)
        }
    } else {
        canvas.drawColor(android.graphics.Color.WHITE)
        val pad = size / 8
        drawable.setBounds(pad, pad, size - pad, size - pad)
        drawable.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/** Widget de la pantalla de bloqueo: qué es y cuántas casillas ocupa (de las 4 de la fila). */
private data class LockWidgetInfo(val kind: Kind, val cells: Int) {
    enum class Kind { STEPS, WEATHER, ALARM, OTHER }
}

/** Los widgets de bloqueo que el móvil guarda en sus ajustes (vivo), en orden; vacío si no se pueden leer. */
private fun lockWidgets(context: Context): List<LockWidgetInfo> = try {
    val list = JSONObject(Settings.Secure.getString(context.contentResolver, "lock_screen_theme_id_os") ?: "{}").optJSONArray("widget")
    (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }.map {
        val name = it.optString("component_name").lowercase()
        val kind = when {
            "step" in name || "health" in name -> LockWidgetInfo.Kind.STEPS
            "weather" in name -> LockWidgetInfo.Kind.WEATHER
            "alarm" in name -> LockWidgetInfo.Kind.ALARM
            else -> LockWidgetInfo.Kind.OTHER
        }
        LockWidgetInfo(kind, it.optInt("width_cell", 1).coerceIn(1, 4))
    }
} catch (e: Exception) {
    emptyList()
}

/** La próxima alarma del móvil ("07:15"), o null si no hay. */
private fun nextAlarm(context: Context): String? {
    val time = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime ?: return null
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

/**
 * Un widget de bloqueo empezando en [left]: círculo de 172 (una casilla) o píldora (dos o más), de
 * y = 648 a 820, con el fondo teñido del color del reloj, como hace vivo. La hora de la alarma es la
 * de verdad; pasos y tiempo son de ejemplo (Android no deja leer los widgets de otras apps).
 */
@Composable
private fun LockWidget(s: Scale, widget: LockWidgetInfo, left: Float, color: Color, alarm: String?) {
    val width = 172f + (widget.cells - 1) * 246f
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .absoluteOffset(s.dp(left), s.dp(648f))
            .size(s.dp(width), s.dp(172f))
            .clip(shape)
            .background(color.copy(alpha = 0.3f))
            .border(s.dp(2f), color.copy(alpha = 0.18f), shape),
    ) {
        when (widget.kind) {
            LockWidgetInfo.Kind.WEATHER -> {
                WidgetIcon(s, Icons.Filled.Cloud, color, 80f, 47f, 46f)
                WidgetText(s, "18°C", color, 113f, 49f, width)
                WidgetText(s, stringResource(R.string.widget_preview_weather), color, 53f, 94f, width)
                WidgetText(s, "15 ~ 21°C", color, 53f, 130f, width)
            }
            LockWidgetInfo.Kind.STEPS -> {
                WidgetIcon(s, Icons.AutoMirrored.Filled.DirectionsWalk, color, width / 2, 54f, 50f)
                WidgetText(s, "4200", color, null, 110f, width, size = 42f)
            }
            LockWidgetInfo.Kind.ALARM -> {
                WidgetIcon(s, Icons.Filled.AccessAlarm, color, width / 2, 54f, 50f)
                WidgetText(s, alarm ?: "--:--", color, null, 110f, width, size = 42f)
            }
            LockWidgetInfo.Kind.OTHER -> WidgetIcon(s, Icons.Filled.Widgets, color, width / 2, 86f, 56f)
        }
    }
}

/** Icono de un widget con el centro en ([x], [y]) dentro del widget. */
@Composable
private fun WidgetIcon(s: Scale, icon: ImageVector, color: Color, x: Float, y: Float, size: Float) {
    Icon(icon, null, tint = color, modifier = Modifier.absoluteOffset(s.dp(x - size / 2), s.dp(y - size / 2)).size(s.dp(size)))
}

/** Línea de texto de un widget centrada en altura en [y]; desde [x] o centrada si [x] es null. */
@Composable
private fun WidgetText(s: Scale, text: String, color: Color, x: Float?, y: Float, width: Float, size: Float = 37f) {
    Box(
        Modifier.absoluteOffset(s.dp(x ?: 0f), s.dp(y - size)).size(s.dp(width - (x ?: 0f)), s.dp(size * 2)),
        contentAlignment = if (x == null) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(text, color = color, fontSize = s.font(size), maxLines = 1, softWrap = false)
    }
}
