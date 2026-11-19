package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.MediaExporter
import com.example.model.BackdropPreset
import com.example.model.CanvasLayer
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportResolution
import com.example.ui.ExportUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDownloadContent(
    layers: List<CanvasLayer>,
    options: ExportOptions,
    previewBitmap: Bitmap?,
    estimatedSizeBytes: Long?,
    exportUiState: ExportUiState,
    onUpdateOptions: ((ExportOptions) -> ExportOptions) -> Unit,
    onDownloadToDevice: () -> Unit,
    onSaveAsDocument: () -> Unit,
    onShareExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = layers.count { it.isVisible }
    val metadataLayerCount = layers.count { it.metadata.hasContent }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Composite Preview Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCheckerboardBackdrop(size)
                }
                if (previewBitmap != null && !previewBitmap.isRecycled) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Composite export preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Export & Download",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${options.resolution.label} · ${options.format.title}" +
                        if (options.format == ExportFormat.JPG) " (${options.jpgQuality}%)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$visibleCount/8 layers active · $metadataLayerCount with metadata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (estimatedSizeBytes != null)
                        "Est. size: ~${MediaExporter.formatBytes(estimatedSizeBytes)}"
                    else "Calculating size...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Format Selection (PNG / JPG)
        Text("Image Format", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExportFormat.entries.forEach { fmt ->
                val selected = options.format == fmt
                Surface(
                    onClick = { onUpdateOptions { it.copy(format = fmt) } },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_format_${fmt.name.lowercase()}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = fmt.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = fmt.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Resolution Selection (512, 1024, 2048, 4096)
        Text("Resolution", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportResolution.entries.forEach { res ->
                val selected = options.resolution == res
                Surface(
                    onClick = { onUpdateOptions { it.copy(resolution = res) } },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("export_res_${res.pixels}")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = res.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = res.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // JPG Quality Slider (only when JPG is selected)
        AnimatedVisibility(visible = options.format == ExportFormat.JPG) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JPG Compression Quality", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${options.jpgQuality}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = options.jpgQuality.toFloat(),
                        onValueChange = { v ->
                            onUpdateOptions { it.copy(jpgQuality = v.toInt().coerceIn(10, 100)) }
                        },
                        valueRange = 10f..100f,
                        modifier = Modifier.testTag("jpg_quality_slider")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(75 to "75% Web", 92 to "92% High", 100 to "100% Max").forEach { (q, lbl) ->
                            FilterChip(
                                selected = options.jpgQuality == q,
                                onClick = { onUpdateOptions { it.copy(jpgQuality = q) } },
                                label = { Text(lbl) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // PNG Transparency & Backdrop Color Options
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (options.format == ExportFormat.PNG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Transparent Background", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Preserve alpha channel for compositing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = options.transparentBackground,
                            onCheckedChange = { checked ->
                                onUpdateOptions { it.copy(transparentBackground = checked) }
                            },
                            modifier = Modifier.testTag("png_transparent_switch")
                        )
                    }
                }

                if (options.format == ExportFormat.JPG || !options.transparentBackground) {
                    Text(
                        text = if (options.format == ExportFormat.JPG)
                            "Flatten Backdrop Color (JPG has no alpha)"
                        else "Solid Backdrop Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackdropPreset.entries.forEach { preset ->
                            val picked = options.backdropArgb == preset.argb
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        onUpdateOptions { it.copy(backdropArgb = preset.argb) }
                                    }
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.argb))
                                        .border(
                                            width = if (picked) 2.5.dp else 1.dp,
                                            color = if (picked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (picked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Metadata Embedding Options
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Embed 8-Layer Metadata", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (options.format == ExportFormat.PNG)
                                "Writes Title, Author, License & Layer JSON into PNG iTXt chunks"
                            else "Writes Artist, Copyright & Layer JSON into JPG EXIF tags",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = options.embedMetadata,
                        onCheckedChange = { checked ->
                            onUpdateOptions { it.copy(embedMetadata = checked) }
                        },
                        modifier = Modifier.testTag("embed_metadata_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Also Save .json Sidecar", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Saves standalone metadata manifest to Downloads/OctaCanvas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = options.exportJsonSidecar,
                        onCheckedChange = { checked ->
                            onUpdateOptions { it.copy(exportJsonSidecar = checked) }
                        },
                        modifier = Modifier.testTag("export_json_sidecar_switch")
                    )
                }
            }
        }

        // Filename input
        OutlinedTextField(
            value = options.fileName,
            onValueChange = { v -> onUpdateOptions { it.copy(fileName = v) } },
            label = { Text("Export Filename") },
            suffix = { Text(".${options.format.extension}", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_filename_input")
        )

        // Status Feedback Card
        when (exportUiState) {
            is ExportUiState.Success -> {
                val res = exportUiState.result
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_success_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Export succeeded",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Saved ${res.fileName} (${MediaExporter.formatBytes(res.fileSizeBytes)})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Location: ${res.displayLocation}" +
                                    (res.jsonSidecarLocation?.let { "\nMetadata JSON: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            is ExportUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Export error",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = exportUiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            else -> Unit
        }

        // Action Buttons: Download + Save As + Share
        val isBusy = exportUiState is ExportUiState.Exporting
        Button(
            onClick = onDownloadToDevice,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("download_image_button")
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("Rendering ${options.resolution.label}...")
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Download ${options.format.title} (${options.resolution.label})")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSaveAsDocument,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_as_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Save As...")
            }
            OutlinedButton(
                onClick = onShareExport,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("share_export_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
