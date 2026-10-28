package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.EditState
import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FilterSettings
import com.wallpaperfit.app.model.PhotoEdits
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Canal de edición a partir de la imagen base: ediciones de foto (filtro, ajustes, curvas,
 * efectos, pincel) → encuadre en el lienzo → capas → desenfoque de lente, viñeta y grano.
 *
 * Mantiene tres versiones de la imagen base: completa (exportación), vista previa y borrador
 * (para respuesta inmediata mientras el usuario arrastra). El resultado de las ediciones de foto
 * se cachea, porque mover el encuadre o la viñeta no necesita recalcularlas.
 */
class EditRenderer(blurEngine: BlurEngine) {

    enum class Quality(val sourceMaxSide: Int, val outputMaxSide: Int) {
        DRAFT(700, 700),
        PREVIEW(1600, 1400),
    }

    /** Versiones de la imagen base; [geometry] la relaciona con la foto original (para el Pincel). */
    class Sources(val full: Bitmap, val preview: Bitmap, val draft: Bitmap, val geometry: BaseGeometry)

    private data class CacheKey(val sources: Sources, val quality: Quality, val photo: PhotoEdits)

    private val adjuster = AdjustmentProcessor(blurEngine)
    private val fitter = ImageProcessor(blurEngine)

    private val photoCache = object : LinkedHashMap<CacheKey, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Bitmap>): Boolean = size > MAX_CACHE_ENTRIES
    }

    fun prepareSources(full: Bitmap, geometry: BaseGeometry): Sources = Sources(
        full = full,
        preview = BitmapScaler.downscaleToMaxSide(full, Quality.PREVIEW.sourceMaxSide),
        draft = BitmapScaler.downscaleToMaxSide(full, Quality.DRAFT.sourceMaxSide),
        geometry = geometry,
    )

    /** Vista previa reducida, ya encuadrada en la pantalla destino. */
    @Synchronized
    fun renderPreview(sources: Sources, edit: EditState, quality: Quality): Bitmap {
        val photo = photoFor(sources, quality, edit.photo)
        val size = edit.fit.size
        val scale = min(1f, quality.outputMaxSide.toFloat() / max(size.width, size.height))
        val width = (size.width * scale).roundToInt().coerceAtLeast(1)
        val height = (size.height * scale).roundToInt().coerceAtLeast(1)
        val output = fitter.render(photo, width, height, edit.fit)
        if (edit.layers.isNotEmpty()) LayerImages.draw(Canvas(output), width, height, edit.layers)
        adjuster.applyCanvasEffects(output, edit.adjustments, edit.lens)
        return output
    }

    /**
     * Imagen base con las ediciones de foto pero sin encuadrar: la usan Corrector, Pincel y
     * Encuadre, que necesitan ver la foto entera.
     */
    @Synchronized
    fun renderBaseView(sources: Sources, photo: PhotoEdits, quality: Quality): Bitmap = photoFor(sources, quality, photo)

    /** Miniatura de cada filtro sobre la imagen con los ajustes actuales. */
    fun filterThumbnails(sources: Sources, adjustments: Adjustments, maxSide: Int): Map<FilterPreset, Bitmap> {
        val small = BitmapScaler.downscaleToMaxSide(sources.draft, maxSide)
        val base = adjuster.applyPhoto(small, adjustments.photoOnly(), FilterSettings())
        return FilterPreset.entries.associateWith { preset ->
            adjuster.applyPhoto(base, Adjustments(), FilterSettings(preset))
        }
    }

    /** Resultado final a la resolución exacta pedida, a partir de la imagen completa. */
    fun renderFull(sources: Sources, edit: EditState): Bitmap {
        val photo = adjuster.applyPhoto(sources.full, edit.photo, mapping(sources, sources.full))
        val size = edit.fit.size
        val output = fitter.render(photo, size.width, size.height, edit.fit)
        if (photo !== sources.full) photo.recycle()
        if (edit.layers.isNotEmpty()) LayerImages.draw(Canvas(output), size.width, size.height, edit.layers)
        adjuster.applyCanvasEffects(output, edit.adjustments, edit.lens)
        return output
    }

    @Synchronized
    fun clearCache() {
        photoCache.clear()
    }

    private fun photoFor(sources: Sources, quality: Quality, photo: PhotoEdits): Bitmap {
        val key = CacheKey(sources, quality, photo)
        val source = if (quality == Quality.DRAFT) sources.draft else sources.preview
        return photoCache[key] ?: adjuster.applyPhoto(source, photo, mapping(sources, source)).also { photoCache[key] = it }
    }

    /** De coordenadas de la foto original a píxeles de [image] (una versión de la base). */
    private fun mapping(sources: Sources, image: Bitmap): PhotoEffects.Mapping {
        val g = sources.geometry
        val k = image.width.toFloat() / g.width
        return PhotoEffects.Mapping(g.scale * k, g.offsetX * k, g.offsetY * k)
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 6
    }
}
