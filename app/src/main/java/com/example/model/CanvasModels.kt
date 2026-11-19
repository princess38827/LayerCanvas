package com.example.model

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import java.util.UUID

const val LAYER_COUNT = 8

enum class LayerBlendMode(
    val displayName: String,
    val composeBlendMode: BlendMode,
    val porterDuffMode: PorterDuff.Mode
) {
    NORMAL("Normal", BlendMode.SrcOver, PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", BlendMode.Multiply, PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", BlendMode.Screen, PorterDuff.Mode.SCREEN),
    OVERLAY("Overlay", BlendMode.Overlay, PorterDuff.Mode.OVERLAY),
    DARKEN("Darken", BlendMode.Darken, PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", BlendMode.Lighten, PorterDuff.Mode.LIGHTEN);

    fun toAndroidBlendMode(): android.graphics.BlendMode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return when (this) {
            NORMAL -> android.graphics.BlendMode.SRC_OVER
            MULTIPLY -> android.graphics.BlendMode.MULTIPLY
            SCREEN -> android.graphics.BlendMode.SCREEN
            OVERLAY -> android.graphics.BlendMode.OVERLAY
            DARKEN -> android.graphics.BlendMode.DARKEN
            LIGHTEN -> android.graphics.BlendMode.LIGHTEN
        }
    }
}

enum class DrawingTool(val label: String) {
    BRUSH("Brush"),
    ERASER("Eraser"),
    LINE("Line"),
    RECTANGLE("Rect"),
    CIRCLE("Circle"),
    TEXT("Text"),
    FILL("Fill")
}

enum class ShapeKind {
    LINE,
    RECTANGLE,
    CIRCLE
}

enum class ArtifactKind(
    val displayName: String,
    val description: String,
    val defaultCategory: String
) {
    SCANLINES("Scanlines", "CRT horizontal raster lines", "Texture"),
    FILM_GRAIN("Film Grain", "Organic speckle noise", "Grain"),
    GLITCH_BARS("Glitch Bars", "Digital offset slice bars", "Glitch"),
    HALFTONE("Halftone", "Precision screen dot matrix", "Pattern"),
    VIEWFINDER("Viewfinder", "Optical crop marks & reticle", "Frame"),
    BOKEH_DUST("Light Dust", "Scattered luminous motes", "Particles"),
    HOLO_GRID("Holo Radar", "Concentric telemetry rings", "HUD"),
    PRISM_SHARDS("Prism Shards", "Faceted crystal triangles", "Geometry")
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

sealed interface LayerElement {
    val id: String

    data class PathStroke(
        override val id: String = UUID.randomUUID().toString(),
        val points: List<NormalizedPoint>,
        val colorArgb: Int,
        val strokeWidthFraction: Float,
        val opacity: Float = 1f,
        val isEraser: Boolean = false
    ) : LayerElement

    data class ShapeElement(
        override val id: String = UUID.randomUUID().toString(),
        val kind: ShapeKind,
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val strokeWidthFraction: Float,
        val filled: Boolean,
        val opacity: Float = 1f
    ) : LayerElement

    data class TextElement(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val position: NormalizedPoint,
        val colorArgb: Int,
        val fontSizeFraction: Float,
        val bold: Boolean = true,
        val opacity: Float = 1f
    ) : LayerElement

    data class ImageElement(
        override val id: String = UUID.randomUUID().toString(),
        val bitmap: Bitmap,
        val label: String,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val scale: Float = 0.85f,
        val opacity: Float = 1f
    ) : LayerElement

    data class ArtifactElement(
        override val id: String = UUID.randomUUID().toString(),
        val kind: ArtifactKind,
        val colorArgb: Int,
        val intensity: Float = 0.65f,
        val seed: Int = 42,
        val opacity: Float = 1f
    ) : LayerElement
}

data class LayerMetadata(
    val traitCategory: String = "",
    val traitValue: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList()
) {
    val hasContent: Boolean
        get() = traitCategory.isNotBlank() || traitValue.isNotBlank() || notes.isNotBlank() || tags.isNotEmpty()
}

data class CanvasLayer(
    val slotNumber: Int, // 1..8 display number
    val id: String = "layer_$slotNumber",
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val backgroundFillArgb: Int? = null,
    val elements: List<LayerElement> = emptyList(),
    val metadata: LayerMetadata = LayerMetadata()
) {
    val hasVisualContent: Boolean
        get() = backgroundFillArgb != null || elements.isNotEmpty()
}

data class MetadataAttribute(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String
)

data class ArtworkMetadata(
    val title: String = "Untitled 8-Layer Composition",
    val creator: String = "",
    val description: String = "",
    val copyright: String = "All Rights Reserved",
    val edition: String = "1/1",
    val keywords: List<String> = listOf("8-layer", "octacanvas"),
    val customAttributes: List<MetadataAttribute> = emptyList()
)

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
    val subtitle: String
) {
    PNG(
        extension = "png",
        mimeType = "image/png",
        title = "PNG",
        subtitle = "Lossless · Alpha · tEXt Chunks"
    ),
    JPG(
        extension = "jpg",
        mimeType = "image/jpeg",
        title = "JPG",
        subtitle = "Compressed · Quality · EXIF Tags"
    )
}

enum class ExportResolution(
    val pixels: Int,
    val label: String,
    val tag: String
) {
    RES_512(512, "512 × 512", "Draft / Icon"),
    RES_1024(1024, "1024 × 1024", "Standard HD"),
    RES_2048(2048, "2048 × 2048", "2K High-Res"),
    RES_4096(4096, "4096 × 4096", "4K Print")
}

enum class BackdropPreset(
    val label: String,
    val argb: Int
) {
    DARK_STUDIO("Obsidian", 0xFF0C0E14.toInt()),
    PURE_WHITE("Pure White", 0xFFFFFFFF.toInt()),
    PITCH_BLACK("Pitch Black", 0xFF000000.toInt()),
    WARM_PAPER("Warm Paper", 0xFFF4EFE6.toInt()),
    DEEP_INDIGO("Deep Indigo", 0xFF151936.toInt())
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PNG,
    val resolution: ExportResolution = ExportResolution.RES_1024,
    val jpgQuality: Int = 92,
    val transparentBackground: Boolean = true,
    val backdropArgb: Int = BackdropPreset.DARK_STUDIO.argb,
    val embedMetadata: Boolean = true,
    val exportJsonSidecar: Boolean = false,
    val fileName: String = "octacanvas_layer8"
)

data class ExportResult(
    val imageUri: Uri?,
    val displayLocation: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val format: ExportFormat,
    val resolutionPx: Int,
    val metadataEmbedded: Boolean,
    val jsonSidecarLocation: String? = null
)

object StudioPalette {
    val defaultLayerNames = listOf(
        "Backdrop",
        "Ambient Wash",
        "Midground",
        "Primary Subject",
        "Detail Ink",
        "Geometry",
        "Glow & FX",
        "Overlay Stamp"
    )

    val defaultTraitCategories = listOf(
        "Background",
        "Atmosphere",
        "Environment",
        "Character / Focal",
        "Linework",
        "Geometry",
        "Lighting",
        "Watermark / Frame"
    )

    val quickSwatches = listOf(
        Color(0xFF2CE6C8), // Electric Cyan
        Color(0xFFFFB84D), // Studio Amber
        Color(0xFFFF6B7A), // Coral Red
        Color(0xFF7B61FF), // Violet
        Color(0xFF3B82F6), // Cobalt
        Color(0xFF34D399), // Emerald
        Color(0xFFFFFFFF), // Pure White
        Color(0xFF0C0E14), // Obsidian Black
        Color(0xFFF472B6), // Magenta Rose
        Color(0xFFFDE047), // Acid Yellow
        Color(0xFF94A3B8), // Slate Gray
        Color(0xFFFB923C)  // Tangerine
    )
}
