package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiLayerTraitSuggestion
import com.example.ai.AiMetadataSuggestion
import com.example.ai.GeminiStudioAssistant
import com.example.ai.configureAppCheck
import com.example.engine.CanvasRenderer
import com.example.engine.MediaExporter
import com.example.engine.MetadataWriter
import com.example.engine.TemplateGenerator
import com.example.model.ArtifactKind
import com.example.model.ArtworkMetadata
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportResolution
import com.example.model.LayerBlendMode
import com.example.ui.AiStudioMode
import com.example.ui.AiStudioUiState
import com.example.ui.OctaCanvasViewModel
import com.example.ui.components.ArtifactsStudioContent
import com.example.ui.components.GeminiStudioContent
import com.example.ui.components.LayersPanelContent
import com.example.ui.theme.OctaCanvasTheme
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OctaCanvas", appName)
    }

    @Test
    fun `showcase has 8 layers and renders at 1024x1024 and 2048x2048`() {
        val (layers, metadata) = TemplateGenerator.createShowcaseComposition()
        assertEquals(8, layers.size)
        assertEquals("Neon Horizon — 8-Layer Study", metadata.title)

        val bmp1024 = CanvasRenderer.renderCompositeBitmap(
            layers = layers,
            sizePx = 1024,
            transparentBackground = false,
            backdropArgb = 0xFF0C0E14.toInt()
        )
        assertEquals(1024, bmp1024.width)
        assertEquals(1024, bmp1024.height)
        bmp1024.recycle()

        val bmp2048 = CanvasRenderer.renderCompositeBitmap(
            layers = layers,
            sizePx = 2048,
            transparentBackground = true,
            backdropArgb = 0xFF0C0E14.toInt()
        )
        assertEquals(2048, bmp2048.width)
        assertEquals(2048, bmp2048.height)
        bmp2048.recycle()
    }

    @Test
    fun `png export embeds iTXt 8-layer metadata manifest`() = runTest {
        val (layers, metadata) = TemplateGenerator.createShowcaseComposition()
        val payload = MediaExporter.encodeComposition(
            layers = layers,
            artworkMetadata = metadata,
            options = ExportOptions(
                format = ExportFormat.PNG,
                resolution = ExportResolution.RES_1024,
                transparentBackground = true,
                embedMetadata = true,
                fileName = "test_png"
            )
        )
        assertEquals("test_png.png", payload.sanitizedFileName)
        assertTrue(payload.imageBytes.size > 1000)
        val asciiView = String(payload.imageBytes, Charsets.ISO_8859_1)
        assertTrue(asciiView.contains("OctaCanvas-Layer8-Metadata"))
        assertTrue(asciiView.contains("Neon Rim Ink"))

        val json = JSONObject(payload.jsonManifest)
        assertEquals("octacanvas.layer8.v1", json.getString("schema"))
        assertEquals(8, json.getJSONArray("layers").length())
    }

    @Test
    fun `jpg export respects quality and embeds exif metadata`() = runTest {
        val (layers, metadata) = TemplateGenerator.createShowcaseComposition()
        val highQuality = MediaExporter.encodeComposition(
            layers = layers,
            artworkMetadata = metadata,
            options = ExportOptions(
                format = ExportFormat.JPG,
                resolution = ExportResolution.RES_512,
                jpgQuality = 95,
                embedMetadata = true,
                fileName = "test_hq"
            )
        )
        val lowQuality = MediaExporter.encodeComposition(
            layers = layers,
            artworkMetadata = metadata,
            options = ExportOptions(
                format = ExportFormat.JPG,
                resolution = ExportResolution.RES_512,
                jpgQuality = 30,
                embedMetadata = true,
                fileName = "test_lq"
            )
        )

        assertTrue(highQuality.imageBytes.size > lowQuality.imageBytes.size)

        val tempJpg = File.createTempFile("verify_exif_", ".jpg")
        try {
            tempJpg.writeBytes(highQuality.imageBytes)
            val exif = ExifInterface(tempJpg)
            assertEquals("OctaCanvas Artist", exif.getAttribute(ExifInterface.TAG_ARTIST))
            assertEquals("CC BY 4.0", exif.getAttribute(ExifInterface.TAG_COPYRIGHT))
            val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: ""
            assertTrue(comment.contains("octacanvas.layer8.v1"))
        } finally {
            tempJpg.delete()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `per-layer opacity scales composited pixel alpha and records in metadata`() {
        val baseLayers = TemplateGenerator.createEmptyEightLayers().toMutableList()
        baseLayers[3] = baseLayers[3].copy(
            backgroundFillArgb = 0xFFFF0000.toInt(),
            opacity = 0.5f
        )

        val bmp = CanvasRenderer.renderCompositeBitmap(
            layers = baseLayers,
            sizePx = 64,
            transparentBackground = true,
            backdropArgb = 0xFF000000.toInt()
        )
        val centerPixel = bmp.getPixel(32, 32)
        val alpha = android.graphics.Color.alpha(centerPixel)
        assertTrue("Expected ~50% alpha (120..135), got $alpha", alpha in 120..135)
        bmp.recycle()

        val json = MetadataWriter.buildMetadataJson(
            artwork = ArtworkMetadata(),
            layers = baseLayers,
            exportOptions = ExportOptions()
        )
        val parsedLayers = JSONObject(json).getJSONArray("layers")
        assertEquals(0.5, parsedLayers.getJSONObject(3).getDouble("opacity"), 0.02)
    }

    @Test
    fun `all 8 layer cards expose opacity sliders and update opacity state`() {
        val initialLayers = TemplateGenerator.createEmptyEightLayers()
        var lastUpdatedLayerIdx = -1
        var lastUpdatedOpacity = 1f

        composeTestRule.setContent {
            OctaCanvasTheme {
                LayersPanelContent(
                    layers = initialLayers,
                    activeLayerIndex = 3,
                    onSelectLayer = {},
                    onToggleVisibility = {},
                    onToggleLock = {},
                    onOpacityChange = { idx, opacity ->
                        lastUpdatedLayerIdx = idx
                        lastUpdatedOpacity = opacity
                    },
                    onBlendModeChange = { _, _ -> },
                    onMoveLayer = { _, _ -> },
                    onRenameLayer = {},
                    onClearLayer = {},
                    onEditLayerMetadata = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Verify top layer (8), selected layer (4), and bottom layer (1) sliders exist via LazyColumn scroll
        composeTestRule.onNodeWithTag("layers_lazy_column")
            .performScrollToNode(hasTestTag("layer_opacity_slider_8"))
        composeTestRule.onNodeWithTag("layer_opacity_slider_8").assertIsDisplayed()

        composeTestRule.onNodeWithTag("layers_lazy_column")
            .performScrollToNode(hasTestTag("layer_4_opacity_50%"))
        composeTestRule.onNodeWithTag("layer_opacity_slider_4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("layer_4_opacity_50%").performClick()
        composeTestRule.waitForIdle()

        assertEquals(3, lastUpdatedLayerIdx)
        assertEquals(0.5f, lastUpdatedOpacity, 0.01f)

        composeTestRule.onNodeWithTag("layers_lazy_column")
            .performScrollToNode(hasTestTag("layer_opacity_slider_1"))
        composeTestRule.onNodeWithTag("layer_opacity_slider_1").assertIsDisplayed()
    }

    @Test
    fun `undo and redo stack covers layer operations and coalesces opacity drags`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = OctaCanvasViewModel(app)

        assertFalse(vm.uiState.value.canUndo)
        assertFalse(vm.uiState.value.canRedo)

        // 1. Visibility toggle
        vm.toggleLayerVisibility(3)
        assertFalse(vm.uiState.value.layers[3].isVisible)
        assertTrue(vm.uiState.value.canUndo)

        // 2. Lock toggle
        vm.toggleLayerLock(3)
        assertTrue(vm.uiState.value.layers[3].isLocked)

        // 3. Rename layer
        vm.renameLayer(3, "Hero Focal")
        assertEquals("Hero Focal", vm.uiState.value.layers[3].name)

        // 4. Blend mode
        vm.setLayerBlendMode(3, LayerBlendMode.MULTIPLY)
        assertEquals(LayerBlendMode.MULTIPLY, vm.uiState.value.layers[3].blendMode)

        // 5. Continuous opacity slider drag should coalesce into ONE undo step
        vm.setLayerOpacity(3, 0.80f, isContinuousDrag = true)
        vm.setLayerOpacity(3, 0.55f, isContinuousDrag = true)
        vm.setLayerOpacity(3, 0.30f, isContinuousDrag = true)
        vm.finishContinuousEdit()
        assertEquals(0.30f, vm.uiState.value.layers[3].opacity, 0.001f)

        // 6. Move active layer up (index 3 -> 4)
        vm.selectLayer(3)
        vm.moveLayer(3, +1)
        assertEquals(4, vm.uiState.value.activeLayerIndex)
        assertEquals("Hero Focal", vm.uiState.value.layers[4].name)

        // Undo Move -> restores both position AND activeLayerIndex = 3
        vm.undo()
        assertEquals(3, vm.uiState.value.activeLayerIndex)
        assertEquals("Hero Focal", vm.uiState.value.layers[3].name)
        assertTrue(vm.uiState.value.canRedo)

        // Undo coalesced Opacity drag -> jumps straight back from 0.30f to 1.0f in one step
        vm.undo()
        assertEquals(1.0f, vm.uiState.value.layers[3].opacity, 0.001f)

        // Undo Blend Mode -> back to NORMAL
        vm.undo()
        assertEquals(LayerBlendMode.NORMAL, vm.uiState.value.layers[3].blendMode)

        // Undo Rename -> back to "Primary Subject"
        vm.undo()
        assertEquals("Primary Subject", vm.uiState.value.layers[3].name)

        // Undo Lock -> unlocked
        vm.undo()
        assertFalse(vm.uiState.value.layers[3].isLocked)

        // Undo Visibility -> visible again
        vm.undo()
        assertTrue(vm.uiState.value.layers[3].isVisible)
        assertFalse(vm.uiState.value.canUndo)

        // Redo all 6 steps back to final state
        repeat(6) { vm.redo() }
        assertEquals(4, vm.uiState.value.activeLayerIndex)
        assertEquals("Hero Focal", vm.uiState.value.layers[4].name)
        assertEquals(0.30f, vm.uiState.value.layers[4].opacity, 0.001f)
        assertEquals(LayerBlendMode.MULTIPLY, vm.uiState.value.layers[4].blendMode)
        assertTrue(vm.uiState.value.layers[4].isLocked)
        assertFalse(vm.uiState.value.layers[4].isVisible)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `procedural artifacts render onto layer, tag metadata, and support undo`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = OctaCanvasViewModel(app)

        vm.selectLayer(6) // Layer 7 (Glow & FX)
        vm.addArtifactToActiveLayer(
            kind = ArtifactKind.SCANLINES,
            intensity = 0.8f,
            seed = 777,
            autoTagMetadata = true
        )

        val layer7 = vm.uiState.value.layers[6]
        assertEquals(1, layer7.elements.size)
        assertEquals("Scanlines", layer7.metadata.traitValue)
        assertTrue("scanlines" in layer7.metadata.tags)

        val bmp = CanvasRenderer.renderCompositeBitmap(
            layers = vm.uiState.value.layers,
            sizePx = 128,
            transparentBackground = false,
            backdropArgb = 0xFF0C0E14.toInt()
        )
        assertEquals(128, bmp.width)
        bmp.recycle()

        val json = vm.buildLiveMetadataJson()
        val layerJson = JSONObject(json).getJSONArray("layers").getJSONObject(6)
        assertEquals(1, layerJson.getJSONArray("artifacts").length())
        assertEquals("SCANLINES", layerJson.getJSONArray("artifacts").getJSONObject(0).getString("kind"))

        vm.undo()
        assertEquals(0, vm.uiState.value.layers[6].elements.size)
        assertEquals("", vm.uiState.value.layers[6].metadata.traitValue)

        vm.redo()
        assertEquals(1, vm.uiState.value.layers[6].elements.size)
        assertEquals("Scanlines", vm.uiState.value.layers[6].metadata.traitValue)
    }

    @Test
    fun `gemini assistant parses structured metadata json and summarizes 8 layers`() {
        val (layers, _) = TemplateGenerator.createShowcaseComposition()
        val summary = GeminiStudioAssistant.buildLayerStackSummary(layers)
        assertTrue(summary.contains("L1 (01 · Midnight Sky)"))
        assertTrue(summary.contains("L8 ("))

        val rawJson = """
            {
              "title": "Synthwave Horizon Grid",
              "description": "An 8-layer synthwave study blending cyan telemetry rings over warm amber horizons.",
              "keywords": ["synthwave", "8-layer", "neon", "grid"],
              "visualMood": "Electric Dusk",
              "colorHarmony": "Cyan & Amber",
              "layerTraits": [
                {
                  "slotNumber": 4,
                  "traitCategory": "Focal Subject",
                  "traitValue": "Radiant Horizon Orb",
                  "notes": "100% normal blend focal anchor",
                  "tags": ["orb", "horizon"]
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiStudioAssistant.parseMetadataSuggestionJson(rawJson)
        assertEquals("Synthwave Horizon Grid", parsed.title)
        assertEquals("Electric Dusk", parsed.visualMood)
        assertEquals("Cyan & Amber", parsed.colorHarmony)
        assertEquals(4, parsed.keywords.size)
        assertEquals(1, parsed.layerTraits.size)
        assertEquals(4, parsed.layerTraits.first().slotNumber)
        assertEquals("Radiant Horizon Orb", parsed.layerTraits.first().traitValue)
    }

    @Test
    fun `gemini studio UI displays structured suggestion and triggers apply callback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureAppCheck(context, null)

        val (layers, _) = TemplateGenerator.createShowcaseComposition()
        val sampleSuggestion = AiMetadataSuggestion(
            title = "Cybernetic Telemetry",
            description = "Layered digital prism study.",
            keywords = listOf("cybernetic", "telemetry"),
            visualMood = "Luminous",
            colorHarmony = "Teal & Gold",
            layerTraits = listOf(
                AiLayerTraitSuggestion(
                    slotNumber = 4,
                    traitCategory = "Subject",
                    traitValue = "Solar Core",
                    notes = "Crisp center anchor",
                    tags = listOf("core")
                )
            )
        )
        var appliedWithTraits: Boolean? = null

        composeTestRule.setContent {
            OctaCanvasTheme {
                GeminiStudioContent(
                    layers = layers,
                    previewBitmap = null,
                    aiMode = AiStudioMode.METADATA_TAGGER,
                    aiUiState = AiStudioUiState.MetadataSuccess(sampleSuggestion),
                    onSelectMode = {},
                    onAnalyzeMetadata = {},
                    onApplyMetadata = { includeTraits -> appliedWithTraits = includeTraits },
                    onAskCoach = {},
                    onGoToMetadata = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_metadata_result_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_apply_all_metadata_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, appliedWithTraits)
    }
}
