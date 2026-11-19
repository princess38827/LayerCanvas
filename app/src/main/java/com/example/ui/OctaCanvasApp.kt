package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.engine.MediaExporter
import com.example.model.ExportFormat
import com.example.ui.components.ArtifactsStudioContent
import com.example.ui.components.CanvasViewport
import com.example.ui.components.ColorPickerDialog
import com.example.ui.components.ExportDownloadContent
import com.example.ui.components.GeminiStudioContent
import com.example.ui.components.LayersPanelContent
import com.example.ui.components.MetadataStudioContent
import com.example.ui.components.RenameLayerDialog
import com.example.ui.components.TextStampDialog
import com.example.ui.components.ToolDock

private enum class ActiveSheet {
    NONE,
    LAYERS,
    METADATA,
    ARTIFACTS,
    GEMINI_AI,
    EXPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OctaCanvasApp(
    viewModel: OctaCanvasViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draftStroke by viewModel.draftStroke.collectAsStateWithLifecycle()
    val draftShape by viewModel.draftShape.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var metadataInitialTab by remember { mutableIntStateOf(0) }
    var metadataInitialLayer by remember { mutableIntStateOf(uiState.activeLayerIndex) }
    var artifactsInitialTab by remember { mutableIntStateOf(0) }

    var showColorPicker by remember { mutableStateOf(false) }
    var renameLayerIndex by remember { mutableStateOf<Int?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Zero-permission Android Photo Picker for importing photos onto any of the 8 layers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importPhotoToActiveLayer(it) }
    }

    // SAF CreateDocument launchers for Save As...
    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let { viewModel.exportImageToSafUri(it) }
    }
    val createJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        uri?.let { viewModel.exportImageToSafUri(it) }
    }

    BackHandler(enabled = activeSheet != ActiveSheet.NONE) {
        activeSheet = ActiveSheet.NONE
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            StudioTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                metadataBadgeCount = uiState.layers.count { it.metadata.hasContent },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onOpenMetadata = {
                    metadataInitialTab = 0
                    metadataInitialLayer = uiState.activeLayerIndex
                    activeSheet = ActiveSheet.METADATA
                },
                onOpenGeminiAi = {
                    viewModel.refreshAiCanvasPreview()
                    activeSheet = ActiveSheet.GEMINI_AI
                },
                onOpenExport = {
                    viewModel.resetExportStatus()
                    viewModel.refreshExportPreviewAndEstimate()
                    activeSheet = ActiveSheet.EXPORT
                },
                overflowExpanded = overflowExpanded,
                onToggleOverflow = { overflowExpanded = it },
                onLoadShowcase = {
                    overflowExpanded = false
                    viewModel.loadShowcaseTemplate()
                },
                onClearAll = {
                    overflowExpanded = false
                    viewModel.clearAllLayers()
                },
                onPlaceTextCenter = {
                    overflowExpanded = false
                    viewModel.requestTextPlacementAtCenter()
                },
                onOpenSavedArtifacts = {
                    overflowExpanded = false
                    artifactsInitialTab = 1
                    activeSheet = ActiveSheet.ARTIFACTS
                },
                onOpenGeminiFromMenu = {
                    overflowExpanded = false
                    viewModel.refreshAiCanvasPreview()
                    activeSheet = ActiveSheet.GEMINI_AI
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            val isExpandedLayout = maxWidth >= 780.dp

            if (isExpandedLayout) {
                // Canonical Adaptive Tablet/Foldable Layout: Left Canvas + Tools, Right Persistent 8-Layer Panel
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        CanvasViewport(
                            layers = uiState.layers,
                            activeLayerIndex = uiState.activeLayerIndex,
                            activeTool = uiState.activeTool,
                            selectedColor = uiState.selectedColor,
                            brushSizeFraction = uiState.brushSizeFraction,
                            brushOpacity = uiState.brushOpacity,
                            shapeFilled = uiState.shapeFilled,
                            showGridOverlay = uiState.showGridOverlay,
                            draftStroke = draftStroke,
                            draftShape = draftShape,
                            onDragStart = viewModel::onCanvasDragStart,
                            onDragMove = viewModel::onCanvasDragMove,
                            onDragEnd = viewModel::onCanvasDragEnd,
                            onDragCancel = viewModel::onCanvasDragCancel,
                            onLoadShowcase = viewModel::loadShowcaseTemplate,
                            onToggleGrid = viewModel::toggleGridOverlay,
                            modifier = Modifier.weight(1f)
                        )
                        ToolDock(
                            layers = uiState.layers,
                            activeLayerIndex = uiState.activeLayerIndex,
                            activeTool = uiState.activeTool,
                            selectedColor = uiState.selectedColor,
                            brushSizeFraction = uiState.brushSizeFraction,
                            shapeFilled = uiState.shapeFilled,
                            showLayersButton = false,
                            onSelectLayer = viewModel::selectLayer,
                            onOpenLayersSheet = {},
                            onSelectTool = viewModel::selectTool,
                            onSelectColor = viewModel::selectColor,
                            onOpenColorPicker = { showColorPicker = true },
                            onBrushSizeChange = viewModel::setBrushSizeFraction,
                            onActiveLayerOpacityChange = { opacity ->
                                viewModel.setLayerOpacity(uiState.activeLayerIndex, opacity, isContinuousDrag = true)
                            },
                            onActiveLayerOpacityChangeFinished = viewModel::finishContinuousEdit,
                            onToggleShapeFilled = viewModel::toggleShapeFilled,
                            onLaunchPhotoPicker = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onOpenArtifactsSheet = {
                                artifactsInitialTab = 0
                                activeSheet = ActiveSheet.ARTIFACTS
                            }
                        )
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                    ) {
                        LayersPanelContent(
                            layers = uiState.layers,
                            activeLayerIndex = uiState.activeLayerIndex,
                            onSelectLayer = viewModel::selectLayer,
                            onToggleVisibility = viewModel::toggleLayerVisibility,
                            onToggleLock = viewModel::toggleLayerLock,
                            onOpacityChange = { idx, opacity ->
                                viewModel.setLayerOpacity(idx, opacity, isContinuousDrag = true)
                            },
                            onOpacityChangeFinished = viewModel::finishContinuousEdit,
                            onBlendModeChange = viewModel::setLayerBlendMode,
                            onMoveLayer = viewModel::moveLayer,
                            onRenameLayer = { renameLayerIndex = it },
                            onClearLayer = viewModel::clearLayer,
                            onEditLayerMetadata = { idx ->
                                metadataInitialLayer = idx
                                metadataInitialTab = 1
                                activeSheet = ActiveSheet.METADATA
                            },
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo,
                            undoLabel = uiState.undoLabel,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            } else {
                // Compact Handheld Studio Layout: Maximized Canvas + Bottom Tool Dock + Modal Sheets
                Column(modifier = Modifier.fillMaxSize()) {
                    CanvasViewport(
                        layers = uiState.layers,
                        activeLayerIndex = uiState.activeLayerIndex,
                        activeTool = uiState.activeTool,
                        selectedColor = uiState.selectedColor,
                        brushSizeFraction = uiState.brushSizeFraction,
                        brushOpacity = uiState.brushOpacity,
                        shapeFilled = uiState.shapeFilled,
                        showGridOverlay = uiState.showGridOverlay,
                        draftStroke = draftStroke,
                        draftShape = draftShape,
                        onDragStart = viewModel::onCanvasDragStart,
                        onDragMove = viewModel::onCanvasDragMove,
                        onDragEnd = viewModel::onCanvasDragEnd,
                        onDragCancel = viewModel::onCanvasDragCancel,
                        onLoadShowcase = viewModel::loadShowcaseTemplate,
                        onToggleGrid = viewModel::toggleGridOverlay,
                        modifier = Modifier.weight(1f)
                    )
                    ToolDock(
                        layers = uiState.layers,
                        activeLayerIndex = uiState.activeLayerIndex,
                        activeTool = uiState.activeTool,
                        selectedColor = uiState.selectedColor,
                        brushSizeFraction = uiState.brushSizeFraction,
                        shapeFilled = uiState.shapeFilled,
                        showLayersButton = true,
                        onSelectLayer = viewModel::selectLayer,
                        onOpenLayersSheet = { activeSheet = ActiveSheet.LAYERS },
                        onSelectTool = viewModel::selectTool,
                        onSelectColor = viewModel::selectColor,
                        onOpenColorPicker = { showColorPicker = true },
                        onBrushSizeChange = viewModel::setBrushSizeFraction,
                        onActiveLayerOpacityChange = { opacity ->
                            viewModel.setLayerOpacity(uiState.activeLayerIndex, opacity, isContinuousDrag = true)
                        },
                        onActiveLayerOpacityChangeFinished = viewModel::finishContinuousEdit,
                        onToggleShapeFilled = viewModel::toggleShapeFilled,
                        onLaunchPhotoPicker = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onOpenArtifactsSheet = {
                            artifactsInitialTab = 0
                            activeSheet = ActiveSheet.ARTIFACTS
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets (Layers, Metadata, Export & Download)
    if (activeSheet != ActiveSheet.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (activeSheet) {
                ActiveSheet.LAYERS -> {
                    LayersPanelContent(
                        layers = uiState.layers,
                        activeLayerIndex = uiState.activeLayerIndex,
                        onSelectLayer = viewModel::selectLayer,
                        onToggleVisibility = viewModel::toggleLayerVisibility,
                        onToggleLock = viewModel::toggleLayerLock,
                        onOpacityChange = { idx, opacity ->
                            viewModel.setLayerOpacity(idx, opacity, isContinuousDrag = true)
                        },
                        onOpacityChangeFinished = viewModel::finishContinuousEdit,
                        onBlendModeChange = viewModel::setLayerBlendMode,
                        onMoveLayer = viewModel::moveLayer,
                        onRenameLayer = { renameLayerIndex = it },
                        onClearLayer = viewModel::clearLayer,
                        onEditLayerMetadata = { idx ->
                            metadataInitialLayer = idx
                            metadataInitialTab = 1
                            activeSheet = ActiveSheet.METADATA
                        },
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        undoLabel = uiState.undoLabel,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo
                    )
                }

                ActiveSheet.METADATA -> {
                    val liveJson = remember(uiState.artworkMetadata, uiState.layers, uiState.exportOptions) {
                        viewModel.buildLiveMetadataJson()
                    }
                    MetadataStudioContent(
                        artworkMetadata = uiState.artworkMetadata,
                        layers = uiState.layers,
                        initialLayerIndex = metadataInitialLayer,
                        initialTab = metadataInitialTab,
                        liveJsonPreview = liveJson,
                        onUpdateArtwork = viewModel::updateArtworkMetadata,
                        onAddKeyword = viewModel::addArtworkKeyword,
                        onRemoveKeyword = viewModel::removeArtworkKeyword,
                        onAddCustomAttribute = viewModel::addCustomAttribute,
                        onRemoveCustomAttribute = viewModel::removeCustomAttribute,
                        onUpdateLayerMetadata = viewModel::updateLayerMetadata,
                        onGoToExport = {
                            viewModel.resetExportStatus()
                            viewModel.refreshExportPreviewAndEstimate()
                            activeSheet = ActiveSheet.EXPORT
                        },
                        onOpenAiStudio = {
                            viewModel.setAiStudioMode(AiStudioMode.METADATA_TAGGER)
                            viewModel.refreshAiCanvasPreview()
                            activeSheet = ActiveSheet.GEMINI_AI
                        }
                    )
                }

                ActiveSheet.ARTIFACTS -> {
                    ArtifactsStudioContent(
                        activeLayer = uiState.activeLayer,
                        selectedColor = uiState.selectedColor,
                        exportedArtifacts = uiState.exportedArtifacts,
                        initialTab = artifactsInitialTab,
                        onApplyArtifact = { kind, intensity, seed, autoTag ->
                            viewModel.addArtifactToActiveLayer(kind, intensity, seed, autoTag)
                            activeSheet = ActiveSheet.NONE
                        },
                        onGoToExport = {
                            viewModel.resetExportStatus()
                            viewModel.refreshExportPreviewAndEstimate()
                            activeSheet = ActiveSheet.EXPORT
                        }
                    )
                }

                ActiveSheet.GEMINI_AI -> {
                    GeminiStudioContent(
                        layers = uiState.layers,
                        previewBitmap = uiState.aiCanvasPreview,
                        aiMode = uiState.aiMode,
                        aiUiState = uiState.aiUiState,
                        onSelectMode = viewModel::setAiStudioMode,
                        onAnalyzeMetadata = viewModel::generateAiMetadata,
                        onApplyMetadata = { includeLayerTraits ->
                            viewModel.applyAiMetadataSuggestion(includeLayerTraits)
                            metadataInitialTab = 0
                            activeSheet = ActiveSheet.METADATA
                        },
                        onAskCoach = viewModel::askAiCreativeCoach,
                        onGoToMetadata = {
                            metadataInitialTab = 0
                            metadataInitialLayer = uiState.activeLayerIndex
                            activeSheet = ActiveSheet.METADATA
                        }
                    )
                }

                ActiveSheet.EXPORT -> {
                    ExportDownloadContent(
                        layers = uiState.layers,
                        options = uiState.exportOptions,
                        previewBitmap = uiState.exportPreviewBitmap,
                        estimatedSizeBytes = uiState.estimatedSizeBytes,
                        exportUiState = uiState.exportUiState,
                        onUpdateOptions = viewModel::updateExportOptions,
                        onDownloadToDevice = viewModel::downloadImageToDevice,
                        onSaveAsDocument = {
                            val opts = uiState.exportOptions
                            val clean = MediaExporter.sanitizeFileName(opts.fileName)
                            val suggested = "$clean.${opts.format.extension}"
                            if (opts.format == ExportFormat.PNG) {
                                createPngLauncher.launch(suggested)
                            } else {
                                createJpgLauncher.launch(suggested)
                            }
                        },
                        onShareExport = {
                            viewModel.prepareShareExport { uri, mimeType ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, uiState.artworkMetadata.title)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share 8-Layer Metadata Image")
                                )
                            }
                        }
                    )
                }

                ActiveSheet.NONE -> Unit
            }
        }
    }

    // Dialogs
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = uiState.selectedColor,
            onColorSelected = { color ->
                viewModel.selectColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (uiState.pendingTextPosition != null) {
        TextStampDialog(
            onConfirm = { text, sizeFrac, bold ->
                viewModel.commitTextElement(text, sizeFrac, bold)
            },
            onDismiss = viewModel::dismissTextPlacement
        )
    }

    val renameIdx = renameLayerIndex
    if (renameIdx != null && renameIdx in uiState.layers.indices) {
        RenameLayerDialog(
            currentName = uiState.layers[renameIdx].name,
            onConfirm = { newName ->
                viewModel.renameLayer(renameIdx, newName)
                renameLayerIndex = null
            },
            onDismiss = { renameLayerIndex = null }
        )
    }
}

@Composable
private fun StudioTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    metadataBadgeCount: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenMetadata: () -> Unit,
    onOpenGeminiAi: () -> Unit,
    onOpenExport: () -> Unit,
    overflowExpanded: Boolean,
    onToggleOverflow: (Boolean) -> Unit,
    onLoadShowcase: () -> Unit,
    onClearAll: () -> Unit,
    onPlaceTextCenter: () -> Unit,
    onOpenSavedArtifacts: () -> Unit,
    onOpenGeminiFromMenu: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "L8",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "OctaCanvas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.cd_undo)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = stringResource(R.string.cd_redo)
                    )
                }
                IconButton(
                    onClick = onOpenGeminiAi,
                    modifier = Modifier.testTag("open_gemini_ai_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_gemini_ai),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onOpenMetadata,
                    modifier = Modifier.testTag("open_metadata_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DataObject,
                        contentDescription = stringResource(R.string.cd_metadata),
                        tint = if (metadataBadgeCount > 0) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.width(4.dp))

                Button(
                    onClick = onOpenExport,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("open_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.cd_export),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Export")
                }

                Box {
                    IconButton(
                        onClick = { onToggleOverflow(true) },
                        modifier = Modifier.testTag("studio_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More studio actions"
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { onToggleOverflow(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini AI Studio") },
                            leadingIcon = {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            },
                            onClick = onOpenGeminiFromMenu
                        )
                        DropdownMenuItem(
                            text = { Text("Load 8-Layer Demo") },
                            leadingIcon = {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            },
                            onClick = onLoadShowcase
                        )
                        DropdownMenuItem(
                            text = { Text("Add / View Artifacts") },
                            leadingIcon = {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            },
                            onClick = onOpenSavedArtifacts
                        )
                        DropdownMenuItem(
                            text = { Text("Place Text Stamp at Center") },
                            onClick = onPlaceTextCenter
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All 8 Layers") },
                            leadingIcon = {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            },
                            onClick = onClearAll
                        )
                    }
                }
            }
        }
    }
}
