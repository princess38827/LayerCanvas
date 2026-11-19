package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CanvasLayer
import com.example.model.LayerElement
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

private const val TAG = "GeminiStudioAssistant"
private const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"

data class AiLayerTraitSuggestion(
    val slotNumber: Int,
    val traitCategory: String,
    val traitValue: String,
    val notes: String,
    val tags: List<String>
)

data class AiMetadataSuggestion(
    val title: String,
    val description: String,
    val keywords: List<String>,
    val visualMood: String,
    val colorHarmony: String,
    val layerTraits: List<AiLayerTraitSuggestion>
)

object GeminiStudioAssistant {

    private val metadataSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise, evocative title for this 8-layer digital artwork (3-6 words)"),
            "description" to Schema.string(description = "1-2 sentence artistic & technical description suitable for PNG iTXt / EXIF metadata"),
            "keywords" to Schema.array(
                items = Schema.string(),
                description = "5 to 8 lowercase metadata tags describing style, palette, composition, and motifs"
            ),
            "visualMood" to Schema.string(description = "Short 1-3 word mood attribute (e.g. 'Neon Cyberpunk')"),
            "colorHarmony" to Schema.string(description = "Short 1-3 word palette classification (e.g. 'Cyan & Amber')"),
            "layerTraits" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "slotNumber" to Schema.integer(description = "Layer slot number from 1 to 8"),
                        "traitCategory" to Schema.string(description = "Metadata role/category for this layer"),
                        "traitValue" to Schema.string(description = "Concise trait value describing this layer's visual contribution"),
                        "notes" to Schema.string(description = "Brief technical note on opacity, blend mode, or artifacts used"),
                        "tags" to Schema.array(
                            items = Schema.string(),
                            description = "2 to 4 short lowercase tags for this layer"
                        )
                    )
                ),
                description = "Metadata trait suggestions for active or non-empty layers (slots 1..8)"
            )
        )
    )

    private val metadataModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = metadataSchema
                temperature = 0.6f
            }
        )
    }

    private val coachModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
    }

    suspend fun analyzeArtworkForMetadata(
        compositeBitmap: Bitmap,
        layers: List<CanvasLayer>,
        userNotes: String
    ): AiMetadataSuggestion {
        val layerBrief = buildLayerStackSummary(layers)
        val promptText = buildString {
            appendLine("You are the AI metadata curator for OctaCanvas, an 8-layer composite image studio.")
            appendLine("Inspect the rendered composite canvas image and the 8-layer stack summary below.")
            appendLine("Generate accurate, creative artwork metadata and per-layer trait metadata matching the schema.")
            if (userNotes.isNotBlank()) {
                appendLine("Artist's additional context: $userNotes")
            }
            appendLine()
            appendLine("8-Layer Stack Telemetry:")
            append(layerBrief)
        }

        val response = metadataModel.generateContent(
            content {
                image(compositeBitmap)
                text(promptText)
            }
        )

        val jsonString = response.text
        if (jsonString.isNullOrBlank()) {
            throw IllegalStateException("Gemini returned an empty metadata response.")
        }
        Log.d(TAG, "Received structured metadata JSON (${jsonString.length} chars)")
        return parseMetadataSuggestionJson(jsonString)
    }

    fun streamCreativeFeedback(
        compositeBitmap: Bitmap,
        layers: List<CanvasLayer>,
        userPrompt: String
    ): Flow<String> {
        val layerBrief = buildLayerStackSummary(layers)
        val promptText = buildString {
            appendLine("You are an expert digital art director inside OctaCanvas, an 8-layer canvas editor.")
            appendLine("Analyze the attached composite image and the 8-layer stack configuration below.")
            appendLine("Available tools in OctaCanvas: Brush, Eraser, Line, Rect, Circle, Text, Fill, Photo Import,")
            appendLine("Layer Opacity (0-100%), Blend Modes (Normal, Multiply, Screen, Overlay, Darken, Lighten),")
            appendLine("and Procedural Artifacts (Scanlines, Film Grain, Glitch Bars, Halftone, Viewfinder, Light Dust, Holo Radar, Prism Shards).")
            appendLine()
            appendLine("8-Layer Stack Telemetry:")
            appendLine(layerBrief)
            appendLine()
            appendLine("Artist Request: $userPrompt")
            appendLine("Give concise, actionable, layer-specific advice referencing layer numbers L1-L8.")
        }

        return coachModel.generateContentStream(
            content {
                image(compositeBitmap)
                text(promptText)
            }
        ).mapNotNull { chunk -> chunk.text }
    }

    internal fun buildLayerStackSummary(layers: List<CanvasLayer>): String {
        return buildString {
            layers.forEach { layer ->
                val artifactNames = layer.elements
                    .filterIsInstance<LayerElement.ArtifactElement>()
                    .joinToString(", ") { it.kind.displayName }
                val textItems = layer.elements
                    .filterIsInstance<LayerElement.TextElement>()
                    .joinToString(", ") { "\"${it.text}\"" }
                val status = if (layer.isVisible) "visible" else "hidden"
                val contentDesc = when {
                    !layer.hasVisualContent -> "empty"
                    else -> buildList {
                        if (layer.backgroundFillArgb != null) add("filled")
                        if (layer.elements.isNotEmpty()) add("${layer.elements.size} elements")
                        if (artifactNames.isNotEmpty()) add("artifacts=[$artifactNames]")
                        if (textItems.isNotEmpty()) add("text=[$textItems]")
                    }.joinToString(", ")
                }
                appendLine(
                    "L${layer.slotNumber} (${layer.name}): $status, opacity=${(layer.opacity * 100).toInt()}%, " +
                        "blend=${layer.blendMode.displayName}, $contentDesc"
                )
            }
        }
    }

    internal fun parseMetadataSuggestionJson(jsonString: String): AiMetadataSuggestion {
        val obj = JSONObject(jsonString)
        val title = obj.optString("title", "").trim().ifEmpty { "Untitled Composition" }
        val description = obj.optString("description", "").trim()
        val visualMood = obj.optString("visualMood", "").trim()
        val colorHarmony = obj.optString("colorHarmony", "").trim()

        val keywordsArr = obj.optJSONArray("keywords")
        val keywords = buildList {
            if (keywordsArr != null) {
                for (i in 0 until keywordsArr.length()) {
                    val kw = keywordsArr.optString(i).trim()
                    if (kw.isNotEmpty()) add(kw)
                }
            }
        }

        val traitsArr = obj.optJSONArray("layerTraits")
        val layerTraits = buildList {
            if (traitsArr != null) {
                for (i in 0 until traitsArr.length()) {
                    val item = traitsArr.optJSONObject(i) ?: continue
                    val slot = item.optInt("slotNumber", -1)
                    if (slot !in 1..8) continue
                    val tagsArr = item.optJSONArray("tags")
                    val tags = buildList {
                        if (tagsArr != null) {
                            for (j in 0 until tagsArr.length()) {
                                val t = tagsArr.optString(j).trim()
                                if (t.isNotEmpty()) add(t)
                            }
                        }
                    }
                    add(
                        AiLayerTraitSuggestion(
                            slotNumber = slot,
                            traitCategory = item.optString("traitCategory", "").trim(),
                            traitValue = item.optString("traitValue", "").trim(),
                            notes = item.optString("notes", "").trim(),
                            tags = tags
                        )
                    )
                }
            }
        }

        return AiMetadataSuggestion(
            title = title,
            description = description,
            keywords = keywords,
            visualMood = visualMood,
            colorHarmony = colorHarmony,
            layerTraits = layerTraits
        )
    }
}
