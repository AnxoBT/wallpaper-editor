package com.wallpaperfit.app.model

import android.graphics.Bitmap

data class TargetSize(val width: Int, val height: Int) {
    val ratio: Float get() = width.toFloat() / height

    companion object {
        /** Solo si no se puede leer la pantalla del móvil (FHD+ habitual). */
        val FALLBACK = TargetSize(1080, 2400)
    }
}

/** Resoluciones habituales que se ofrecen como atajo en Encuadre (además de la de este móvil). */
enum class ResolutionPreset(val size: TargetSize) {
    FHD_PLUS_2340(TargetSize(1080, 2340)),
    QHD_PLUS(TargetSize(1440, 3200)),
    FHD_PORTRAIT(TargetSize(1080, 1920)),
    TABLET(TargetSize(1600, 2560)),
    SQUARE(TargetSize(1080, 1080)),
    PC_FHD(TargetSize(1920, 1080)),
    PC_QHD(TargetSize(2560, 1440)),
}

/** Relleno del lienzo de Encuadre donde la imagen no llega. */
enum class FrameFill { BLUR, WHITE, BLACK }

/**
 * Posición libre de la imagen dentro del recuadro de la pantalla destino.
 * [scale] es relativo al escalado que cubre justo el recuadro (1 = lo cubre; menos de 1 deja
 * huecos que se rellenan con [FrameFill]). [offsetX]/[offsetY] desplazan el centro de la imagen
 * como fracción del ancho/alto del recuadro. [rotation] en grados.
 */
data class FrameTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
) {
    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f
    }
}

enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    val compressFormat: Bitmap.CompressFormat,
    val quality: Int,
) {
    PNG("image/png", "png", Bitmap.CompressFormat.PNG, 100),
    JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG, 100),
}

enum class WallpaperTarget { HOME, LOCK, BOTH }

/** Al guardar: una copia nueva en la carpeta elegida o encima de la foto original. */
enum class SaveMode { COPY, REPLACE }

/** Grupos de la barra de herramientas. */
enum class ToolGroup { COMPOSE, ADJUST, EFFECTS }

/**
 * Herramientas del editor, en el orden en que aparecen en la barra inferior.
 * [photoLevel]: se aplica a la foto antes del encuadre (si no, al lienzo final ya encuadrado).
 */
enum class Tool(val group: ToolGroup, val photoLevel: Boolean = false) {
    FIT(ToolGroup.COMPOSE),
    CROP(ToolGroup.COMPOSE),
    EXPAND(ToolGroup.COMPOSE),
    HEAL(ToolGroup.COMPOSE),
    LAYERS(ToolGroup.COMPOSE),
    TUNE(ToolGroup.ADJUST, photoLevel = true),
    CURVES(ToolGroup.ADJUST, photoLevel = true),
    DETAILS(ToolGroup.ADJUST, photoLevel = true),
    BRUSH(ToolGroup.ADJUST, photoLevel = true),
    FILTERS(ToolGroup.EFFECTS, photoLevel = true),
    LENS_BLUR(ToolGroup.EFFECTS),
    GLAMOUR(ToolGroup.EFFECTS, photoLevel = true),
    HDR(ToolGroup.EFFECTS, photoLevel = true),
    DRAMA(ToolGroup.EFFECTS, photoLevel = true),
    VIGNETTE(ToolGroup.EFFECTS),
    GRAIN(ToolGroup.EFFECTS),
}

/**
 * Parámetros ajustables. [initial] es el valor que se pone al abrir la herramienta si aún no se
 * ha tocado (como Snapseed, que muestra el efecto aplicado nada más entrar).
 */
enum class AdjustParam(val tool: Tool, val min: Float, val max: Float, val default: Float = 0f, val initial: Float? = null) {
    BRIGHTNESS(Tool.TUNE, -100f, 100f),
    CONTRAST(Tool.TUNE, -100f, 100f),
    SATURATION(Tool.TUNE, -100f, 100f),
    AMBIANCE(Tool.TUNE, -100f, 100f),
    HIGHLIGHTS(Tool.TUNE, -100f, 100f),
    SHADOWS(Tool.TUNE, -100f, 100f),
    WARMTH(Tool.TUNE, -100f, 100f),
    TINT(Tool.TUNE, -100f, 100f),
    STRUCTURE(Tool.DETAILS, 0f, 100f),
    SHARPEN(Tool.DETAILS, 0f, 100f),
    DEHAZE(Tool.DETAILS, -100f, 100f),
    LENS_STRENGTH(Tool.LENS_BLUR, 0f, 100f, initial = 50f),
    LENS_TRANSITION(Tool.LENS_BLUR, 0f, 100f, 40f),
    LENS_VIGNETTE(Tool.LENS_BLUR, 0f, 100f, 30f),
    GLOW(Tool.GLAMOUR, 0f, 100f, initial = 40f),
    GLOW_SATURATION(Tool.GLAMOUR, -100f, 100f),
    GLOW_WARMTH(Tool.GLAMOUR, -100f, 100f),
    HDR_STRENGTH(Tool.HDR, 0f, 100f, initial = 50f),
    HDR_BRIGHTNESS(Tool.HDR, -100f, 100f),
    HDR_SATURATION(Tool.HDR, -100f, 100f),
    DRAMA_STRENGTH(Tool.DRAMA, 0f, 100f, initial = 50f),
    DRAMA_SATURATION(Tool.DRAMA, -100f, 100f),
    VIGNETTE(Tool.VIGNETTE, -100f, 100f, initial = 40f),
    VIGNETTE_SIZE(Tool.VIGNETTE, 0f, 100f, 50f),
    GRAIN(Tool.GRAIN, 0f, 100f),
    GRAIN_SIZE(Tool.GRAIN, 0f, 100f, 30f);

    val isSigned: Boolean get() = min < 0f

    companion object {
        fun forTool(tool: Tool): List<AdjustParam> = entries.filter { it.tool == tool }
    }
}

// region Curvas

/** Canal sobre el que actúa una curva. */
enum class CurveChannel { RGB, RED, GREEN, BLUE }

/** Punto de control de una curva, en 0..1 (x = entrada, y = salida). */
data class CurvePoint(val x: Float, val y: Float)

/** Curvas de tono por canal. Una curva con solo los extremos (0,0)-(1,1) no cambia nada. */
data class Curves(val points: Map<CurveChannel, List<CurvePoint>> = emptyMap()) {

    operator fun get(channel: CurveChannel): List<CurvePoint> = points[channel] ?: IDENTITY

    fun with(channel: CurveChannel, curve: List<CurvePoint>): Curves {
        val sorted = curve.sortedBy { it.x }
        return Curves(if (isIdentity(sorted)) points - channel else points + (channel to sorted))
    }

    val isNone: Boolean get() = points.isEmpty()

    companion object {
        val IDENTITY = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        fun isIdentity(curve: List<CurvePoint>): Boolean = curve.all { kotlin.math.abs(it.x - it.y) < 0.002f }
    }
}

/** Curvas predefinidas de un toque (canal RGB). */
enum class CurvePreset(val points: List<CurvePoint>) {
    LINEAR(Curves.IDENTITY),
    SOFT_CONTRAST(listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.21f), CurvePoint(0.75f, 0.79f), CurvePoint(1f, 1f))),
    STRONG_CONTRAST(listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.16f), CurvePoint(0.75f, 0.85f), CurvePoint(1f, 1f))),
    BRIGHTEN(listOf(CurvePoint(0f, 0f), CurvePoint(0.45f, 0.58f), CurvePoint(1f, 1f))),
    DARKEN(listOf(CurvePoint(0f, 0f), CurvePoint(0.55f, 0.42f), CurvePoint(1f, 1f))),
    MATTE(listOf(CurvePoint(0f, 0.1f), CurvePoint(0.3f, 0.3f), CurvePoint(0.75f, 0.78f), CurvePoint(1f, 0.94f))),
    FADED(listOf(CurvePoint(0f, 0.16f), CurvePoint(0.5f, 0.52f), CurvePoint(1f, 0.88f))),
}

// endregion

// region Pincel

/** Qué cambia el Pincel donde se pinta. */
enum class BrushEffect { EXPOSURE, TEMPERATURE, SATURATION, STRUCTURE }

/**
 * Trazo del Pincel en coordenadas de la foto original sin recortar (como [HealStroke]).
 * [value] va de -100 a 100; 0 borra el efecto en esa zona.
 */
data class BrushStroke(val points: List<Float>, val radius: Float, val effect: BrushEffect, val value: Float)

// endregion

// region Desenfoque de lente

enum class LensShape { RADIAL, LINEAR }

/**
 * Zona enfocada del Desenfoque de lente, en fracciones del lienzo final: centro ([centerX],
 * [centerY]), tamaño respecto al lado corto y ángulo (en grados, solo para la forma lineal).
 */
data class LensBlur(
    val shape: LensShape = LensShape.RADIAL,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val size: Float = 0.35f,
    val angle: Float = 0f,
) {
    companion object {
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 1.5f
    }
}

// endregion

/** Valores de los ajustes. Solo se guardan los que difieren del valor por defecto. */
data class Adjustments(private val values: Map<AdjustParam, Float> = emptyMap()) {

    operator fun get(param: AdjustParam): Float = values[param] ?: param.default

    fun with(param: AdjustParam, value: Float): Adjustments {
        val clamped = value.coerceIn(param.min, param.max)
        return Adjustments(if (clamped == param.default) values - param else values + (param to clamped))
    }

    fun reset(tool: Tool): Adjustments = Adjustments(values.filterKeys { it.tool != tool })

    /** Subconjunto que modifica la foto antes del encaje (sirve como clave de caché). */
    fun photoOnly(): Adjustments = Adjustments(values.filterKeys { it.tool.photoLevel })

    fun isDefault(tool: Tool): Boolean = values.keys.none { it.tool == tool }
}

/** Filtros de un toque. */
enum class FilterPreset {
    NONE, BW, NOIR, SEPIA, INVERT, VINTAGE, WARM, COOL, DRAMA, VIVID, MATTE, CINEMA
}

data class FilterSettings(
    val preset: FilterPreset = FilterPreset.NONE,
    /** 0-100: mezcla entre la foto sin filtro y con el filtro completo. */
    val intensity: Float = 100f,
) {
    val isNone: Boolean get() = preset == FilterPreset.NONE || intensity <= 0f
}

/**
 * Recorte de la foto original, en fracciones de su ancho y alto (0 = borde izquierdo/superior,
 * 1 = borde derecho/inferior). Se aplica antes de Expandir.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f
    val widthFraction: Float get() = right - left
    val heightFraction: Float get() = bottom - top

    companion object {
        /** Tamaño mínimo del recorte, en fracción de la foto. */
        const val MIN_SIZE = 0.05f
    }
}

/** Proporciones fijas del recorte. [ORIGINAL] y [SCREEN] dependen de la foto y del destino. */
enum class CropAspect(val ratio: Float?) {
    FREE(null),
    ORIGINAL(null),
    SQUARE(1f),
    PORTRAIT_4_5(4f / 5f),
    PORTRAIT_3_4(3f / 4f),
    PORTRAIT_9_16(9f / 16f),
    SCREEN(null),
    LANDSCAPE_16_9(16f / 9f),
}

/** Qué parte del rectángulo de recorte se está arrastrando. */
enum class CropHandle { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Alineación rápida de la imagen respecto al recuadro de Encuadre. */
enum class FrameAlign { LEFT, CENTER_HORIZONTAL, RIGHT, TOP, CENTER_VERTICAL, BOTTOM }

/** Relleno del área añadida por Expandir, igual que en Snapseed. */
enum class ExpandFill { SMART, WHITE, BLACK }

/** Qué reproduce el relleno inteligente de Expandir además del color del fondo. */
enum class FillDetail {
    /** Solo el fondo, sin partículas ni manchas. */
    CLEAN,

    /** Textura del fondo: nubes, grano, degradados con detalle. */
    TEXTURE,
}

enum class Side { LEFT, TOP, RIGHT, BOTTOM }

/**
 * Lienzo añadido a cada lado, como fracción del ancho (izquierda/derecha) o del alto
 * (arriba/abajo) de la foto original.
 */
data class Expansion(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val fill: ExpandFill = ExpandFill.SMART,
    val detail: FillDetail = FillDetail.CLEAN,
) {
    val isNone: Boolean get() = left == 0f && top == 0f && right == 0f && bottom == 0f

    operator fun get(side: Side): Float = when (side) {
        Side.LEFT -> left
        Side.TOP -> top
        Side.RIGHT -> right
        Side.BOTTOM -> bottom
    }

    fun with(side: Side, value: Float): Expansion {
        val v = value.coerceIn(0f, MAX)
        return when (side) {
            Side.LEFT -> copy(left = v)
            Side.TOP -> copy(top = v)
            Side.RIGHT -> copy(right = v)
            Side.BOTTOM -> copy(bottom = v)
        }
    }

    /** Ancho y alto del lienzo expandido para una foto de [width] × [height]. */
    fun expandedWidth(width: Int): Float = width * (1f + left + right)

    fun expandedHeight(height: Int): Float = height * (1f + top + bottom)

    companion object {
        const val MAX = 2f
    }
}

/** Forma de una zona del Corrector. */
enum class HealShape {
    /** Trazo de pincel: [HealStroke.points] es la línea y [HealStroke.radius] el radio del pincel. */
    STROKE,

    /** Rectángulo: [HealStroke.points] = izquierda, arriba, derecha, abajo; [HealStroke.radius] es un margen extra. */
    RECT,

    /**
     * Texto detectado: como [RECT], pero solo se borran los trazos del color [HealStroke.textColor]
     * dentro del rectángulo, ensanchados [HealStroke.radius].
     */
    TEXT,
}

/** Cómo se rellena una zona del Corrector. */
enum class HealEngine {
    /** Modelo generativo: reconstruye objetos, textos y fondos con estructura. */
    AI,

    /** Por parches de la propia foto: ideal para motas sobre fondos lisos (mantiene el color exacto). */
    CLASSIC,

    /** Funde los colores bajo el pincel: disimula uniones y crea degradados. */
    MIX,

    /** Revuelve lo que hay bajo el pincel, sin difuminarlo: rompe repeticiones y patrones. */
    SHUFFLE,
}

/**
 * Zona del Corrector en coordenadas de píxel de la foto original sin recortar (pueden caer en
 * la zona expandida, con valores negativos o mayores que el tamaño de la foto).
 * [points] va aplanado: x0, y0, x1, y1…
 */
data class HealStroke(
    val points: List<Float>,
    val radius: Float,
    val shape: HealShape = HealShape.STROKE,
    val engine: HealEngine = HealEngine.AI,
    val textColor: Int = 0,
)

/** Ediciones que cambian el contenido o el tamaño de la imagen base: recorte → expansión → corrector. */
data class ContentEdits(
    val crop: CropRect = CropRect(),
    val expansion: Expansion = Expansion(),
    val heals: List<HealStroke> = emptyList(),
)

data class FitSettings(
    val size: TargetSize = TargetSize.FALLBACK,
    val fill: FrameFill = FrameFill.BLUR,
    val transform: FrameTransform = FrameTransform(),
)

/** Estado completo y reproducible de una edición: lo que guarda el historial de deshacer. */
data class EditState(
    val content: ContentEdits = ContentEdits(),
    val filter: FilterSettings = FilterSettings(),
    val adjustments: Adjustments = Adjustments(),
    val curves: Curves = Curves(),
    val brush: List<BrushStroke> = emptyList(),
    val lens: LensBlur = LensBlur(),
    val fit: FitSettings = FitSettings(),
    /** Imágenes encima de la foto (collage, superposición), de abajo arriba. */
    val layers: List<ImageLayer> = emptyList(),
    /**
     * Imagen de partida: 0 = la foto abierta; las demás, fotos con las capas ya combinadas (al
     * confirmar Capas), para que Recortar, Corrector, etc. trabajen sobre el conjunto.
     */
    val base: Int = 0,
) {
    /** Lo que se aplica a la foto antes del encuadre; sirve de clave de caché. */
    val photo: PhotoEdits get() = PhotoEdits(adjustments.photoOnly(), filter, curves, brush)
}

/** Ediciones de la foto (no dependen del encuadre). */
data class PhotoEdits(
    val adjustments: Adjustments = Adjustments(),
    val filter: FilterSettings = FilterSettings(),
    val curves: Curves = Curves(),
    val brush: List<BrushStroke> = emptyList(),
) {
    val isNone: Boolean get() = adjustments == Adjustments() && filter.isNone && curves.isNone && brush.isEmpty()
}

/**
 * Imagen puesta encima del lienzo final. Posición y tamaño en fracciones del lienzo: centro
 * ([centerX], [centerY]) y ancho ([width]); el alto sale de [aspect] (alto / ancho de la imagen).
 */
data class ImageLayer(
    val id: Long,
    val uri: String,
    val aspect: Float,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
) {
    companion object {
        const val MIN_WIDTH = 0.03f
        const val MAX_WIDTH = 8f
    }
}

/** Acciones rápidas sobre la capa elegida. */
enum class LayerAction { FIT, FILL, CENTER, FLIP, FRONT, BACK, DELETE }
