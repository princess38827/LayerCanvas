package com.example.engine

import com.example.model.ArtworkMetadata
import com.example.model.CanvasLayer
import com.example.model.LAYER_COUNT
import com.example.model.LayerBlendMode
import com.example.model.LayerElement
import com.example.model.LayerMetadata
import com.example.model.MetadataAttribute
import com.example.model.NormalizedPoint
import com.example.model.ShapeKind
import com.example.model.StudioPalette

object TemplateGenerator {

    fun createEmptyEightLayers(): List<CanvasLayer> {
        return (1..LAYER_COUNT).map { slot ->
            CanvasLayer(
                slotNumber = slot,
                id = "layer_$slot",
                name = StudioPalette.defaultLayerNames[slot - 1],
                isVisible = true,
                isLocked = false,
                opacity = 1f,
                blendMode = LayerBlendMode.NORMAL,
                metadata = LayerMetadata(
                    traitCategory = StudioPalette.defaultTraitCategories[slot - 1]
                )
            )
        }
    }

    /**
     * Builds an 8-layer geometric horizon composition with pre-filled per-layer blend modes
     * and metadata traits so creators can test visibility, blending, and PNG/JPG metadata export immediately.
     */
    fun createShowcaseComposition(): Pair<List<CanvasLayer>, ArtworkMetadata> {
        val layers = listOf(
            // Layer 1: Backdrop
            CanvasLayer(
                slotNumber = 1,
                id = "layer_1",
                name = "01 · Midnight Sky",
                opacity = 1f,
                blendMode = LayerBlendMode.NORMAL,
                backgroundFillArgb = 0xFF0E1324.toInt(),
                metadata = LayerMetadata(
                    traitCategory = "Background",
                    traitValue = "Deep Obsidian Indigo",
                    notes = "Base sky canvas fill",
                    tags = listOf("backdrop", "night")
                )
            ),
            // Layer 2: Ambient Wash
            CanvasLayer(
                slotNumber = 2,
                id = "layer_2",
                name = "02 · Horizon Glow",
                opacity = 0.85f,
                blendMode = LayerBlendMode.SCREEN,
                elements = listOf(
                    LayerElement.ShapeElement(
                        kind = ShapeKind.CIRCLE,
                        start = NormalizedPoint(0.15f, 0.25f),
                        end = NormalizedPoint(0.85f, 0.95f),
                        colorArgb = 0x552CE6C8.toInt(),
                        strokeWidthFraction = 0.01f,
                        filled = true
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.CIRCLE,
                        start = NormalizedPoint(0.28f, 0.30f),
                        end = NormalizedPoint(0.72f, 0.74f),
                        colorArgb = 0x66FFB84D.toInt(),
                        strokeWidthFraction = 0.01f,
                        filled = true
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Atmosphere",
                    traitValue = "Cyan-Amber Halo",
                    notes = "Dual radial glow using Screen blend",
                    tags = listOf("glow", "ambient")
                )
            ),
            // Layer 3: Midground Solar Disc
            CanvasLayer(
                slotNumber = 3,
                id = "layer_3",
                name = "03 · Solar Disc",
                opacity = 0.95f,
                blendMode = LayerBlendMode.NORMAL,
                elements = listOf(
                    LayerElement.ShapeElement(
                        kind = ShapeKind.CIRCLE,
                        start = NormalizedPoint(0.32f, 0.22f),
                        end = NormalizedPoint(0.68f, 0.58f),
                        colorArgb = 0xFFFFB84D.toInt(),
                        strokeWidthFraction = 0.01f,
                        filled = true
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Celestial",
                    traitValue = "Amber Orbit",
                    notes = "Primary focal solar geometry",
                    tags = listOf("sun", "amber")
                )
            ),
            // Layer 4: Primary Subject - Mountain Peaks
            CanvasLayer(
                slotNumber = 4,
                id = "layer_4",
                name = "04 · Ridge Silhouette",
                opacity = 1f,
                blendMode = LayerBlendMode.NORMAL,
                elements = listOf(
                    LayerElement.PathStroke(
                        points = listOf(
                            NormalizedPoint(0.0f, 0.68f),
                            NormalizedPoint(0.18f, 0.50f),
                            NormalizedPoint(0.34f, 0.62f),
                            NormalizedPoint(0.54f, 0.42f),
                            NormalizedPoint(0.72f, 0.58f),
                            NormalizedPoint(0.86f, 0.48f),
                            NormalizedPoint(1.0f, 0.66f)
                        ),
                        colorArgb = 0xFF172038.toInt(),
                        strokeWidthFraction = 0.065f
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.RECTANGLE,
                        start = NormalizedPoint(0.0f, 0.62f),
                        end = NormalizedPoint(1.0f, 1.0f),
                        colorArgb = 0xFF101628.toInt(),
                        strokeWidthFraction = 0.01f,
                        filled = true
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Terrain",
                    traitValue = "Obsidian Peaks",
                    notes = "Foreground ridge silhouette & horizon floor",
                    tags = listOf("mountain", "silhouette")
                )
            ),
            // Layer 5: Detail Ink - Contour Highlight
            CanvasLayer(
                slotNumber = 5,
                id = "layer_5",
                name = "05 · Neon Rim Ink",
                opacity = 0.9f,
                blendMode = LayerBlendMode.NORMAL,
                elements = listOf(
                    LayerElement.PathStroke(
                        points = listOf(
                            NormalizedPoint(0.02f, 0.66f),
                            NormalizedPoint(0.18f, 0.48f),
                            NormalizedPoint(0.34f, 0.60f),
                            NormalizedPoint(0.54f, 0.40f),
                            NormalizedPoint(0.72f, 0.56f),
                            NormalizedPoint(0.86f, 0.46f),
                            NormalizedPoint(0.98f, 0.64f)
                        ),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.012f
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Linework",
                    traitValue = "Electric Cyan Rim",
                    notes = "Crisp vector ridge contour",
                    tags = listOf("cyan", "lineart")
                )
            ),
            // Layer 6: Geometry Grid
            CanvasLayer(
                slotNumber = 6,
                id = "layer_6",
                name = "06 · Horizon Grid",
                opacity = 0.55f,
                blendMode = LayerBlendMode.LIGHTEN,
                elements = listOf(
                    LayerElement.ShapeElement(
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.05f, 0.72f),
                        end = NormalizedPoint(0.95f, 0.72f),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.006f,
                        filled = false
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.05f, 0.80f),
                        end = NormalizedPoint(0.95f, 0.80f),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.006f,
                        filled = false
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.05f, 0.88f),
                        end = NormalizedPoint(0.95f, 0.88f),
                        colorArgb = 0xFFFF6B7A.toInt(),
                        strokeWidthFraction = 0.006f,
                        filled = false
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.50f, 0.64f),
                        end = NormalizedPoint(0.15f, 0.96f),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.005f,
                        filled = false
                    ),
                    LayerElement.ShapeElement(
                        kind = ShapeKind.LINE,
                        start = NormalizedPoint(0.50f, 0.64f),
                        end = NormalizedPoint(0.85f, 0.96f),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.005f,
                        filled = false
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Geometry",
                    traitValue = "Perspective Grid",
                    notes = "Synthesized perspective grid lines",
                    tags = listOf("grid", "synth")
                )
            ),
            // Layer 7: Glow & FX
            CanvasLayer(
                slotNumber = 7,
                id = "layer_7",
                name = "07 · Starlight & Rings",
                opacity = 0.85f,
                blendMode = LayerBlendMode.SCREEN,
                elements = listOf(
                    LayerElement.ShapeElement(
                        kind = ShapeKind.CIRCLE,
                        start = NormalizedPoint(0.24f, 0.14f),
                        end = NormalizedPoint(0.76f, 0.66f),
                        colorArgb = 0x88FF6B7A.toInt(),
                        strokeWidthFraction = 0.007f,
                        filled = false
                    ),
                    LayerElement.PathStroke(
                        points = listOf(NormalizedPoint(0.16f, 0.18f)),
                        colorArgb = 0xFFFFFFFF.toInt(),
                        strokeWidthFraction = 0.015f
                    ),
                    LayerElement.PathStroke(
                        points = listOf(NormalizedPoint(0.82f, 0.22f)),
                        colorArgb = 0xFF2CE6C8.toInt(),
                        strokeWidthFraction = 0.014f
                    ),
                    LayerElement.PathStroke(
                        points = listOf(NormalizedPoint(0.76f, 0.12f)),
                        colorArgb = 0xFFFFB84D.toInt(),
                        strokeWidthFraction = 0.011f
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Lighting",
                    traitValue = "Orbital Ring & Stars",
                    notes = "Screen-blended star nodes and coral orbital ring",
                    tags = listOf("fx", "orbital")
                )
            ),
            // Layer 8: Overlay Stamp
            CanvasLayer(
                slotNumber = 8,
                id = "layer_8",
                name = "08 · Edition Frame",
                opacity = 0.92f,
                blendMode = LayerBlendMode.NORMAL,
                elements = listOf(
                    LayerElement.ShapeElement(
                        kind = ShapeKind.RECTANGLE,
                        start = NormalizedPoint(0.04f, 0.04f),
                        end = NormalizedPoint(0.96f, 0.96f),
                        colorArgb = 0x66FFFFFF.toInt(),
                        strokeWidthFraction = 0.005f,
                        filled = false
                    ),
                    LayerElement.TextElement(
                        text = "OCTA // L8",
                        position = NormalizedPoint(0.50f, 0.92f),
                        colorArgb = 0xFFE2E8F0.toInt(),
                        fontSizeFraction = 0.038f,
                        bold = true
                    )
                ),
                metadata = LayerMetadata(
                    traitCategory = "Overlay",
                    traitValue = "Series Frame Stamp",
                    notes = "Outer bounding frame & edition typography",
                    tags = listOf("frame", "stamp")
                )
            )
        )

        val artwork = ArtworkMetadata(
            title = "Neon Horizon — 8-Layer Study",
            creator = "OctaCanvas Artist",
            description = "Eight-layer composite demonstration featuring atmospheric blending, vector contours, and embedded layer trait metadata.",
            copyright = "CC BY 4.0",
            edition = "1/1",
            keywords = listOf("8-layer", "horizon", "metadata", "octacanvas"),
            customAttributes = listOf(
                MetadataAttribute(key = "Palette", value = "Obsidian / Cyan / Amber"),
                MetadataAttribute(key = "Layers", value = "8 Active")
            )
        )

        return layers to artwork
    }
}
