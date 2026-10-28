package com.wallpaperfit.app.ui

import com.wallpaperfit.app.model.HealStroke
import androidx.compose.ui.graphics.ImageBitmap
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.BrushEffect
import com.wallpaperfit.app.model.CropAspect
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CropRect
import com.wallpaperfit.app.model.EditState
import com.wallpaperfit.app.model.ExportFormat
import com.wallpaperfit.app.model.Expansion
import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.ResolutionPreset
import com.wallpaperfit.app.model.SaveMode
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.processing.BaseGeometry

/** Vista previa rápida de Expandir, válida solo para la expansión con la que se calculó. */
data class ExpandDraft(val crop: CropRect, val expansion: Expansion, val image: ImageBitmap)

data class EditorUiState(
    val hasImage: Boolean = false,
    /** Último estado guardado (exportado) de la foto abierta; al abrirla, el inicial. */
    val savedEdit: EditState? = null,
    /** Foto abierta (para que el selector empiece en su carpeta). */
    val sourceUri: android.net.Uri? = null,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    /** Geometría de la imagen base actual (foto expandida y corregida). */
    val baseGeometry: BaseGeometry? = null,
    /** Último estado confirmado (✓); es lo que se exporta y lo que guarda el historial. */
    val committed: EditState = EditState(),
    /** Estado que se está editando y mostrando; igual a [committed] si no hay herramienta abierta. */
    val working: EditState = EditState(),
    val activeTool: Tool? = null,
    /** Encuadre: pegar la imagen a bordes y centro al arrastrar. */
    val frameMagnet: Boolean = false,
    val selectedParam: AdjustParam? = null,
    /** Resolución de la pantalla de este móvil: la de partida y un atajo en Encuadre. */
    val deviceSize: TargetSize = TargetSize.FALLBACK,
    val widthText: String = deviceSize.width.toString(),
    val heightText: String = deviceSize.height.toString(),
    /** Resultado encajado en la pantalla destino. */
    val preview: ImageBitmap? = null,
    /** Miniaturas de cada filtro con los ajustes actuales. */
    val filterThumbnails: Map<FilterPreset, ImageBitmap> = emptyMap(),
    /** Imagen base con filtro y ajustes, sin encuadrar (Corrector y Encuadre). */
    val baseView: ImageBitmap? = null,
    /** Foto original reducida (Expandir). */
    val originalPreview: ImageBitmap? = null,
    val expandDraft: ExpandDraft? = null,
    val isExpandDraftLoading: Boolean = false,
    /** Diámetro del pincel del Corrector en dp de pantalla. */
    val brushSize: Float = 32f,
    /** Corrector: trazos deshechos que se pueden rehacer (el último, al final). */
    val healRedo: List<HealStroke> = emptyList(),
    /** Corrector: pintar con pincel o marcar un rectángulo. */
    val healShape: HealShape = HealShape.STROKE,
    /** Corrector: rellenar con IA o con el método clásico. */
    val healEngine: HealEngine = if (PlatformFeatures.AI_TOOLS) HealEngine.AI else HealEngine.CLASSIC,
    val isDetectingText: Boolean = false,
    /** Curvas: canal que se está editando. */
    val curveChannel: CurveChannel = CurveChannel.RGB,
    /** Pincel: efecto y valor con los que se pinta (0 = borrar). */
    val brushEffect: BrushEffect = BrushEffect.EXPOSURE,
    val brushValue: Float = 50f,
    /** Herramienta de cada paso del historial (null = foto recién abierta) y paso actual. */
    val historySteps: List<Tool?> = listOf(null),
    val historyIndex: Int = 0,
    val showHistorySheet: Boolean = false,
    /** Capa elegida en la herramienta Capas. */
    val selectedLayer: Long? = null,
    val showOriginal: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoading: Boolean = false,
    /** Expandir o Corrector están recalculando la imagen base. */
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val showExportSheet: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.PNG,
    /** Proporción elegida en Recortar (preferencia de la herramienta, no forma parte del historial). */
    val cropAspect: CropAspect = CropAspect.FREE,
    /** Nombre de la carpeta de guardado elegida por el usuario; null = la predeterminada. */
    val saveFolderName: String? = null,
    /** Carpeta de la foto abierta: sin carpeta elegida, las copias se guardan ahí. */
    val sourceFolderName: String? = null,
    /** Al guardar, poner la foto ya como fondo del fondo dinámico (si está activo). */
    val showOnWallpaper: Boolean = false,
    /** El fondo dinámico de la app está puesto (para ofrecer lo anterior y avisar del fondo fijo). */
    val liveWallpaperActive: Boolean = false,
    val saveMode: SaveMode = SaveMode.COPY,
    /** Nombre de la foto original si se puede reemplazar (foto local, no de la nube). */
    val originalName: String? = null,
) {
    /** Hay cambios en la foto que no se han guardado (contando la herramienta abierta). */
    val hasUnsavedChanges: Boolean get() = hasImage && savedEdit != null && (committed != savedEdit || working != savedEdit)

    /** Tamaño en píxeles de la foto tras el recorte en edición. */
    val croppedWidth: Int
        get() = if (originalWidth <= 0) 0 else BaseGeometry.croppedSize(originalWidth, working.content.crop.left, working.content.crop.right)

    val croppedHeight: Int
        get() = if (originalHeight <= 0) 0 else BaseGeometry.croppedSize(originalHeight, working.content.crop.top, working.content.crop.bottom)

    val isResolutionValid: Boolean get() = parseTargetSize(widthText, heightText) != null

    val selectedPreset: ResolutionPreset? get() = ResolutionPreset.entries.firstOrNull { it.size == working.fit.size }

    val isDeviceSize: Boolean get() = working.fit.size == deviceSize

    companion object {
        const val MIN_SIDE = 16
        const val MAX_SIDE = 8192
        const val MIN_BRUSH = 10f
        const val MAX_BRUSH = 90f

        fun parseTargetSize(widthText: String, heightText: String): TargetSize? {
            val width = widthText.toIntOrNull() ?: return null
            val height = heightText.toIntOrNull() ?: return null
            if (width !in MIN_SIDE..MAX_SIDE || height !in MIN_SIDE..MAX_SIDE) return null
            return TargetSize(width, height)
        }
    }
}
