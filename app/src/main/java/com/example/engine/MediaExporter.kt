package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.ArtworkMetadata
import com.example.model.CanvasLayer
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MediaExporter {

    data class EncodedExportPayload(
        val imageBytes: ByteArray,
        val jsonManifest: String,
        val sanitizedFileName: String
    )

    suspend fun encodeComposition(
        layers: List<CanvasLayer>,
        artworkMetadata: ArtworkMetadata,
        options: ExportOptions
    ): EncodedExportPayload = withContext(Dispatchers.Default) {
        val sizePx = options.resolution.pixels
        val effectiveTransparent =
            options.format == ExportFormat.PNG && options.transparentBackground

        val bitmap = CanvasRenderer.renderCompositeBitmap(
            layers = layers,
            sizePx = sizePx,
            transparentBackground = effectiveTransparent,
            backdropArgb = options.backdropArgb
        )

        val manifestJson = MetadataWriter.buildMetadataJson(
            artwork = artworkMetadata,
            layers = layers,
            exportOptions = options
        )

        val rawBytes = try {
            when (options.format) {
                ExportFormat.PNG -> {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val png = baos.toByteArray()
                    if (options.embedMetadata) {
                        MetadataWriter.injectPngMetadataChunks(png, artworkMetadata, manifestJson)
                    } else {
                        png
                    }
                }
                ExportFormat.JPG -> {
                    val tempFile = File.createTempFile("octa_jpg_", ".jpg")
                    try {
                        FileOutputStream(tempFile).use { fos ->
                            bitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                options.jpgQuality.coerceIn(10, 100),
                                fos
                            )
                        }
                        if (options.embedMetadata) {
                            MetadataWriter.writeJpgExifMetadata(tempFile, artworkMetadata, manifestJson)
                        }
                        tempFile.readBytes()
                    } finally {
                        tempFile.delete()
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }

        val cleanBase = sanitizeFileName(options.fileName)
        EncodedExportPayload(
            imageBytes = rawBytes,
            jsonManifest = manifestJson,
            sanitizedFileName = "$cleanBase.${options.format.extension}"
        )
    }

    /**
     * Downloads the composite image directly into Pictures/OctaCanvas using MediaStore (API 29+)
     * or app-external Pictures folder + media index fallback on API 24-28 with zero runtime permissions.
     */
    suspend fun downloadToDevice(
        context: Context,
        layers: List<CanvasLayer>,
        artworkMetadata: ArtworkMetadata,
        options: ExportOptions
    ): ExportResult = withContext(Dispatchers.IO) {
        val payload = encodeComposition(layers, artworkMetadata, options)

        var savedImageUri: Uri?
        var displayLocation: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, payload.sanitizedFileName)
                put(MediaStore.Images.Media.MIME_TYPE, options.format.mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/OctaCanvas"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create MediaStore image entry")

            resolver.openOutputStream(uri)?.use { out ->
                out.write(payload.imageBytes)
            } ?: throw IllegalStateException("Unable to open output stream for MediaStore URI")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            savedImageUri = uri
            displayLocation = "Pictures/OctaCanvas/${payload.sanitizedFileName}"
        } else {
            val exportDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "OctaCanvas"
            ).apply { mkdirs() }
            val outFile = File(exportDir, payload.sanitizedFileName)
            FileOutputStream(outFile).use { it.write(payload.imageBytes) }
            savedImageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                copyToShareCache(context, payload.sanitizedFileName, payload.imageBytes)
            )
            displayLocation = "OctaCanvas/${payload.sanitizedFileName}"
        }

        var sidecarLocation: String? = null
        if (options.exportJsonSidecar) {
            val jsonName = "${sanitizeFileName(options.fileName)}_metadata.json"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val jsonValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, jsonName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/OctaCanvas"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val jsonUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, jsonValues)
                if (jsonUri != null) {
                    resolver.openOutputStream(jsonUri)?.use { out ->
                        out.write(payload.jsonManifest.toByteArray(Charsets.UTF_8))
                    }
                    jsonValues.clear()
                    jsonValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(jsonUri, jsonValues, null, null)
                    sidecarLocation = "Downloads/OctaCanvas/$jsonName"
                }
            } else {
                val docDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "OctaCanvas"
                ).apply { mkdirs() }
                File(docDir, jsonName).writeText(payload.jsonManifest, Charsets.UTF_8)
                sidecarLocation = "OctaCanvas/$jsonName"
            }
        }

        ExportResult(
            imageUri = savedImageUri,
            displayLocation = displayLocation,
            fileName = payload.sanitizedFileName,
            fileSizeBytes = payload.imageBytes.size.toLong(),
            format = options.format,
            resolutionPx = options.resolution.pixels,
            metadataEmbedded = options.embedMetadata,
            jsonSidecarLocation = sidecarLocation
        )
    }

    /**
     * Writes the encoded composite image directly to a SAF Uri chosen via CreateDocument.
     */
    suspend fun exportToUri(
        context: Context,
        targetUri: Uri,
        layers: List<CanvasLayer>,
        artworkMetadata: ArtworkMetadata,
        options: ExportOptions
    ): ExportResult = withContext(Dispatchers.IO) {
        val payload = encodeComposition(layers, artworkMetadata, options)
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(payload.imageBytes)
        } ?: throw IllegalStateException("Could not write to selected document URI")

        ExportResult(
            imageUri = targetUri,
            displayLocation = payload.sanitizedFileName,
            fileName = payload.sanitizedFileName,
            fileSizeBytes = payload.imageBytes.size.toLong(),
            format = options.format,
            resolutionPx = options.resolution.pixels,
            metadataEmbedded = options.embedMetadata
        )
    }

    /**
     * Prepares a cached FileProvider URI for instant Android Sharesheet export.
     */
    suspend fun prepareShareUri(
        context: Context,
        layers: List<CanvasLayer>,
        artworkMetadata: ArtworkMetadata,
        options: ExportOptions
    ): Pair<Uri, ExportResult> = withContext(Dispatchers.IO) {
        val payload = encodeComposition(layers, artworkMetadata, options)
        val cachedFile = copyToShareCache(context, payload.sanitizedFileName, payload.imageBytes)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedFile
        )
        val result = ExportResult(
            imageUri = shareUri,
            displayLocation = "Share Sheet · ${payload.sanitizedFileName}",
            fileName = payload.sanitizedFileName,
            fileSizeBytes = payload.imageBytes.size.toLong(),
            format = options.format,
            resolutionPx = options.resolution.pixels,
            metadataEmbedded = options.embedMetadata
        )
        shareUri to result
    }

    private fun copyToShareCache(context: Context, fileName: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
        return if (cleaned.isBlank()) "octacanvas_layer8" else cleaned
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
