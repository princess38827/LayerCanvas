package com.example.engine

import androidx.exifinterface.media.ExifInterface
import com.example.model.ArtworkMetadata
import com.example.model.CanvasLayer
import com.example.model.ExportOptions
import com.example.model.LayerElement
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32

object MetadataWriter {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun buildMetadataJson(
        artwork: ArtworkMetadata,
        layers: List<CanvasLayer>,
        exportOptions: ExportOptions,
        exportedAtIso: String = currentIsoTimestamp()
    ): String {
        val root = JSONObject()
        root.put("schema", "octacanvas.layer8.v1")
        root.put("software", "OctaCanvas Studio")
        root.put("exportedAt", exportedAtIso)

        val exportObj = JSONObject().apply {
            put("format", exportOptions.format.name)
            put("resolution", "${exportOptions.resolution.pixels}x${exportOptions.resolution.pixels}")
            if (exportOptions.format.name == "JPG") {
                put("jpgQuality", exportOptions.jpgQuality)
            } else {
                put("transparentBackground", exportOptions.transparentBackground)
            }
        }
        root.put("export", exportObj)

        val artworkObj = JSONObject().apply {
            put("title", artwork.title)
            put("creator", artwork.creator)
            put("description", artwork.description)
            put("copyright", artwork.copyright)
            put("edition", artwork.edition)
            put("keywords", JSONArray(artwork.keywords))
            val attrsObj = JSONObject()
            artwork.customAttributes.forEach { attr ->
                if (attr.key.isNotBlank()) {
                    attrsObj.put(attr.key.trim(), attr.value.trim())
                }
            }
            put("attributes", attrsObj)
        }
        root.put("artwork", artworkObj)

        val layersArray = JSONArray()
        layers.forEachIndexed { idx, layer ->
            val layerObj = JSONObject().apply {
                put("slot", idx + 1)
                put("name", layer.name)
                put("visible", layer.isVisible)
                put("locked", layer.isLocked)
                put("opacity", ((layer.opacity * 100).toInt()) / 100.0)
                put("blendMode", layer.blendMode.name)
                put("hasBackgroundFill", layer.backgroundFillArgb != null)
                put("elementCount", layer.elements.size)
                val artifactsArray = JSONArray()
                layer.elements.filterIsInstance<LayerElement.ArtifactElement>().forEach { art ->
                    artifactsArray.put(
                        JSONObject().apply {
                            put("kind", art.kind.name)
                            put("label", art.kind.displayName)
                            put("intensity", ((art.intensity * 100).toInt()) / 100.0)
                            put("seed", art.seed)
                        }
                    )
                }
                put("artifacts", artifactsArray)
                val metaObj = JSONObject().apply {
                    put("traitCategory", layer.metadata.traitCategory)
                    put("traitValue", layer.metadata.traitValue)
                    put("notes", layer.metadata.notes)
                    put("tags", JSONArray(layer.metadata.tags))
                }
                put("metadata", metaObj)
            }
            layersArray.put(layerObj)
        }
        root.put("layers", layersArray)

        return root.toString(2)
    }

    /**
     * Injects UTF-8 PNG iTXt metadata chunks right before the IEND chunk, updating CRC32 checksums.
     */
    fun injectPngMetadataChunks(
        pngBytes: ByteArray,
        artwork: ArtworkMetadata,
        manifestJson: String
    ): ByteArray {
        if (pngBytes.size < 16 || !pngBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            return pngBytes
        }

        val entries = linkedMapOf<String, String>()
        if (artwork.title.isNotBlank()) entries["Title"] = artwork.title
        if (artwork.creator.isNotBlank()) entries["Author"] = artwork.creator
        if (artwork.description.isNotBlank()) entries["Description"] = artwork.description
        if (artwork.copyright.isNotBlank()) entries["Copyright"] = artwork.copyright
        entries["Software"] = "OctaCanvas 8-Layer Studio"
        entries["Creation Time"] = currentIsoTimestamp()
        entries["OctaCanvas-Layer8-Metadata"] = manifestJson

        val out = ByteArrayOutputStream(pngBytes.size + manifestJson.length + 512)
        out.write(PNG_SIGNATURE)

        var pos = 8
        var inserted = false
        while (pos + 12 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, pos, 4).int
            val typeBytes = pngBytes.copyOfRange(pos + 4, pos + 8)
            val chunkType = String(typeBytes, StandardCharsets.US_ASCII)
            val totalChunkLen = 12 + length
            if (length < 0 || pos + totalChunkLen > pngBytes.size) {
                return pngBytes
            }

            if (chunkType == "IEND" && !inserted) {
                for ((key, value) in entries) {
                    writeITXtChunk(out, key, value)
                }
                inserted = true
            }

            out.write(pngBytes, pos, totalChunkLen)
            pos += totalChunkLen
        }

        return out.toByteArray()
    }

    private fun writeITXtChunk(
        out: ByteArrayOutputStream,
        keyword: String,
        text: String
    ) {
        val safeKeyword = keyword.take(79).toByteArray(StandardCharsets.ISO_8859_1)
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)

        val payload = ByteArrayOutputStream(safeKeyword.size + textBytes.size + 6)
        payload.write(safeKeyword)
        payload.write(0) // Null separator
        payload.write(0) // Compression flag (uncompressed)
        payload.write(0) // Compression method
        payload.write(0) // Empty language tag + null
        payload.write(0) // Empty translated keyword + null
        payload.write(textBytes)

        val data = payload.toByteArray()
        val type = "iTXt".toByteArray(StandardCharsets.US_ASCII)

        val crc = CRC32().apply {
            update(type)
            update(data)
        }.value.toInt()

        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(type)
        out.write(data)
        out.write(ByteBuffer.allocate(4).putInt(crc).array())
    }

    /**
     * Writes standard EXIF metadata tags + full 8-layer JSON manifest into a JPEG file.
     */
    fun writeJpgExifMetadata(
        jpgFile: File,
        artwork: ArtworkMetadata,
        manifestJson: String
    ) {
        val exif = ExifInterface(jpgFile)
        val desc = buildString {
            if (artwork.title.isNotBlank()) append(artwork.title)
            if (artwork.description.isNotBlank()) {
                if (isNotEmpty()) append(" — ")
                append(artwork.description)
            }
        }
        if (desc.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
        }
        if (artwork.creator.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_ARTIST, artwork.creator)
        }
        if (artwork.copyright.isNotBlank()) {
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, artwork.copyright)
        }
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "OctaCanvas 8-Layer Studio")
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, currentExifTimestamp())
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, manifestJson)
        exif.saveAttributes()
    }

    fun currentIsoTimestamp(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun currentExifTimestamp(): String {
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return fmt.format(Date())
    }
}
