package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiMetadataSuggestion
import com.example.ai.GeminiStudioAssistant
import com.example.engine.CanvasRenderer
import com.example.engine.MediaExporter
import com.example.engine.MetadataWriter
import com.example.engine.TemplateGenerator
import com.example.model.ArtifactKind
import com.example.model.ArtworkMetadata
import com.example.model.CanvasLayer
import com.example.model.DrawingTool
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportResolution
import com.example.model.ExportResult
import com.example.model.LayerBlendMode
import com.example.model.LayerElement
import com.example.model.LayerMetadata
import com.example.model.MetadataAttribute
import com.example.model.NormalizedPoint
import com.example.model.ShapeKind
import com.example.model.StudioPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.max

data class ActiveDraftStroke(
    val points: List<NormalizedPoint>,
    val isEraser: Boolean
)

data class ActiveDraftShape(
    val kind: ShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Exporting : ExportUiState
    data class Success(val result: ExportResult) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

enum class AiStudioMode {
    METADATA_TAGGER,
    CREATIVE_COACH
}

sealed interface AiStudioUiState {
    data object Idle : AiStudioUiState
    data object Loading : AiStudioUiState
    data class Streaming(val text: String) : AiStudioUiState
    data class MetadataSuccess(val suggestion: AiMetadataSuggestion) : AiStudioUiState
    data class CoachSuccess(val text: String) : AiStudioUiState
    data class Error(val message: String) : AiStudioUiState
}

data class StudioUiState(
    val layers: List<CanvasLayer> = TemplateGenerator.createEmptyEightLayers(),
    val activeLayerIndex: Int = 3, // Layer 4 (Primary Subject) selected by default
    val activeTool: DrawingTool = DrawingTool.BRUSH,
    val selectedColor: Color = StudioPalette.quickSwatches.first(),
    val brushSizeFraction: Float = 0.022f,
    val brushOpacity: Float = 1f,
    val shapeFilled: Boolean = true,
    val showGridOverlay: Boolean = false,
    val artworkMetadata: ArtworkMetadata = ArtworkMetadata(),
    val exportOptions: ExportOptions = ExportOptions(),
    val exportUiState: ExportUiState = ExportUiState.Idle,
    val exportPreviewBitmap: Bitmap? = null,
    val estimatedSizeBytes: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val pendingTextPosition: NormalizedPoint? = null,
    val exportedArtifacts: List<ExportResult> = emptyList(),
    val statusBannerMessage: String? = null,
    val aiMode: AiStudioMode = AiStudioMode.METADATA_TAGGER,
    val aiUiState: AiStudioUiState = AiStudioUiState.Idle,
    val aiCanvasPreview: Bitmap? = null
) {
    val activeLayer: CanvasLayer
        get() = layers.getOrElse(activeLayerIndex) { layers.first() }
}

private data class HistorySnapshot(
    val layers: List<CanvasLayer>,
    val activeLayerIndex: Int,
    val artworkMetadata: ArtworkMetadata
)

private data class HistoryEntry(
    val label: String,
    val snapshot: HistorySnapshot,
    val coalesceKey: String? = null
)

class OctaCanvasViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _draftStroke = MutableStateFlow<ActiveDraftStroke?>(null)
    val draftStroke: StateFlow<ActiveDraftStroke?> = _draftStroke.asStateFlow()

    private val _draftShape = MutableStateFlow<ActiveDraftShape?>(null)
    val draftShape: StateFlow<ActiveDraftShape?> = _draftShape.asStateFlow()

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private val maxHistory = 40
    private var activeCoalesceKey: String? = null

    private var previewEstimateJob: Job? = null
    private var aiJob: Job? = null

    private companion object {
        const val AI_TAG = "OctaCanvasAI"
        const val AI_TIMEOUT_MS = 60_000L
    }

    private fun currentSnapshot(state: StudioUiState = _uiState.value): HistorySnapshot {
        return HistorySnapshot(
            layers = state.layers,
            activeLayerIndex = state.activeLayerIndex,
            artworkMetadata = state.artworkMetadata
        )
    }

    private fun pushUndoSnapshot(
        label: String,
        snapshot: HistorySnapshot = currentSnapshot(),
        coalesceKey: String? = null
    ) {
        if (coalesceKey != null && activeCoalesceKey == coalesceKey && undoStack.isNotEmpty()) {
            redoStack.clear()
            syncHistoryFlags()
            return
        }
        undoStack.addLast(HistoryEntry(label = label, snapshot = snapshot, coalesceKey = coalesceKey))
        while (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        activeCoalesceKey = coalesceKey
        redoStack.clear()
        syncHistoryFlags()
    }

    fun finishContinuousEdit() {
        activeCoalesceKey = null
    }

    private fun syncHistoryFlags() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                undoLabel = undoStack.lastOrNull()?.label,
                redoLabel = redoStack.lastOrNull()?.label
            )
        }
    }

    fun undo() {
        activeCoalesceKey = null
        val prevEntry = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(HistoryEntry(label = prevEntry.label, snapshot = currentSnapshot()))
        _uiState.update {
            it.copy(
                layers = prevEntry.snapshot.layers,
                activeLayerIndex = prevEntry.snapshot.activeLayerIndex.coerceIn(0, prevEntry.snapshot.layers.lastIndex),
                artworkMetadata = prevEntry.snapshot.artworkMetadata,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                undoLabel = undoStack.lastOrNull()?.label,
                redoLabel = redoStack.lastOrNull()?.label
            )
        }
    }

    fun redo() {
        activeCoalesceKey = null
        val nextEntry = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(HistoryEntry(label = nextEntry.label, snapshot = currentSnapshot()))
        _uiState.update {
            it.copy(
                layers = nextEntry.snapshot.layers,
                activeLayerIndex = nextEntry.snapshot.activeLayerIndex.coerceIn(0, nextEntry.snapshot.layers.lastIndex),
                artworkMetadata = nextEntry.snapshot.artworkMetadata,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                undoLabel = undoStack.lastOrNull()?.label,
                redoLabel = redoStack.lastOrNull()?.label
            )
        }
    }

    fun selectLayer(index: Int) {
        if (index in 0 until _uiState.value.layers.size) {
            _uiState.update { it.copy(activeLayerIndex = index) }
        }
    }

    fun selectTool(tool: DrawingTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun selectColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun setBrushSizeFraction(fraction: Float) {
        _uiState.update { it.copy(brushSizeFraction = fraction.coerceIn(0.004f, 0.10f)) }
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0.1f, 1f)) }
    }

    fun toggleShapeFilled() {
        _uiState.update { it.copy(shapeFilled = !it.shapeFilled) }
    }

    fun toggleGridOverlay() {
        _uiState.update { it.copy(showGridOverlay = !it.showGridOverlay) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Canvas Drawing Gestures ---

    fun onCanvasDragStart(point: NormalizedPoint) {
        val state = _uiState.value
        val active = state.activeLayer
        if (active.isLocked) {
            _uiState.update {
                it.copy(statusBannerMessage = "Layer \"${active.name}\" is locked. Unlock it to draw.")
            }
            return
        }
        if (!active.isVisible) {
            _uiState.update {
                it.copy(statusBannerMessage = "Layer \"${active.name}\" is hidden. Enable visibility to see strokes.")
            }
        }

        when (state.activeTool) {
            DrawingTool.BRUSH -> {
                _draftStroke.value = ActiveDraftStroke(listOf(point), isEraser = false)
            }
            DrawingTool.ERASER -> {
                _draftStroke.value = ActiveDraftStroke(listOf(point), isEraser = true)
            }
            DrawingTool.LINE -> {
                _draftShape.value = ActiveDraftShape(ShapeKind.LINE, point, point)
            }
            DrawingTool.RECTANGLE -> {
                _draftShape.value = ActiveDraftShape(ShapeKind.RECTANGLE, point, point)
            }
            DrawingTool.CIRCLE -> {
                _draftShape.value = ActiveDraftShape(ShapeKind.CIRCLE, point, point)
            }
            DrawingTool.FILL -> {
                applyFillToActiveLayer(state.selectedColor.toArgb())
            }
            DrawingTool.TEXT -> {
                _uiState.update { it.copy(pendingTextPosition = point) }
            }
        }
    }

    fun onCanvasDragMove(point: NormalizedPoint) {
        val stroke = _draftStroke.value
        if (stroke != null) {
            val last = stroke.points.lastOrNull()
            if (last == null ||
                kotlin.math.hypot((point.x - last.x).toDouble(), (point.y - last.y).toDouble()) > 0.0025
            ) {
                _draftStroke.value = stroke.copy(points = stroke.points + point)
            }
            return
        }

        val shape = _draftShape.value
        if (shape != null) {
            _draftShape.value = shape.copy(end = point)
        }
    }

    fun onCanvasDragEnd() {
        val state = _uiState.value
        val stroke = _draftStroke.value
        if (stroke != null) {
            _draftStroke.value = null
            if (stroke.points.isNotEmpty()) {
                val element = LayerElement.PathStroke(
                    points = stroke.points,
                    colorArgb = state.selectedColor.toArgb(),
                    strokeWidthFraction = state.brushSizeFraction,
                    opacity = state.brushOpacity,
                    isEraser = stroke.isEraser
                )
                appendElementToActiveLayer(element)
            }
            return
        }

        val shape = _draftShape.value
        if (shape != null) {
            _draftShape.value = null
            val element = LayerElement.ShapeElement(
                kind = shape.kind,
                start = shape.start,
                end = shape.end,
                colorArgb = state.selectedColor.toArgb(),
                strokeWidthFraction = state.brushSizeFraction,
                filled = state.shapeFilled,
                opacity = state.brushOpacity
            )
            appendElementToActiveLayer(element)
        }
    }

    fun onCanvasDragCancel() {
        _draftStroke.value = null
        _draftShape.value = null
    }

    fun requestTextPlacementAtCenter() {
        val active = _uiState.value.activeLayer
        if (active.isLocked) {
            _uiState.update {
                it.copy(statusBannerMessage = "Layer \"${active.name}\" is locked.")
            }
            return
        }
        _uiState.update { it.copy(pendingTextPosition = NormalizedPoint(0.5f, 0.5f)) }
    }

    fun dismissTextPlacement() {
        _uiState.update { it.copy(pendingTextPosition = null) }
    }

    fun commitTextElement(text: String, fontSizeFraction: Float, bold: Boolean) {
        val pos = _uiState.value.pendingTextPosition ?: NormalizedPoint(0.5f, 0.5f)
        _uiState.update { it.copy(pendingTextPosition = null) }
        val clean = text.trim()
        if (clean.isEmpty()) return

        val state = _uiState.value
        val element = LayerElement.TextElement(
            text = clean,
            position = pos,
            colorArgb = state.selectedColor.toArgb(),
            fontSizeFraction = fontSizeFraction.coerceIn(0.025f, 0.18f),
            bold = bold,
            opacity = state.brushOpacity
        )
        appendElementToActiveLayer(element)
    }

    fun importPhotoToActiveLayer(uri: Uri) {
        val active = _uiState.value.activeLayer
        if (active.isLocked) {
            _uiState.update {
                it.copy(statusBannerMessage = "Layer \"${active.name}\" is locked. Unlock it to import a photo.")
            }
            return
        }

        viewModelScope.launch {
            val loadedBitmap = withContext(Dispatchers.IO) {
                decodeScaledBitmap(uri, maxDimension = 1536)
            }
            if (loadedBitmap == null) {
                _uiState.update { it.copy(statusBannerMessage = "Could not decode selected image.") }
                return@launch
            }

            val element = LayerElement.ImageElement(
                bitmap = loadedBitmap,
                label = "Photo Import",
                centerX = 0.5f,
                centerY = 0.5f,
                scale = 0.88f,
                opacity = 1f
            )
            appendElementToActiveLayer(element)
            _uiState.update {
                it.copy(statusBannerMessage = "Image added to ${active.name}")
            }
        }
    }

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (max(origW, origH) / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val maxSide = max(decoded.width, decoded.height)
        if (maxSide <= maxDimension) return decoded

        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (resized != decoded) decoded.recycle()
        return resized
    }

    private fun appendElementToActiveLayer(element: LayerElement) {
        val current = _uiState.value
        val idx = current.activeLayerIndex
        val slot = current.layers[idx].slotNumber
        val label = when (element) {
            is LayerElement.PathStroke -> if (element.isEraser) "Erase on L$slot" else "Brush on L$slot"
            is LayerElement.ShapeElement -> "${element.kind.name.lowercase().replaceFirstChar { it.uppercase() }} on L$slot"
            is LayerElement.TextElement -> "Text on L$slot"
            is LayerElement.ImageElement -> "Photo on L$slot"
            is LayerElement.ArtifactElement -> "Artifact: ${element.kind.displayName} on L$slot"
        }
        pushUndoSnapshot(label = label, snapshot = currentSnapshot(current))

        val updatedLayers = current.layers.mapIndexed { i, layer ->
            if (i == idx) layer.copy(elements = layer.elements + element) else layer
        }
        _uiState.update { it.copy(layers = updatedLayers) }
    }

    fun addArtifactToActiveLayer(
        kind: ArtifactKind,
        intensity: Float,
        seed: Int,
        autoTagMetadata: Boolean
    ) {
        val current = _uiState.value
        val idx = current.activeLayerIndex
        val target = current.layers[idx]
        if (target.isLocked) {
            _uiState.update {
                it.copy(statusBannerMessage = "Layer \"${target.name}\" is locked. Unlock it to add artifacts.")
            }
            return
        }

        pushUndoSnapshot(
            label = "Artifact: ${kind.displayName} on L${target.slotNumber}",
            snapshot = currentSnapshot(current)
        )

        val element = LayerElement.ArtifactElement(
            kind = kind,
            colorArgb = current.selectedColor.toArgb(),
            intensity = intensity.coerceIn(0.1f, 1f),
            seed = seed,
            opacity = current.brushOpacity
        )

        val updatedMeta = if (autoTagMetadata) {
            val tagName = kind.displayName.lowercase().replace(" ", "_")
            target.metadata.copy(
                traitCategory = target.metadata.traitCategory.ifBlank { kind.defaultCategory },
                traitValue = kind.displayName,
                tags = if (tagName in target.metadata.tags) target.metadata.tags
                else target.metadata.tags + tagName
            )
        } else {
            target.metadata
        }

        val updatedLayers = current.layers.mapIndexed { i, layer ->
            if (i == idx) layer.copy(elements = layer.elements + element, metadata = updatedMeta) else layer
        }
        _uiState.update {
            it.copy(
                layers = updatedLayers,
                statusBannerMessage = "Added ${kind.displayName} artifact to L${target.slotNumber}"
            )
        }
    }

    fun applyFillToActiveLayer(colorArgb: Int) {
        val current = _uiState.value
        val active = current.activeLayer
        if (active.isLocked) {
            _uiState.update { it.copy(statusBannerMessage = "Layer \"${active.name}\" is locked.") }
            return
        }
        pushUndoSnapshot(label = "Fill L${active.slotNumber}", snapshot = currentSnapshot(current))
        val updatedLayers = current.layers.mapIndexed { i, layer ->
            if (i == current.activeLayerIndex) layer.copy(backgroundFillArgb = colorArgb) else layer
        }
        _uiState.update { it.copy(layers = updatedLayers) }
    }

    // --- Layer Stack Controls ---

    fun toggleLayerVisibility(index: Int) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        val action = if (target.isVisible) "Hide L${target.slotNumber}" else "Show L${target.slotNumber}"
        pushUndoSnapshot(label = action, snapshot = currentSnapshot(current))
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(isVisible = !layer.isVisible) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun toggleLayerLock(index: Int) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        val action = if (target.isLocked) "Unlock L${target.slotNumber}" else "Lock L${target.slotNumber}"
        pushUndoSnapshot(label = action, snapshot = currentSnapshot(current))
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(isLocked = !layer.isLocked) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun setLayerOpacity(
        index: Int,
        opacity: Float,
        isContinuousDrag: Boolean = false
    ) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val clamped = opacity.coerceIn(0f, 1f)
        val target = current.layers[index]
        if (kotlin.math.abs(target.opacity - clamped) < 0.0001f) return

        val coalesceKey = if (isContinuousDrag) "opacity_${target.id}" else null
        pushUndoSnapshot(
            label = "L${target.slotNumber} Opacity",
            snapshot = currentSnapshot(current),
            coalesceKey = coalesceKey
        )
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(opacity = clamped) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun setLayerBlendMode(index: Int, blendMode: LayerBlendMode) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        if (target.blendMode == blendMode) return
        pushUndoSnapshot(
            label = "L${target.slotNumber} Blend: ${blendMode.displayName}",
            snapshot = currentSnapshot(current)
        )
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(blendMode = blendMode) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun renameLayer(index: Int, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        if (target.name == clean) return
        pushUndoSnapshot(label = "Rename L${target.slotNumber}", snapshot = currentSnapshot(current))
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(name = clean) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun moveLayer(index: Int, delta: Int) {
        val current = _uiState.value
        val targetIndex = index + delta
        if (index !in current.layers.indices || targetIndex !in current.layers.indices) return

        val movedLayer = current.layers[index]
        val direction = if (delta > 0) "Up" else "Down"
        pushUndoSnapshot(label = "Move L${movedLayer.slotNumber} $direction", snapshot = currentSnapshot(current))

        val mutable = current.layers.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(targetIndex, moved)

        val newActive = when (current.activeLayerIndex) {
            index -> targetIndex
            targetIndex -> index
            else -> current.activeLayerIndex
        }
        _uiState.update { it.copy(layers = mutable, activeLayerIndex = newActive) }
    }

    fun clearLayer(index: Int) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        if (target.isLocked) {
            _uiState.update { it.copy(statusBannerMessage = "Unlock layer before clearing.") }
            return
        }
        if (!target.hasVisualContent) return
        pushUndoSnapshot(label = "Clear L${target.slotNumber}", snapshot = currentSnapshot(current))
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(backgroundFillArgb = null, elements = emptyList()) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun clearAllLayers() {
        val current = _uiState.value
        pushUndoSnapshot(label = "Reset All Layers", snapshot = currentSnapshot(current))
        _uiState.update {
            it.copy(
                layers = TemplateGenerator.createEmptyEightLayers(),
                activeLayerIndex = 3,
                statusBannerMessage = "Reset all 8 layers to blank canvas"
            )
        }
    }

    fun loadShowcaseTemplate() {
        val current = _uiState.value
        pushUndoSnapshot(label = "Load Demo Stack", snapshot = currentSnapshot(current))
        val (demoLayers, demoMeta) = TemplateGenerator.createShowcaseComposition()
        _uiState.update {
            it.copy(
                layers = demoLayers,
                artworkMetadata = demoMeta,
                activeLayerIndex = 3,
                exportOptions = it.exportOptions.copy(fileName = "neon_horizon_8layer"),
                statusBannerMessage = "Loaded 8-layer showcase with layer metadata"
            )
        }
    }

    // --- Metadata Editing ---

    fun updateArtworkMetadata(transform: (ArtworkMetadata) -> ArtworkMetadata) {
        val current = _uiState.value
        val updatedMeta = transform(current.artworkMetadata)
        if (updatedMeta == current.artworkMetadata) return
        pushUndoSnapshot(
            label = "Edit Artwork Metadata",
            snapshot = currentSnapshot(current),
            coalesceKey = "artwork_meta"
        )
        _uiState.update { it.copy(artworkMetadata = updatedMeta) }
    }

    fun addArtworkKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        val current = _uiState.value
        if (current.artworkMetadata.keywords.any { it.equals(clean, ignoreCase = true) }) return
        pushUndoSnapshot(label = "Add Keyword", snapshot = currentSnapshot(current))
        _uiState.update {
            it.copy(
                artworkMetadata = it.artworkMetadata.copy(
                    keywords = it.artworkMetadata.keywords + clean
                )
            )
        }
    }

    fun removeArtworkKeyword(keyword: String) {
        val current = _uiState.value
        if (keyword !in current.artworkMetadata.keywords) return
        pushUndoSnapshot(label = "Remove Keyword", snapshot = currentSnapshot(current))
        _uiState.update {
            it.copy(
                artworkMetadata = it.artworkMetadata.copy(
                    keywords = it.artworkMetadata.keywords.filterNot { kw -> kw == keyword }
                )
            )
        }
    }

    fun addCustomAttribute(key: String, value: String) {
        val k = key.trim()
        val v = value.trim()
        if (k.isEmpty() || v.isEmpty()) return
        pushUndoSnapshot(label = "Add Attribute", snapshot = currentSnapshot())
        _uiState.update {
            it.copy(
                artworkMetadata = it.artworkMetadata.copy(
                    customAttributes = it.artworkMetadata.customAttributes + MetadataAttribute(key = k, value = v)
                )
            )
        }
    }

    fun removeCustomAttribute(id: String) {
        val current = _uiState.value
        if (current.artworkMetadata.customAttributes.none { it.id == id }) return
        pushUndoSnapshot(label = "Remove Attribute", snapshot = currentSnapshot(current))
        _uiState.update {
            it.copy(
                artworkMetadata = it.artworkMetadata.copy(
                    customAttributes = it.artworkMetadata.customAttributes.filterNot { attr -> attr.id == id }
                )
            )
        }
    }

    fun updateLayerMetadata(index: Int, transform: (LayerMetadata) -> LayerMetadata) {
        val current = _uiState.value
        if (index !in current.layers.indices) return
        val target = current.layers[index]
        val updatedMeta = transform(target.metadata)
        if (updatedMeta == target.metadata) return
        pushUndoSnapshot(
            label = "L${target.slotNumber} Metadata",
            snapshot = currentSnapshot(current),
            coalesceKey = "layer_meta_${target.id}"
        )
        val updated = current.layers.mapIndexed { i, layer ->
            if (i == index) layer.copy(metadata = updatedMeta) else layer
        }
        _uiState.update { it.copy(layers = updated) }
    }

    fun buildLiveMetadataJson(): String {
        val state = _uiState.value
        return MetadataWriter.buildMetadataJson(
            artwork = state.artworkMetadata,
            layers = state.layers,
            exportOptions = state.exportOptions
        )
    }

    // --- Export Options & Download Execution ---

    fun refreshExportPreviewAndEstimate() {
        previewEstimateJob?.cancel()
        previewEstimateJob = viewModelScope.launch {
            delay(120)
            val state = _uiState.value
            val opts = state.exportOptions

            val previewBitmap = withContext(Dispatchers.Default) {
                val effectiveTransparent =
                    opts.format == ExportFormat.PNG && opts.transparentBackground
                CanvasRenderer.renderCompositeBitmap(
                    layers = state.layers,
                    sizePx = 320,
                    transparentBackground = effectiveTransparent,
                    backdropArgb = opts.backdropArgb
                )
            }

            // Compute exact size for <= 2048px, or scaled estimate from 1024px for 4096px to stay ultra-snappy
            val estimatedBytes = withContext(Dispatchers.Default) {
                if (opts.resolution.pixels <= 2048) {
                    MediaExporter.encodeComposition(
                        layers = state.layers,
                        artworkMetadata = state.artworkMetadata,
                        options = opts
                    ).imageBytes.size.toLong()
                } else {
                    val base1024 = MediaExporter.encodeComposition(
                        layers = state.layers,
                        artworkMetadata = state.artworkMetadata,
                        options = opts.copy(resolution = ExportResolution.RES_1024)
                    ).imageBytes.size.toLong()
                    // 4096x4096 has 16x pixels; typical PNG/JPG compression scales ~9x-12x
                    (base1024 * 10.5).toLong()
                }
            }

            _uiState.update {
                it.copy(
                    exportPreviewBitmap = previewBitmap,
                    estimatedSizeBytes = estimatedBytes
                )
            }
        }
    }

    fun updateExportOptions(transform: (ExportOptions) -> ExportOptions) {
        _uiState.update {
            it.copy(
                exportOptions = transform(it.exportOptions),
                exportUiState = ExportUiState.Idle
            )
        }
        refreshExportPreviewAndEstimate()
    }

    fun resetExportStatus() {
        _uiState.update { it.copy(exportUiState = ExportUiState.Idle) }
    }

    fun downloadImageToDevice() {
        val state = _uiState.value
        if (state.exportUiState is ExportUiState.Exporting) return

        _uiState.update { it.copy(exportUiState = ExportUiState.Exporting) }
        viewModelScope.launch {
            try {
                val result = MediaExporter.downloadToDevice(
                    context = getApplication(),
                    layers = state.layers,
                    artworkMetadata = state.artworkMetadata,
                    options = state.exportOptions
                )
                _uiState.update {
                    it.copy(
                        exportUiState = ExportUiState.Success(result),
                        exportedArtifacts = listOf(result) + it.exportedArtifacts
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        exportUiState = ExportUiState.Error(
                            t.message ?: "Failed to export image"
                        )
                    )
                }
            }
        }
    }

    fun exportImageToSafUri(uri: Uri) {
        val state = _uiState.value
        _uiState.update { it.copy(exportUiState = ExportUiState.Exporting) }
        viewModelScope.launch {
            try {
                val result = MediaExporter.exportToUri(
                    context = getApplication(),
                    targetUri = uri,
                    layers = state.layers,
                    artworkMetadata = state.artworkMetadata,
                    options = state.exportOptions
                )
                _uiState.update {
                    it.copy(
                        exportUiState = ExportUiState.Success(result),
                        exportedArtifacts = listOf(result) + it.exportedArtifacts
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        exportUiState = ExportUiState.Error(
                            t.message ?: "Failed to save to selected folder"
                        )
                    )
                }
            }
        }
    }

    fun prepareShareExport(onUriReady: (Uri, String) -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(exportUiState = ExportUiState.Exporting) }
        viewModelScope.launch {
            try {
                val (shareUri, result) = MediaExporter.prepareShareUri(
                    context = getApplication(),
                    layers = state.layers,
                    artworkMetadata = state.artworkMetadata,
                    options = state.exportOptions
                )
                _uiState.update {
                    it.copy(exportUiState = ExportUiState.Success(result))
                }
                onUriReady(shareUri, state.exportOptions.format.mimeType)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        exportUiState = ExportUiState.Error(
                            t.message ?: "Failed to prepare share file"
                        )
                    )
                }
            }
        }
    }

    // --- Gemini AI Studio (Firebase AI Logic) ---

    fun setAiStudioMode(mode: AiStudioMode) {
        _uiState.update {
            it.copy(
                aiMode = mode,
                aiUiState = if (it.aiUiState is AiStudioUiState.Error) AiStudioUiState.Idle else it.aiUiState
            )
        }
    }

    fun resetAiState() {
        aiJob?.cancel()
        _uiState.update { it.copy(aiUiState = AiStudioUiState.Idle) }
    }

    fun refreshAiCanvasPreview() {
        val state = _uiState.value
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                CanvasRenderer.renderCompositeBitmap(
                    layers = state.layers,
                    sizePx = 512,
                    transparentBackground = false,
                    backdropArgb = state.exportOptions.backdropArgb
                )
            }
            _uiState.update { it.copy(aiCanvasPreview = preview) }
        }
    }

    fun generateAiMetadata(userNotes: String = "") {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiMode = AiStudioMode.METADATA_TAGGER,
                    aiUiState = AiStudioUiState.Loading
                )
            }
            try {
                val state = _uiState.value
                val compositeBitmap = withContext(Dispatchers.Default) {
                    CanvasRenderer.renderCompositeBitmap(
                        layers = state.layers,
                        sizePx = 512,
                        transparentBackground = false,
                        backdropArgb = state.exportOptions.backdropArgb
                    )
                }
                _uiState.update { it.copy(aiCanvasPreview = compositeBitmap) }

                val suggestion = withTimeout(AI_TIMEOUT_MS) {
                    GeminiStudioAssistant.analyzeArtworkForMetadata(
                        compositeBitmap = compositeBitmap,
                        layers = state.layers,
                        userNotes = userNotes.trim()
                    )
                }
                _uiState.update { it.copy(aiUiState = AiStudioUiState.MetadataSuccess(suggestion)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Gemini metadata analysis timed out", e)
                _uiState.update {
                    it.copy(
                        aiUiState = AiStudioUiState.Error(
                            "Gemini analysis timed out after 60 seconds. Please try again."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Gemini metadata analysis failed", e)
                _uiState.update {
                    it.copy(
                        aiUiState = AiStudioUiState.Error(
                            e.localizedMessage ?: "Gemini metadata generation failed."
                        )
                    )
                }
            }
        }
    }

    fun applyAiMetadataSuggestion(includeLayerTraits: Boolean) {
        val state = _uiState.value
        val successState = state.aiUiState as? AiStudioUiState.MetadataSuccess ?: return
        val suggestion = successState.suggestion

        pushUndoSnapshot(label = "AI Apply Metadata", snapshot = currentSnapshot(state))

        val mergedKeywords = (state.artworkMetadata.keywords + suggestion.keywords)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        val updatedCustomAttributes = state.artworkMetadata.customAttributes.toMutableList()
        if (suggestion.visualMood.isNotBlank()) {
            updatedCustomAttributes.removeAll { it.key.equals("Visual Mood", ignoreCase = true) }
            updatedCustomAttributes.add(MetadataAttribute(key = "Visual Mood", value = suggestion.visualMood))
        }
        if (suggestion.colorHarmony.isNotBlank()) {
            updatedCustomAttributes.removeAll { it.key.equals("Palette", ignoreCase = true) }
            updatedCustomAttributes.add(MetadataAttribute(key = "Palette", value = suggestion.colorHarmony))
        }

        val updatedArtwork = state.artworkMetadata.copy(
            title = suggestion.title,
            description = suggestion.description.ifBlank { state.artworkMetadata.description },
            keywords = mergedKeywords,
            customAttributes = updatedCustomAttributes
        )

        val traitBySlot = suggestion.layerTraits.associateBy { it.slotNumber }
        val updatedLayers = if (includeLayerTraits) {
            state.layers.map { layer ->
                val trait = traitBySlot[layer.slotNumber]
                if (trait != null) {
                    layer.copy(
                        metadata = layer.metadata.copy(
                            traitCategory = trait.traitCategory.ifBlank { layer.metadata.traitCategory },
                            traitValue = trait.traitValue.ifBlank { layer.metadata.traitValue },
                            notes = trait.notes.ifBlank { layer.metadata.notes },
                            tags = (layer.metadata.tags + trait.tags)
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinctBy { it.lowercase() }
                        )
                    )
                } else {
                    layer
                }
            }
        } else {
            state.layers
        }

        _uiState.update {
            it.copy(
                artworkMetadata = updatedArtwork,
                layers = updatedLayers,
                statusBannerMessage = if (includeLayerTraits) {
                    "Applied Gemini artwork & layer metadata"
                } else {
                    "Applied Gemini artwork metadata"
                }
            )
        }
    }

    fun askAiCreativeCoach(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiMode = AiStudioMode.CREATIVE_COACH,
                    aiUiState = AiStudioUiState.Loading
                )
            }
            val accumulated = StringBuilder()
            try {
                val state = _uiState.value
                val compositeBitmap = withContext(Dispatchers.Default) {
                    CanvasRenderer.renderCompositeBitmap(
                        layers = state.layers,
                        sizePx = 512,
                        transparentBackground = false,
                        backdropArgb = state.exportOptions.backdropArgb
                    )
                }
                _uiState.update { it.copy(aiCanvasPreview = compositeBitmap) }

                withTimeout(AI_TIMEOUT_MS) {
                    GeminiStudioAssistant.streamCreativeFeedback(
                        compositeBitmap = compositeBitmap,
                        layers = state.layers,
                        userPrompt = cleanPrompt
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _uiState.update {
                            it.copy(aiUiState = AiStudioUiState.Streaming(accumulated.toString()))
                        }
                    }
                }

                if (accumulated.isNotBlank()) {
                    _uiState.update {
                        it.copy(aiUiState = AiStudioUiState.CoachSuccess(accumulated.toString()))
                    }
                } else {
                    _uiState.update {
                        it.copy(aiUiState = AiStudioUiState.Error("Gemini returned an empty response."))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Gemini coaching stream timed out", e)
                _uiState.update {
                    it.copy(aiUiState = AiStudioUiState.Error("Streaming timed out after 60 seconds."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Gemini coaching stream failed", e)
                _uiState.update {
                    it.copy(
                        aiUiState = AiStudioUiState.Error(
                            e.localizedMessage ?: "Gemini creative coaching failed."
                        )
                    )
                }
            }
        }
    }
}
