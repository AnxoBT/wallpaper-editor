package com.wallpaperfit.app.ui

import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.storage.RecentEdits
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.wallpaperfit.app.wallpaper.WallpaperController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.Adjustments
import com.wallpaperfit.app.model.BrushEffect
import com.wallpaperfit.app.model.BrushStroke
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CurvePoint
import com.wallpaperfit.app.model.CurvePreset
import com.wallpaperfit.app.model.Curves
import com.wallpaperfit.app.model.LensBlur
import com.wallpaperfit.app.model.LensShape
import com.wallpaperfit.app.model.PhotoEdits
import com.wallpaperfit.app.processing.BaseGeometry
import com.wallpaperfit.app.model.ContentEdits
import com.wallpaperfit.app.model.CropAspect
import com.wallpaperfit.app.model.CropHandle
import com.wallpaperfit.app.model.CropRect
import com.wallpaperfit.app.model.FrameAlign
import com.wallpaperfit.app.model.EditState
import com.wallpaperfit.app.model.ExpandFill
import com.wallpaperfit.app.model.Expansion
import com.wallpaperfit.app.model.ExportFormat
import com.wallpaperfit.app.model.FillDetail
import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FilterSettings
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.FrameTransform
import com.wallpaperfit.app.model.ResolutionPreset
import com.wallpaperfit.app.model.SaveMode
import com.wallpaperfit.app.model.FitSettings
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.HealStroke
import com.wallpaperfit.app.model.Side
import com.wallpaperfit.app.model.TargetSize
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.model.WallpaperTarget
import com.wallpaperfit.app.processing.AiInpainter
import com.wallpaperfit.app.processing.BitmapScaler
import com.wallpaperfit.app.processing.BlurEngine
import com.wallpaperfit.app.processing.ContentProcessor
import com.wallpaperfit.app.processing.EditRenderer
import com.wallpaperfit.app.processing.Geometry
import com.wallpaperfit.app.processing.ImageLoader
import com.wallpaperfit.app.processing.LayerImages
import com.wallpaperfit.app.model.ImageLayer
import com.wallpaperfit.app.model.LayerAction
import com.wallpaperfit.app.processing.TextDetector
import com.wallpaperfit.app.storage.GallerySaver
import com.wallpaperfit.app.storage.OriginalReplacer
import com.wallpaperfit.app.storage.WallpaperSetter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val blurEngine = BlurEngine(application)
    private val renderer = EditRenderer(blurEngine)
    private val aiInpainter = AiInpainter(application)
    private val contentProcessor = ContentProcessor(aiInpainter)

    /** Resolución de esta pantalla: es la de partida de cada foto nueva. */
    private val deviceSize = DeviceScreen.size(application)
    private val initialEdit = EditState(fit = FitSettings(size = deviceSize))

    private val _uiState = MutableStateFlow(EditorUiState(deviceSize = deviceSize, committed = initialEdit, working = initialEdit))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Diálogos del sistema que la UI debe lanzar para pedir permiso de modificar la original. */
    private val _writeRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val writeRequests: SharedFlow<IntentSender> = _writeRequests.asSharedFlow()

    /** Foto original en la galería, si se puede reemplazar. */
    private var originalTarget: OriginalReplacer.Target? = null

    /** Foto tal y como se cargó (máx. [ImageLoader.MAX_SOURCE_SIDE]). */
    private val original = MutableStateFlow<Bitmap?>(null)

    /** Imágenes de partida por id ([EditState.base]): la foto abierta y las combinadas con capas. */
    private val bases = HashMap<Int, Bitmap>()
    private var nextBase = 1

    /** Imagen de partida y contenido con los que se construye la imagen base. */
    private data class SourceKey(val base: Int, val content: ContentEdits)

    /** Imagen base construida junto con aquello para lo que se construyó (se publican a la vez). */
    private data class Built(val key: SourceKey, val set: EditRenderer.Sources)

    /** Capas combinándose (evita combinar dos veces con un doble toque en ✓). */
    private var merging = false

    /** Contenido con el que se construye la imagen base (en Expandir y Recortar, el confirmado). */
    private fun EditorUiState.sourceKey(): SourceKey = SourceKey(
        working.base,
        if (activeTool == Tool.EXPAND || activeTool == Tool.CROP) committed.content else working.content,
    )
    private var originalSources: EditRenderer.Sources? = null
    private val originalDraft = MutableStateFlow<Bitmap?>(null)

    /** Versiones de la imagen base (expandida y corregida) para vista previa y exportación. */
    private val built = MutableStateFlow<Built?>(null)
    private val sources = built.map { it?.set }.distinctUntilChanged()

    // Declaradas antes de init: el bloque init lee la carpeta guardada.
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var saveFolder: Uri? = preferences.getString(KEY_SAVE_FOLDER, null)?.let(Uri::parse)

    /** Estados confirmados y la herramienta que produjo cada uno (null = estado inicial). */
    private val history = mutableListOf(initialEdit)
    private val historyTools = mutableListOf<Tool?>(null)

    /** Posición de la imagen sin imán durante un gesto en Encuadre. */
    private var rawFrameTransform: FrameTransform? = null
    private var historyIndex = 0

    private data class RenderRequest(val edit: EditState, val original: Boolean, val key: SourceKey)
    private data class BaseViewRequest(val active: Boolean, val photo: PhotoEdits)
    private data class ThumbnailRequest(val active: Boolean, val adjustments: Adjustments)

    init {
        refreshSaveFolderName()
        observeContent()
        observePreview()
        observeBaseView()
        observeExpandDraft()
        observeFilterThumbnails()
        observeBase()
    }

    /**
     * Al combinar capas o deshacer/rehacer una combinación cambia la imagen de partida: se
     * actualizan sus versiones reducidas (Expandir) y su tamaño. La imagen base la reconstruye
     * [observeContent] con el id y el contenido a la vez, así nunca se mezclan.
     */
    private fun observeBase() {
        viewModelScope.launch {
            // También con cada foto nueva: su id (0) no cambia de una foto a otra.
            combine(original, _uiState.map { it.working.base }.distinctUntilChanged()) { loaded, id -> loaded to id }
                .collectLatest { (loaded, id) ->
                    if (loaded == null) return@collectLatest
                    val bitmap = bases[id] ?: return@collectLatest
                    val (preview, draft) = withContext(Dispatchers.Default) { reducedVersions(bitmap) }
                    _uiState.update {
                        it.copy(
                            originalWidth = bitmap.width,
                            originalHeight = bitmap.height,
                            originalPreview = preview.asImageBitmap(),
                            expandDraft = null,
                        )
                    }
                    originalDraft.value = draft
                }
        }
    }

    /** Vista previa y borrador reducidos de una imagen de partida. */
    private fun reducedVersions(bitmap: Bitmap): Pair<Bitmap, Bitmap> {
        val preview = BitmapScaler.downscaleToMaxSide(bitmap, EditRenderer.Quality.PREVIEW.sourceMaxSide)
        // El borrador sale de la vista previa: reducir desde la imagen entera otra vez sobra.
        return preview to BitmapScaler.downscaleToMaxSide(preview, ORIGINAL_DRAFT_SIDE)
    }

    /**
     * Confirmar Capas: la foto con sus capas (y lo ya editado) pasa a ser una sola imagen, así
     * las demás herramientas trabajan sobre el conjunto. Se puede deshacer.
     */
    private fun flattenLayers(state: EditorUiState) {
        if (merging) return
        merging = true
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                // Si la imagen base aún se está reconstruyendo (un retoque recién hecho), se espera
                // a que esté lista para no perder ese último cambio.
                val key = state.sourceKey()
                val set = withTimeoutOrNull(MERGE_WAIT_MS) { built.filterNotNull().first { it.key == key }.set }
                    ?: return@launch showMessage(R.string.msg_render_error)
                val factor = mergeFactor(set, state.working.fit)
                val merged = withContext(Dispatchers.Default) {
                    // A la resolución de la foto (no a la del resultado): recortar o ampliar luego
                    // no pierde nitidez. La escala del encuadre es relativa: no cambia con el tamaño.
                    val size = state.working.fit.size
                    val big = TargetSize((size.width * factor).roundToInt(), (size.height * factor).roundToInt())
                    renderer.renderFull(set, state.working.copy(fit = state.working.fit.copy(size = big)))
                }
                // Si mientras tanto se siguió editando (o se cerró Capas), el resultado ya no vale.
                val now = _uiState.value
                if (now.activeTool != Tool.LAYERS || now.working != state.working) {
                    merged.recycle()
                    return@launch
                }
                val id = nextBase++
                bases[id] = merged
                val edit = EditState(fit = state.working.fit.copy(transform = FrameTransform()), base = id)
                pushHistory(edit, Tool.LAYERS)
                _uiState.update {
                    it.copy(activeTool = null, selectedParam = null, selectedLayer = null, committed = edit, working = edit)
                        .withResolutionTexts(edit.fit.size)
                        .withHistoryFlags()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                showMessage(R.string.msg_oom)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron combinar las capas", e)
                showMessage(R.string.msg_render_error)
            } finally {
                merging = false
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    /**
     * Cuántas veces mayor que el resultado se guarda la imagen combinada: la densidad de la foto
     * dentro del encuadre (≥ 1), con un tope de memoria.
     */
    private fun mergeFactor(set: EditRenderer.Sources, fit: FitSettings): Float {
        val size = fit.size
        val cover = Geometry.coverScale(set.full.width, set.full.height, size.width.toFloat(), size.height.toFloat())
        val density = 1f / (cover * fit.transform.scale)
        val memoryCap = sqrt(MAX_MERGE_PIXELS / (size.width.toFloat() * size.height))
        // Más grande que el lado máximo de la imagen base no sirve: se reduciría al usarla.
        val sideCap = ContentProcessor.MAX_BASE_SIDE.toFloat() / maxOf(size.width, size.height)
        return density.coerceIn(1f, minOf(MAX_MERGE_FACTOR, memoryCap, sideCap).coerceAtLeast(1f))
    }

    /** Suelta las imágenes combinadas a las que ya no llega el historial. */
    private fun pruneBases() {
        val used = history.map { it.base }.toSet() + _uiState.value.working.base
        bases.keys.retainAll(used)
    }

    // region Canales de procesado

    /**
     * Reconstruye la imagen base cuando cambian Expandir o Corrector. Mientras Expandir está
     * abierto se usa la expansión confirmada: su vista previa se calcula aparte, más rápida.
     */
    private fun observeContent() {
        viewModelScope.launch {
            combine(
                original,
                _uiState.map { it.sourceKey() }.distinctUntilChanged(),
            ) { loaded, key -> loaded to key }
                .collectLatest { (loaded, key) ->
                    // [original] solo avisa de que hay foto cargada; la imagen de partida es la del id.
                    if (loaded == null) return@collectLatest
                    val photo = bases[key.base] ?: return@collectLatest
                    val content = key.content
                    _uiState.update { it.copy(isProcessing = true) }
                    try {
                        val (set, geometry) = withContext(Dispatchers.Default) {
                            val base = contentProcessor.buildBase(photo, content, cancellationCheck())
                            val geometry = contentProcessor.geometry(photo, content)
                            renderer.prepareSources(base, geometry) to geometry
                        }
                        built.value = Built(key, set)
                        _uiState.update { it.copy(baseGeometry = geometry, isProcessing = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        _uiState.update { it.copy(isProcessing = false) }
                        showMessage(R.string.msg_oom)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar la imagen base", e)
                        _uiState.update { it.copy(isProcessing = false) }
                        showMessage(R.string.msg_render_error)
                    }
                }
        }
    }

    /**
     * Cada cambio se pinta primero en calidad borrador (respuesta inmediata al arrastrar) y, si el
     * usuario deja de tocar, se refina en calidad de vista previa.
     */
    private fun observePreview() {
        viewModelScope.launch {
            combine(
                built,
                // Con Capas abierta, las capas las pinta la pantalla (para moverlas con fluidez).
                _uiState.map {
                    val edit = if (it.activeTool == Tool.LAYERS) it.working.copy(layers = emptyList()) else it.working
                    RenderRequest(edit, it.showOriginal, it.sourceKey())
                }.distinctUntilChanged(),
            ) { current, request -> current to request }
                .collectLatest { (current, request) ->
                    // Imagen base aún de otro paso (se está reconstruyendo): se deja la vista previa
                    // anterior hasta que llegue la buena, en vez de mezclar ediciones y foto.
                    if (current == null || current.key != request.key) return@collectLatest
                    val set = current.set
                    val source = if (request.original) originalSources ?: set else set
                    val edit = if (request.original) EditState(fit = request.edit.fit) else request.edit
                    try {
                        publishPreview(render(source, edit, EditRenderer.Quality.DRAFT))
                        delay(REFINE_DELAY_MS)
                        publishPreview(render(source, edit, EditRenderer.Quality.PREVIEW))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        showMessage(R.string.msg_oom)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en la vista previa", e)
                        showMessage(R.string.msg_render_error)
                    }
                }
        }
    }

    private suspend fun render(set: EditRenderer.Sources, edit: EditState, quality: EditRenderer.Quality): Bitmap =
        withContext(Dispatchers.Default) {
            renderer.renderPreview(set, edit, quality)
        }

    private fun publishPreview(bitmap: Bitmap) {
        _uiState.update { it.copy(preview = bitmap.asImageBitmap()) }
    }

    /** Imagen completa sin encuadrar para Corrector, Pincel y Encuadre. */
    private fun observeBaseView() {
        viewModelScope.launch {
            combine(
                sources,
                _uiState.map {
                    BaseViewRequest(
                        active = it.activeTool == Tool.HEAL || it.activeTool == Tool.FIT || it.activeTool == Tool.BRUSH,
                        photo = if (it.showOriginal) PhotoEdits() else it.working.photo,
                    )
                }.distinctUntilChanged(),
            ) { set, request -> set to request }
                .collectLatest { (set, request) ->
                    if (set == null || !request.active) return@collectLatest
                    val view = withContext(Dispatchers.Default) { renderer.renderBaseView(set, request.photo, EditRenderer.Quality.PREVIEW) }
                    _uiState.update { it.copy(baseView = view.asImageBitmap()) }
                }
        }
    }

    /** Relleno de Expandir en baja resolución mientras se ajustan los bordes. */
    private fun observeExpandDraft() {
        viewModelScope.launch {
            combine(
                originalDraft,
                _uiState.map { if (it.activeTool == Tool.EXPAND) it.working.content.crop to it.working.content.expansion else null }
                    .distinctUntilChanged(),
            ) { draft, request -> draft to request }
                .collectLatest { (draft, request) ->
                    if (draft == null || request == null) {
                        _uiState.update { it.copy(expandDraft = null, isExpandDraftLoading = false) }
                        return@collectLatest
                    }
                    val (crop, expansion) = request
                    val current = _uiState.value.expandDraft
                    if (current?.crop == crop && current.expansion == expansion) return@collectLatest
                    _uiState.update { it.copy(isExpandDraftLoading = true) }
                    // Espera a que el usuario suelte o pare el arrastre antes de calcular.
                    if (expansion.fill == ExpandFill.SMART && !expansion.isNone) delay(EXPAND_DRAFT_DELAY_MS)
                    try {
                        val bitmap = withContext(Dispatchers.Default) {
                            contentProcessor.expandDraft(draft, crop, expansion, cancellationCheck())
                        }
                        _uiState.update {
                            it.copy(expandDraft = ExpandDraft(crop, expansion, bitmap.asImageBitmap()), isExpandDraftLoading = false)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en la vista previa de Expandir", e)
                        _uiState.update { it.copy(isExpandDraftLoading = false) }
                    }
                }
        }
    }

    /** Miniaturas de los filtros mientras la herramienta Filtros está abierta. */
    private fun observeFilterThumbnails() {
        viewModelScope.launch {
            combine(
                sources,
                _uiState.map { ThumbnailRequest(it.activeTool == Tool.FILTERS, it.working.adjustments.photoOnly()) }
                    .distinctUntilChanged(),
            ) { set, request -> set to request }
                .collectLatest { (set, request) ->
                    if (set == null || !request.active) return@collectLatest
                    val thumbnails = withContext(Dispatchers.Default) {
                        renderer.filterThumbnails(set, request.adjustments, THUMBNAIL_SIDE)
                            .mapValues { it.value.asImageBitmap() }
                    }
                    _uiState.update { it.copy(filterThumbnails = thumbnails) }
                }
        }
    }

    /** Permite abortar el relleno inteligente si el usuario cambia algo mientras se calcula. */
    private fun CoroutineScope.cancellationCheck(): () -> Unit {
        val context = coroutineContext
        return { context.ensureActive() }
    }

    // endregion

    // region Imagen

    /** [fromWallpapers]: abierta desde Fondos, así que al guardarla se pone ya de fondo. */
    fun loadImage(uri: Uri, fromWallpapers: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                aiInpainter.warmUp()
                val bitmap = ImageLoader.load(getApplication<Application>(), uri)
                originalTarget = withContext(Dispatchers.IO) { OriginalReplacer.resolve(getApplication(), uri) }
                sourceFolder = withContext(Dispatchers.IO) { originalTarget?.let { folderOf(it.uri) } ?: documentFolder(uri) }
                Log.i(TAG, "Imagen $uri; reemplazable: ${originalTarget?.uri}")
                withContext(Dispatchers.Default) {
                    // Se conserva aparte para "comparar con la original", aunque luego se combinen capas.
                    originalSources = renderer.prepareSources(bitmap, BaseGeometry(bitmap.width, bitmap.height, 1f, 0f, 0f))
                }
                contentProcessor.clear()
                renderer.clearCache()
                bases.clear()
                bases[0] = bitmap
                nextBase = 1
                // Se conservan la resolución y el modo elegidos; se reinicia todo lo demás.
                val start = EditState(fit = _uiState.value.committed.fit.copy(transform = FrameTransform()))
                history.clear()
                history += start
                historyTools.clear()
                historyTools += null
                historyIndex = 0
                built.value = null
                _uiState.update {
                    it.copy(
                        hasImage = true,
                        savedEdit = start,
                        showOnWallpaper = fromWallpapers,
                        sourceUri = uri,
                        sourceFolderName = sourceFolder?.trimEnd('/')?.substringAfterLast('/'),
                        originalWidth = bitmap.width,
                        originalHeight = bitmap.height,
                        baseGeometry = null,
                        committed = start,
                        working = start,
                        activeTool = null,
                        selectedParam = null,
                        preview = null,
                        baseView = null,
                        // Las versiones reducidas las pone observeBase al publicar la foto.
                        originalPreview = null,
                        expandDraft = null,
                        showOriginal = false,
                        originalName = originalTarget?.displayName,
                        saveMode = SaveMode.COPY,
                    ).withHistoryFlags()
                }
                original.value = bitmap
                RecentEdits.get(getApplication()).add(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                showMessage(R.string.msg_oom)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo cargar $uri", e)
                showMessage(R.string.msg_load_error)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Cierra la foto y vuelve al inicio. */
    fun closeImage() {
        original.value = null
        built.value = null
        bases.clear()
        contentProcessor.clear()
        originalTarget = null
        _uiState.update { it.copy(hasImage = false, activeTool = null, preview = null, baseView = null, originalPreview = null) }
    }

    // endregion

    // region Herramientas

    fun openTool(tool: Tool) {
        _uiState.update {
            // Como Snapseed, los efectos se abren ya aplicados con un valor de partida.
            var adjustments = it.committed.adjustments
            if (adjustments.isDefault(tool)) {
                AdjustParam.forTool(tool).forEach { param -> param.initial?.let { v -> adjustments = adjustments.with(param, v) } }
            }
            it.copy(
                activeTool = tool,
                healRedo = emptyList(),
                selectedParam = AdjustParam.forTool(tool).firstOrNull(),
                working = it.committed.copy(adjustments = adjustments),
                selectedLayer = it.committed.layers.lastOrNull()?.id,
            ).withResolutionTexts(it.committed.fit.size)
        }
    }

    fun cancelTool() {
        _uiState.update {
            it.copy(activeTool = null, selectedParam = null, working = it.committed, healRedo = emptyList())
                .withResolutionTexts(it.committed.fit.size)
        }
    }

    fun applyTool() {
        val state = _uiState.value
        if (state.activeTool == Tool.LAYERS && state.working.layers.isNotEmpty()) {
            flattenLayers(state)
            return
        }
        if (state.working != state.committed) pushHistory(state.working, state.activeTool)
        _uiState.update {
            it.copy(activeTool = null, selectedParam = null, committed = it.working)
                .withResolutionTexts(it.working.fit.size)
                .withHistoryFlags()
        }
    }

    fun resetTool() {
        _uiState.update { state ->
            when (val tool = state.activeTool) {
                null -> state
                Tool.FIT -> state.copy(
                    working = state.working.copy(fit = FitSettings(size = state.working.fit.size)),
                )
                Tool.FILTERS -> state.copy(working = state.working.copy(filter = FilterSettings()))
                Tool.EXPAND -> state.updateContent {
                    it.copy(expansion = Expansion(fill = it.expansion.fill, detail = it.expansion.detail))
                }
                Tool.HEAL -> state.updateContent { it.copy(heals = emptyList()) }
                Tool.CROP -> state.updateContent { it.copy(crop = CropRect()) }.copy(cropAspect = CropAspect.FREE)
                Tool.CURVES -> state.copy(working = state.working.copy(curves = Curves()))
                Tool.LAYERS -> state.copy(working = state.working.copy(layers = emptyList()), selectedLayer = null)
                Tool.BRUSH -> state.copy(working = state.working.copy(brush = emptyList()))
                Tool.LENS_BLUR -> state.copy(
                    working = state.working.copy(
                        lens = LensBlur(shape = state.working.lens.shape),
                        adjustments = state.working.adjustments.reset(tool).with(AdjustParam.LENS_STRENGTH, 50f),
                    ),
                )
                else -> state.copy(working = state.working.copy(adjustments = state.working.adjustments.reset(tool)))
            }
        }
    }

    fun selectParam(param: AdjustParam) {
        _uiState.update { it.copy(selectedParam = param) }
    }

    /** Arrastre horizontal sobre la imagen: cambia el parámetro seleccionado en [delta] unidades. */
    fun adjustSelectedBy(delta: Float) {
        _uiState.update { state ->
            val param = state.selectedParam ?: return@update state
            val adjustments = state.working.adjustments
            state.copy(working = state.working.copy(adjustments = adjustments.with(param, adjustments[param] + delta)))
        }
    }

    fun setParamValue(param: AdjustParam, value: Float) {
        _uiState.update { state ->
            state.copy(
                selectedParam = param,
                working = state.working.copy(adjustments = state.working.adjustments.with(param, value)),
            )
        }
    }

    // Recortar

    /** Arrastre del rectángulo de recorte; [dx]/[dy] en fracciones del ancho/alto de la foto. */
    fun onCropDrag(handle: CropHandle, dx: Float, dy: Float) {
        _uiState.update { state ->
            val crop = state.working.content.crop
            var left = crop.left
            var top = crop.top
            var right = crop.right
            var bottom = crop.bottom
            when (handle) {
                CropHandle.MOVE -> {
                    val width = right - left
                    val height = bottom - top
                    left = (left + dx).coerceIn(0f, 1f - width)
                    top = (top + dy).coerceIn(0f, 1f - height)
                    right = left + width
                    bottom = top + height
                }
                CropHandle.LEFT -> left += dx
                CropHandle.RIGHT -> right += dx
                CropHandle.TOP -> top += dy
                CropHandle.BOTTOM -> bottom += dy
                CropHandle.TOP_LEFT -> { left += dx; top += dy }
                CropHandle.TOP_RIGHT -> { right += dx; top += dy }
                CropHandle.BOTTOM_LEFT -> { left += dx; bottom += dy }
                CropHandle.BOTTOM_RIGHT -> { right += dx; bottom += dy }
            }
            left = left.coerceIn(0f, right - CropRect.MIN_SIZE)
            right = right.coerceIn(left + CropRect.MIN_SIZE, 1f)
            top = top.coerceIn(0f, bottom - CropRect.MIN_SIZE)
            bottom = bottom.coerceIn(top + CropRect.MIN_SIZE, 1f)
            val rect = CropRect(left, top, right, bottom)
            val ratio = normalizedCropRatio(state)
            val fixed = if (ratio == null || handle == CropHandle.MOVE) rect else conformToRatio(rect, ratio, handle)
            state.updateContent { it.copy(crop = fixed) }
        }
    }

    fun selectCropAspect(aspect: CropAspect) {
        _uiState.update { state ->
            val withAspect = state.copy(cropAspect = aspect)
            val ratio = normalizedCropRatio(withAspect) ?: return@update withAspect
            withAspect.updateContent { it.copy(crop = conformToRatio(it.crop, ratio, null)) }
        }
    }

    /** Proporción ancho/alto del recorte expresada en fracciones de la foto (no en píxeles). */
    private fun normalizedCropRatio(state: EditorUiState): Float? {
        if (state.originalWidth <= 0 || state.originalHeight <= 0) return null
        val pixelRatio = when (state.cropAspect) {
            CropAspect.FREE -> return null
            CropAspect.ORIGINAL -> state.originalWidth.toFloat() / state.originalHeight
            CropAspect.SCREEN -> state.working.fit.size.ratio
            else -> state.cropAspect.ratio ?: return null
        }
        return pixelRatio * state.originalHeight / state.originalWidth
    }

    /**
     * Ajusta [rect] a la proporción [ratio] manteniendo fijo el punto que corresponde al tirador:
     * la esquina opuesta, el lado opuesto (centrado en el otro eje) o el centro si [handle] es null.
     * Si no cabe en la foto, se reduce manteniendo la proporción.
     */
    private fun conformToRatio(rect: CropRect, ratio: Float, handle: CropHandle?): CropRect {
        var width = rect.widthFraction
        var height = rect.heightFraction
        val heightDriven = handle == CropHandle.TOP || handle == CropHandle.BOTTOM
        if (heightDriven) {
            width = height * ratio
        } else if (handle == null) {
            if (width / height > ratio) width = height * ratio else height = width / ratio
        } else {
            height = width / ratio
        }
        // Punto fijo y hacia dónde crece el rectángulo en cada eje (-1 izquierda/arriba, 0 centrado, 1 derecha/abajo).
        val (anchorX, growX) = when (handle) {
            CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT, CropHandle.LEFT -> rect.right to -1
            CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT, CropHandle.RIGHT -> rect.left to 1
            else -> (rect.left + rect.right) / 2f to 0
        }
        val (anchorY, growY) = when (handle) {
            CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT, CropHandle.TOP -> rect.bottom to -1
            CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT, CropHandle.BOTTOM -> rect.top to 1
            else -> (rect.top + rect.bottom) / 2f to 0
        }
        val maxWidth = when (growX) { -1 -> anchorX; 1 -> 1f - anchorX; else -> 2f * minOf(anchorX, 1f - anchorX) }
        val maxHeight = when (growY) { -1 -> anchorY; 1 -> 1f - anchorY; else -> 2f * minOf(anchorY, 1f - anchorY) }
        val fit = minOf(1f, maxWidth / width, maxHeight / height)
        width *= fit
        height *= fit
        val left = when (growX) { -1 -> anchorX - width; 1 -> anchorX; else -> anchorX - width / 2f }
        val top = when (growY) { -1 -> anchorY - height; 1 -> anchorY; else -> anchorY - height / 2f }
        return CropRect(left, top, left + width, top + height)
    }

    // Expandir

    /** [delta] en fracción del lado de la foto: positivo añade lienzo en ese lado. */
    fun onExpandDrag(side: Side, delta: Float) {
        _uiState.update { state ->
            state.updateContent { it.copy(expansion = it.expansion.with(side, it.expansion[side] + delta)) }
        }
    }

    fun onExpandDetailSelected(detail: FillDetail) {
        _uiState.update { state -> state.updateContent { it.copy(expansion = it.expansion.copy(detail = detail)) } }
    }

    fun onExpandFillSelected(fill: ExpandFill) {
        _uiState.update { state -> state.updateContent { it.copy(expansion = it.expansion.copy(fill = fill)) } }
    }

    /** Expande lo justo, repartido a ambos lados, para que la foto tenga la proporción de la pantalla. */
    fun fitExpansionToScreen() {
        _uiState.update { state ->
            val width = state.croppedWidth
            val height = state.croppedHeight
            if (width <= 0 || height <= 0) return@update state
            val photoRatio = width.toFloat() / height
            val targetRatio = state.working.fit.size.ratio
            val current = state.working.content.expansion
            val expansion = if (photoRatio > targetRatio) {
                val extra = ((width / targetRatio - height) / height / 2f).coerceIn(0f, Expansion.MAX)
                Expansion(top = extra, bottom = extra, fill = current.fill, detail = current.detail)
            } else {
                val extra = ((height * targetRatio - width) / width / 2f).coerceIn(0f, Expansion.MAX)
                Expansion(left = extra, right = extra, fill = current.fill, detail = current.detail)
            }
            state.updateContent { it.copy(expansion = expansion) }
                .let { it.copy(working = it.working.copy(fit = it.working.fit.copy(transform = FrameTransform()))) }
        }
    }

    // Corrector

    fun addHealStroke(stroke: HealStroke) {
        _uiState.update { state ->
            // Un trazo nuevo descarta lo que se podía rehacer.
            state.updateContent { it.copy(heals = it.heals + stroke.copy(engine = state.healEngine)) }.copy(healRedo = emptyList())
        }
    }

    fun setHealShape(shape: HealShape) {
        _uiState.update { it.copy(healShape = shape) }
    }

    fun setHealEngine(engine: HealEngine) {
        _uiState.update { it.copy(healEngine = engine) }
    }

    /**
     * Busca textos (firmas, nombres, marcas de agua) en la imagen y los marca para borrarlos,
     * uno por línea, con un pequeño margen para cubrir sombras y contornos.
     */
    fun detectText() {
        val state = _uiState.value
        val set = built.value?.set ?: return
        val geometry = state.baseGeometry ?: return
        if (state.isDetectingText) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingText = true) }
            try {
                val image = BitmapScaler.downscaleToMaxSide(set.full, TEXT_DETECTION_SIDE)
                val lines = withContext(Dispatchers.Default) {
                    TextDetector.withNearbyMarks(image, TextDetector.detectLines(image))
                }
                val basePerImage = geometry.width.toFloat() / image.width
                val strokes = lines.map { found ->
                    val box = found.box
                    val padding = found.lineHeight * basePerImage * TEXT_PADDING / geometry.scale
                    // Margen extra a los lados: símbolos como © o iconos junto al nombre no se
                    // reconocen como parte de la línea.
                    val side = found.lineHeight * TEXT_SIDE_PADDING
                    HealStroke(
                        points = listOf(
                            ((box.left - side) * basePerImage - geometry.offsetX) / geometry.scale,
                            (box.top * basePerImage - geometry.offsetY) / geometry.scale,
                            ((box.right + side) * basePerImage - geometry.offsetX) / geometry.scale,
                            (box.bottom * basePerImage - geometry.offsetY) / geometry.scale,
                        ),
                        radius = padding,
                        shape = HealShape.TEXT,
                        engine = HealEngine.AI,
                        textColor = found.color,
                    )
                }
                if (strokes.isEmpty()) {
                    showMessage(R.string.msg_no_text)
                } else {
                    _uiState.update { s -> s.updateContent { it.copy(heals = it.heals + strokes) }.copy(healRedo = emptyList()) }
                    showMessage(R.string.msg_text_found, strokes.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error al detectar texto", e)
                showMessage(R.string.msg_text_error)
            } finally {
                _uiState.update { it.copy(isDetectingText = false) }
            }
        }
    }

    fun undoHealStroke() {
        _uiState.update { state ->
            val heals = state.working.content.heals
            if (heals.size <= state.committed.content.heals.size) return@update state
            state.updateContent { it.copy(heals = heals.dropLast(1)) }.copy(healRedo = state.healRedo + heals.last())
        }
    }

    /** Vuelve a poner el último trazo deshecho del Corrector. */
    fun redoHealStroke() {
        _uiState.update { state ->
            val stroke = state.healRedo.lastOrNull() ?: return@update state
            state.updateContent { it.copy(heals = it.heals + stroke) }.copy(healRedo = state.healRedo.dropLast(1))
        }
    }

    fun setBrushSize(size: Float) {
        _uiState.update { it.copy(brushSize = size.coerceIn(EditorUiState.MIN_BRUSH, EditorUiState.MAX_BRUSH)) }
    }

    // Encuadre

    fun onWidthChange(text: String) {
        _uiState.update { applyResolutionText(it.copy(widthText = sanitize(text))) }
    }

    fun onHeightChange(text: String) {
        _uiState.update { applyResolutionText(it.copy(heightText = sanitize(text))) }
    }

    fun selectDeviceSize() {
        _uiState.update { state ->
            updateFit(state.withResolutionTexts(deviceSize)) { it.copy(size = deviceSize) }
        }
    }

    fun selectPreset(preset: ResolutionPreset) {
        _uiState.update { state ->
            updateFit(state.withResolutionTexts(preset.size)) { it.copy(size = preset.size) }
        }
    }

    fun onFrameFillSelected(fill: FrameFill) {
        _uiState.update { state -> updateFit(state) { it.copy(fill = fill) } }
    }

    /**
     * Gesto libre sobre el recuadro, en píxeles de pantalla: [centroidX]/[centroidY] respecto al
     * centro del recuadro, [panX]/[panY] desplazamiento, [zoom] factor y [rotation] grados.
     * La imagen gira y escala alrededor del punto entre los dedos, como en cualquier visor.
     */
    fun onFrameGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        frameWidth: Float,
        frameHeight: Float,
        snapDistance: Float,
    ) {
        if (frameWidth <= 0f || frameHeight <= 0f) return
        updateTransform { state, current ->
            // El gesto se acumula sobre la posición sin imán; así basta con seguir arrastrando
            // para despegar la imagen de un borde.
            val t = rawFrameTransform ?: current
            val moved = transformByGesture(t, centroidX, centroidY, panX, panY, zoom, rotation, frameWidth, frameHeight)
            rawFrameTransform = moved
            if (state.frameMagnet) snapToFrame(state, moved, snapDistance * state.working.fit.size.width / frameWidth) else moved
        }
    }

    /** Imán a bordes y centro al arrastrar en Encuadre (desactivado: movimiento libre). */
    fun setFrameMagnet(enabled: Boolean) {
        _uiState.update { it.copy(frameMagnet = enabled) }
    }

    /** Zoom fino: [steps] puntos de porcentaje (sobre el centro de la imagen). */
    fun zoomFrameStep(steps: Int) {
        updateTransform { _, t ->
            val percent = (t.scale * 100f).roundToInt() + steps
            t.copy(scale = (percent / 100f).coerceIn(FrameTransform.MIN_SCALE, FrameTransform.MAX_SCALE))
        }
    }

    /** Giro fino de [degrees] grados, redondeado a décimas. */
    fun rotateFrameStep(degrees: Float) {
        updateTransform { _, t ->
            t.copy(rotation = normalizeDegrees(((t.rotation + degrees) * 10f).roundToInt() / 10f))
        }
    }

    /** Mueve la imagen [dx], [dy] píxeles del resultado (ajuste fino con las flechas). */
    fun nudgeFrame(dx: Int, dy: Int) {
        updateTransform { state, t ->
            val size = state.working.fit.size
            t.copy(offsetX = t.offsetX + dx.toFloat() / size.width, offsetY = t.offsetY + dy.toFloat() / size.height)
        }
    }

    /** Fin del gesto en el recuadro: el siguiente empieza desde la posición (ya pegada) actual. */
    fun onFrameGestureEnd() {
        rawFrameTransform = null
    }

    /** Pega la imagen a un lado o al centro del recuadro sin cambiar zoom ni giro. */
    fun alignFrame(align: FrameAlign) {
        updateTransform { state, t ->
            val bounds = imageBounds(state, t) ?: return@updateTransform t
            val size = state.working.fit.size
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            when (align) {
                FrameAlign.LEFT -> t.copy(offsetX = t.offsetX - bounds.left / width)
                FrameAlign.CENTER_HORIZONTAL -> t.copy(offsetX = t.offsetX - (bounds.centerX() - width / 2f) / width)
                FrameAlign.RIGHT -> t.copy(offsetX = t.offsetX - (bounds.right - width) / width)
                FrameAlign.TOP -> t.copy(offsetY = t.offsetY - bounds.top / height)
                FrameAlign.CENTER_VERTICAL -> t.copy(offsetY = t.offsetY - (bounds.centerY() - height / 2f) / height)
                FrameAlign.BOTTOM -> t.copy(offsetY = t.offsetY - (bounds.bottom - height) / height)
            }
        }
    }

    /** Caja de la imagen (girada) en píxeles del lienzo destino. */
    private fun imageBounds(state: EditorUiState, t: FrameTransform): RectF? {
        val geometry = state.baseGeometry ?: return null
        val size = state.working.fit.size
        return Geometry.imageBounds(geometry.width, geometry.height, size.width.toFloat(), size.height.toFloat(), t)
    }

    /**
     * Imán: si un borde de la imagen (o su centro) queda a menos de [threshold] píxeles del borde
     * (o del centro) del recuadro, se ajusta para que coincidan exactamente.
     */
    private fun snapToFrame(state: EditorUiState, t: FrameTransform, threshold: Float): FrameTransform {
        val bounds = imageBounds(state, t) ?: return t
        val size = state.working.fit.size
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val dx = listOf(bounds.left, bounds.right - width, bounds.centerX() - width / 2f)
            .filter { abs(it) < threshold }.minByOrNull { abs(it) } ?: 0f
        val dy = listOf(bounds.top, bounds.bottom - height, bounds.centerY() - height / 2f)
            .filter { abs(it) < threshold }.minByOrNull { abs(it) } ?: 0f
        return t.copy(offsetX = t.offsetX - dx / width, offsetY = t.offsetY - dy / height)
    }

    private fun transformByGesture(
        t: FrameTransform,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        frameWidth: Float,
        frameHeight: Float,
    ): FrameTransform {
        val scale = (t.scale * zoom).coerceIn(FrameTransform.MIN_SCALE, FrameTransform.MAX_SCALE)
        val appliedZoom = scale / t.scale
        val radians = Math.toRadians(rotation.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        // Vector del punto de los dedos al centro de la imagen, escalado y girado.
        val dx = (t.offsetX * frameWidth - centroidX) * appliedZoom
        val dy = (t.offsetY * frameHeight - centroidY) * appliedZoom
        val centerX = centroidX + dx * cos - dy * sin + panX
        val centerY = centroidY + dx * sin + dy * cos + panY
        return t.copy(
            scale = scale,
            offsetX = centerX / frameWidth,
            offsetY = centerY / frameHeight,
            rotation = normalizeDegrees(t.rotation + rotation),
        )
    }

    /** Imagen entera dentro del recuadro; lo que sobre se rellena. */
    fun frameFit() {
        updateTransform { state, t ->
            val geometry = state.baseGeometry ?: return@updateTransform t
            val size = state.working.fit.size
            FrameTransform(
                scale = Geometry.fitScale(geometry.width, geometry.height, size.width.toFloat(), size.height.toFloat(), t.rotation),
                rotation = t.rotation,
                flipHorizontal = t.flipHorizontal,
            )
        }
    }

    /** La imagen cubre todo el recuadro, centrada y sin huecos. */
    fun frameFill() {
        updateTransform { state, t ->
            val geometry = state.baseGeometry ?: return@updateTransform t
            val size = state.working.fit.size
            FrameTransform(
                scale = Geometry.fillScale(geometry.width, geometry.height, size.width.toFloat(), size.height.toFloat(), t.rotation),
                rotation = t.rotation,
                flipHorizontal = t.flipHorizontal,
            )
        }
    }

    fun frameRotate90() {
        updateTransform { _, t -> t.copy(rotation = normalizeDegrees((t.rotation / 90f).roundToInt() * 90f + 90f)) }
        frameFill()
    }

    fun frameFlip() {
        updateTransform { _, t -> t.copy(flipHorizontal = !t.flipHorizontal) }
    }

    private fun updateTransform(change: (EditorUiState, FrameTransform) -> FrameTransform) {
        _uiState.update { state -> updateFit(state) { it.copy(transform = change(state, it.transform)) } }
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    // Curvas

    fun selectCurveChannel(channel: CurveChannel) {
        _uiState.update { it.copy(curveChannel = channel) }
    }

    fun setCurve(channel: CurveChannel, points: List<CurvePoint>) {
        _uiState.update { it.copy(working = it.working.copy(curves = it.working.curves.with(channel, points))) }
    }

    fun applyCurvePreset(preset: CurvePreset) {
        _uiState.update {
            it.copy(
                curveChannel = CurveChannel.RGB,
                working = it.working.copy(curves = Curves().with(CurveChannel.RGB, preset.points)),
            )
        }
    }

    // Pincel

    fun selectBrushEffect(effect: BrushEffect) {
        _uiState.update { it.copy(brushEffect = effect) }
    }

    fun setBrushValue(value: Float) {
        _uiState.update { it.copy(brushValue = value.coerceIn(-100f, 100f)) }
    }

    /** Trazo de pincel ya convertido a coordenadas de la foto original (ver [HealStroke]). */
    fun addBrushStroke(stroke: HealStroke) {
        _uiState.update { state ->
            val brush = BrushStroke(stroke.points, stroke.radius, state.brushEffect, state.brushValue)
            state.copy(working = state.working.copy(brush = state.working.brush + brush))
        }
    }

    fun undoBrushStroke() {
        _uiState.update { state ->
            if (state.working.brush.size <= state.committed.brush.size) return@update state
            state.copy(working = state.working.copy(brush = state.working.brush.dropLast(1)))
        }
    }

    // Desenfoque de lente

    fun setLensShape(shape: LensShape) {
        _uiState.update { it.copy(working = it.working.copy(lens = it.working.lens.copy(shape = shape))) }
    }

    /**
     * Gesto sobre la vista previa: [panX]/[panY] mueven el centro (fracción del lienzo), [zoom]
     * cambia el tamaño de la zona enfocada y [rotation] (grados) gira la franja lineal.
     */
    fun onLensGesture(panX: Float, panY: Float, zoom: Float, rotation: Float) {
        _uiState.update { state ->
            val lens = state.working.lens
            state.copy(
                working = state.working.copy(
                    lens = lens.copy(
                        centerX = (lens.centerX + panX).coerceIn(0f, 1f),
                        centerY = (lens.centerY + panY).coerceIn(0f, 1f),
                        size = (lens.size * zoom).coerceIn(LensBlur.MIN_SIZE, LensBlur.MAX_SIZE),
                        angle = normalizeDegrees(lens.angle + rotation),
                    ),
                ),
            )
        }
    }

    // Capas

    /** Añade una imagen encima, entera dentro del lienzo y centrada; queda seleccionada. */
    fun addLayer(uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { LayerImages.load(getApplication(), uri.toString()) }
                _uiState.update { state ->
                    val size = state.working.fit.size
                    val aspect = bitmap.height.toFloat() / bitmap.width
                    val layer = ImageLayer(id = System.nanoTime(), uri = uri.toString(), aspect = aspect)
                        .let { it.copy(width = fitWidth(it, size)) }
                    state.copy(
                        working = state.working.copy(layers = state.working.layers + layer),
                        selectedLayer = layer.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo añadir la capa $uri", e)
                showMessage(R.string.msg_load_error)
            }
        }
    }

    fun selectLayer(id: Long?) {
        _uiState.update { it.copy(selectedLayer = id) }
    }

    /**
     * Gesto sobre la capa elegida: [panX]/[panY] en fracciones del lienzo, [zoom] factor y
     * [rotation] en grados.
     */
    fun onLayerGesture(panX: Float, panY: Float, zoom: Float, rotation: Float) {
        updateSelectedLayer { layer, _ ->
            layer.copy(
                centerX = (layer.centerX + panX).coerceIn(-0.5f, 1.5f),
                centerY = (layer.centerY + panY).coerceIn(-0.5f, 1.5f),
                width = (layer.width * zoom).coerceIn(ImageLayer.MIN_WIDTH, ImageLayer.MAX_WIDTH),
                rotation = normalizeDegrees(layer.rotation + rotation),
            )
        }
    }

    fun setLayerOpacity(opacity: Float) {
        updateSelectedLayer { layer, _ -> layer.copy(opacity = opacity.coerceIn(0f, 1f)) }
    }

    fun layerAction(action: LayerAction) {
        _uiState.update { state ->
            val layers = state.working.layers
            val index = layers.indexOfFirst { it.id == state.selectedLayer }
            if (index < 0) return@update state
            val layer = layers[index]
            val size = state.working.fit.size
            val updated: List<ImageLayer> = when (action) {
                LayerAction.FIT -> layers.replaced(index, layer.copy(centerX = 0.5f, centerY = 0.5f, rotation = 0f, width = fitWidth(layer, size)))
                LayerAction.FILL -> layers.replaced(index, layer.copy(centerX = 0.5f, centerY = 0.5f, rotation = 0f, width = fillWidth(layer, size)))
                LayerAction.CENTER -> layers.replaced(index, layer.copy(centerX = 0.5f, centerY = 0.5f))
                LayerAction.FLIP -> layers.replaced(index, layer.copy(flipHorizontal = !layer.flipHorizontal))
                LayerAction.FRONT -> layers.toMutableList().apply { removeAt(index); add(layer) }
                LayerAction.BACK -> layers.toMutableList().apply { removeAt(index); add(0, layer) }
                LayerAction.DELETE -> layers.toMutableList().apply { removeAt(index) }
            }
            state.copy(
                working = state.working.copy(layers = updated),
                selectedLayer = if (action == LayerAction.DELETE) updated.lastOrNull()?.id else state.selectedLayer,
            )
        }
    }

    private fun updateSelectedLayer(change: (ImageLayer, EditorUiState) -> ImageLayer) {
        _uiState.update { state ->
            val layers = state.working.layers
            val index = layers.indexOfFirst { it.id == state.selectedLayer }
            if (index < 0) return@update state
            state.copy(working = state.working.copy(layers = layers.replaced(index, change(layers[index], state))))
        }
    }

    private fun <T> List<T>.replaced(index: Int, item: T): List<T> = toMutableList().also { it[index] = item }

    /** Ancho (fracción del lienzo) para que la capa quepa entera. */
    private fun fitWidth(layer: ImageLayer, size: TargetSize): Float =
        minOf(1f, size.height.toFloat() / (layer.aspect * size.width))

    /** Ancho para que la capa cubra todo el lienzo (útil para sustituir la foto por otra). */
    private fun fillWidth(layer: ImageLayer, size: TargetSize): Float =
        maxOf(1f, size.height.toFloat() / (layer.aspect * size.width))

    // Filtros

    fun selectFilter(preset: FilterPreset) {
        _uiState.update { it.copy(working = it.working.copy(filter = it.working.filter.copy(preset = preset))) }
    }

    fun setFilterIntensity(intensity: Float) {
        _uiState.update {
            it.copy(working = it.working.copy(filter = it.working.filter.copy(intensity = intensity.coerceIn(0f, 100f))))
        }
    }

    // endregion

    // region Historial y comparación

    fun undo() {
        if (historyIndex == 0 || _uiState.value.activeTool != null) return
        historyIndex--
        restoreHistory()
    }

    fun redo() {
        if (historyIndex >= history.lastIndex || _uiState.value.activeTool != null) return
        historyIndex++
        restoreHistory()
    }

    fun setShowOriginal(show: Boolean) {
        _uiState.update { it.copy(showOriginal = show) }
    }

    /** Salta a un paso del historial (como "Ver ediciones" de Snapseed). */
    fun goToHistory(index: Int) {
        if (index !in history.indices || _uiState.value.activeTool != null) return
        historyIndex = index
        restoreHistory()
    }

    fun showHistorySheet(show: Boolean) {
        _uiState.update { it.copy(showHistorySheet = show) }
    }

    private fun pushHistory(state: EditState, tool: Tool?) {
        while (history.lastIndex > historyIndex) {
            history.removeAt(history.lastIndex)
            historyTools.removeAt(historyTools.lastIndex)
        }
        history += state
        historyTools += tool
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
            historyTools.removeAt(0)
        }
        historyIndex = history.lastIndex
        pruneBases()
    }

    private fun restoreHistory() {
        val state = history[historyIndex]
        _uiState.update {
            it.copy(committed = state, working = state).withResolutionTexts(state.fit.size).withHistoryFlags()
        }
    }

    private fun EditorUiState.withHistoryFlags(): EditorUiState {
        // Ojo: dentro de esta extensión "historyIndex" a secas sería el del estado de la UI.
        val index = this@EditorViewModel.historyIndex
        return copy(
            canUndo = index > 0,
            canRedo = index < history.lastIndex,
            historySteps = historyTools.toList(),
            historyIndex = index,
        )
    }

    // endregion

    // region Exportación

    fun showExportSheet(show: Boolean) {
        val app = getApplication<Application>()
        val live = WallpaperController.isLiveWallpaperActive(app)
        _uiState.update { it.copy(showExportSheet = show, liveWallpaperActive = live) }
    }

    fun onFormatSelected(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun onStoragePermissionDenied() {
        showMessage(R.string.msg_permission_denied)
    }

    fun saveToGallery() {
        val format = _uiState.value.exportFormat
        val folder = saveFolder
        // Sin carpeta elegida, la copia va junto a la foto original (si se sabe dónde está).
        val relativePath = if (folder == null) sourceFolder else null
        val folderLabel = _uiState.value.saveFolderName
            ?: _uiState.value.sourceFolderName
            ?: getApplication<Application>().getString(R.string.save_folder_default)
        export { bitmap, size ->
            try {
                GallerySaver.save(getApplication<Application>(), bitmap, format, folder, relativePath).also {
                    copyTags(it)
                    showSavedOnWallpaper(it)
                }
            } catch (e: SecurityException) {
                throw GallerySaver.FolderNotWritableException()
            }
            getApplication<Application>().getString(R.string.msg_saved, folderLabel, size.width, size.height)
        }
    }

    /** La copia guardada se queda con las etiquetas de la original (al reemplazar ya las conserva: mismo nombre). */
    private suspend fun copyTags(saved: Uri) {
        val source = _uiState.value.sourceUri ?: return
        val tags = PhotoTags.get(getApplication())
        val from = tags.nameOf(source) ?: return
        val to = tags.nameOf(saved) ?: return
        tags.copy(from, to)
    }

    /** Carpeta de la galería de la foto abierta ("Pictures/…/"), para guardar las copias a su lado. */
    private var sourceFolder: String? = null

    private fun folderOf(media: Uri): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            getApplication<Application>().contentResolver
                .query(media, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    /**
     * Sin permiso de fotos no se puede preguntar a la galería, pero las fotos de una carpeta de
     * Fondos llevan la ruta en su URI ("primary:Pictures/…/foto.jpg").
     */
    private fun documentFolder(uri: Uri): String? = try {
        if (!DocumentsContract.isDocumentUri(getApplication(), uri)) {
            null
        } else {
            DocumentsContract.getDocumentId(uri).takeIf { it.startsWith("primary:") }
                ?.removePrefix("primary:")?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }?.let { "$it/" }
        }
    } catch (e: Exception) {
        null
    }

    fun setShowOnWallpaper(show: Boolean) {
        _uiState.update { it.copy(showOnWallpaper = show) }
    }

    /** Tras guardar: si se pidió, la foto guardada pasa a ser la del fondo dinámico. */
    private suspend fun showSavedOnWallpaper(saved: Uri) {
        val state = _uiState.value
        if (state.showOnWallpaper && state.liveWallpaperActive) WallpaperController.show(getApplication(), saved.toString())
    }

    fun setSaveMode(mode: SaveMode) {
        _uiState.update { it.copy(saveMode = mode) }
    }

    /**
     * Reemplazar la foto original: primero Android pide permiso al usuario (Android 11+) y, si lo
     * concede, [onWriteGranted] escribe encima.
     */
    fun replaceOriginal() {
        val target = originalTarget ?: return showMessage(R.string.msg_replace_unavailable)
        val request = try {
            OriginalReplacer.writeRequest(getApplication(), target)
        } catch (e: IllegalArgumentException) {
            // Android no reconoce la foto como suya: mejor avisar que cerrarse.
            Log.e(TAG, "No se puede pedir permiso para ${target.uri}", e)
            return showMessage(R.string.msg_replace_unavailable)
        }
        if (request != null) {
            _writeRequests.tryEmit(request)
        } else {
            onWriteGranted()
        }
    }

    fun onWriteGranted() {
        val target = originalTarget ?: return
        export { bitmap, size ->
            try {
                OriginalReplacer.write(getApplication(), target, bitmap)
                // Si es una de las fotos del fondo dinámico, que la vuelva a leer.
                WallpaperController.onPhotosChanged()
                // Se pone con la misma URI con la que se abrió (la del pase si viene de Fondos).
                (_uiState.value.sourceUri ?: target.uri).let { showSavedOnWallpaper(it) }
            } catch (e: SecurityException) {
                // Android 10: el permiso se pide al intentar escribir.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _writeRequests.tryEmit(e.userAction.actionIntent.intentSender)
                    return@export null
                }
                throw e
            }
            getApplication<Application>().getString(R.string.msg_replaced, target.displayName, size.width, size.height)
        }
    }

    fun onWriteDenied() {
        showMessage(R.string.msg_replace_denied)
    }

    // Carpeta de guardado

    /** El selector del sistema ha devuelto una carpeta con permiso persistente de escritura. */
    fun setSaveFolder(folder: Uri) {
        saveFolder = folder
        preferences.edit().putString(KEY_SAVE_FOLDER, folder.toString()).apply()
        refreshSaveFolderName()
    }

    fun resetSaveFolder() {
        saveFolder = null
        preferences.edit().remove(KEY_SAVE_FOLDER).apply()
        _uiState.update { it.copy(saveFolderName = null) }
    }

    private fun refreshSaveFolderName() {
        val folder = saveFolder ?: return
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { GallerySaver.folderName(getApplication<Application>(), folder) }
            if (name == null) {
                // Se ha perdido el permiso (carpeta borrada o acceso revocado): volver a la predeterminada.
                resetSaveFolder()
            } else {
                _uiState.update { it.copy(saveFolderName = name) }
            }
        }
    }

    fun setAsWallpaper(target: WallpaperTarget) {
        export { bitmap, _ ->
            WallpaperSetter.apply(getApplication<Application>(), bitmap, target)
            getApplication<Application>().getString(R.string.msg_wallpaper_set)
        }
    }

    /** [action] devuelve el mensaje a mostrar, o null si la operación sigue en otro paso. */
    private fun export(action: suspend (Bitmap, TargetSize) -> String?) {
        val state = _uiState.value
        if (state.isExporting || state.isProcessing) return
        val current = built.value ?: return showMessage(R.string.msg_no_image)
        // Solo desde la imagen base del estado confirmado (no una que aún se está rehaciendo).
        if (current.key != state.sourceKey()) return
        val set = current.set
        val edit = state.committed

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val output = withContext(Dispatchers.Default) { renderer.renderFull(set, edit) }
                val message = try {
                    action(output, edit.fit.size)
                } finally {
                    output.recycle()
                }
                if (message != null) {
                    // Guardado: salir ya no pierde nada (hasta el siguiente cambio).
                    _uiState.update { it.copy(showExportSheet = false, savedEdit = edit) }
                    _messages.tryEmit(message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                showMessage(R.string.msg_oom)
            } catch (e: GallerySaver.FolderNotWritableException) {
                Log.e(TAG, "Carpeta de guardado sin acceso", e)
                showMessage(R.string.msg_folder_error)
            } catch (e: Exception) {
                Log.e(TAG, "Error al exportar", e)
                showMessage(R.string.msg_export_error, e.localizedMessage ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    // endregion

    override fun onCleared() {
        blurEngine.close()
        aiInpainter.close()
        super.onCleared()
    }

    private fun EditorUiState.updateContent(change: (ContentEdits) -> ContentEdits): EditorUiState =
        copy(working = working.copy(content = change(working.content)))

    private fun applyResolutionText(state: EditorUiState): EditorUiState {
        val size = EditorUiState.parseTargetSize(state.widthText, state.heightText) ?: return state
        return updateFit(state) { it.copy(size = size) }
    }

    private fun updateFit(state: EditorUiState, change: (FitSettings) -> FitSettings): EditorUiState =
        state.copy(working = state.working.copy(fit = change(state.working.fit)))

    private fun EditorUiState.withResolutionTexts(size: TargetSize): EditorUiState =
        copy(widthText = size.width.toString(), heightText = size.height.toString())

    private fun sanitize(text: String): String = text.filter(Char::isDigit).take(MAX_INPUT_DIGITS)

    private fun showMessage(@StringRes message: Int, vararg args: Any) {
        _messages.tryEmit(getApplication<Application>().getString(message, *args))
    }

    private companion object {
        const val TAG = "EditorViewModel"
        const val REFINE_DELAY_MS = 180L
        const val EXPAND_DRAFT_DELAY_MS = 250L
        const val MAX_HISTORY = 50
        const val MAX_INPUT_DIGITS = 5
        const val ORIGINAL_DRAFT_SIDE = 420

        /** Espera máxima a que la imagen base esté lista antes de combinar capas. */
        const val MERGE_WAIT_MS = 30_000L

        /** Combinar capas: hasta 3× el resultado y unos 12 MP como mucho. */
        const val MAX_MERGE_FACTOR = 3f
        const val MAX_MERGE_PIXELS = 12_000_000f
        const val THUMBNAIL_SIDE = 160
        const val PREFERENCES = "settings"
        const val KEY_SAVE_FOLDER = "save_folder"
        /** Lado mayor de la imagen que se pasa al detector de texto. */
        const val TEXT_DETECTION_SIDE = 2400
        /** Margen alrededor de cada línea detectada, en fracción de su altura. */
        const val TEXT_PADDING = 0.35f
        /** Margen adicional a izquierda y derecha, en alturas de línea. */
        const val TEXT_SIDE_PADDING = 1.1f
    }
}
