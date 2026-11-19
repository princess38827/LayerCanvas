package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.model.ArtworkMetadata
import com.example.model.CanvasLayer
import com.example.model.LayerMetadata
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetadataStudioContent(
    artworkMetadata: ArtworkMetadata,
    layers: List<CanvasLayer>,
    initialLayerIndex: Int,
    initialTab: Int = 0,
    liveJsonPreview: String,
    onUpdateArtwork: ((ArtworkMetadata) -> ArtworkMetadata) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onAddCustomAttribute: (String, String) -> Unit,
    onRemoveCustomAttribute: (String) -> Unit,
    onUpdateLayerMetadata: (Int, (LayerMetadata) -> LayerMetadata) -> Unit,
    onGoToExport: () -> Unit,
    onOpenAiStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var inspectedLayerIdx by remember(initialLayerIndex) { mutableIntStateOf(initialLayerIndex) }
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Metadata Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Embedded in PNG iTXt chunks & JPG EXIF tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onOpenAiStudio,
                    modifier = Modifier.testTag("metadata_ai_autofill_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("AI Fill")
                }
                FilledTonalButton(
                    onClick = onGoToExport,
                    modifier = Modifier.testTag("metadata_to_export_btn")
                ) {
                    Text("Export")
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Artwork") },
                modifier = Modifier.testTag("meta_tab_artwork")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("8 Layers") },
                modifier = Modifier.testTag("meta_tab_layers")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("JSON Preview") },
                modifier = Modifier.testTag("meta_tab_json")
            )
        }

        when (selectedTab) {
            0 -> {
                var newKeyword by remember { mutableStateOf("") }
                var attrKey by remember { mutableStateOf("") }
                var attrVal by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = artworkMetadata.title,
                        onValueChange = { v -> onUpdateArtwork { it.copy(title = v) } },
                        label = { Text("Artwork Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_title_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = artworkMetadata.creator,
                            onValueChange = { v -> onUpdateArtwork { it.copy(creator = v) } },
                            label = { Text("Creator / Artist") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("meta_creator_input")
                        )
                        OutlinedTextField(
                            value = artworkMetadata.edition,
                            onValueChange = { v -> onUpdateArtwork { it.copy(edition = v) } },
                            label = { Text("Edition") },
                            singleLine = true,
                            modifier = Modifier.width(108.dp)
                        )
                    }

                    OutlinedTextField(
                        value = artworkMetadata.description,
                        onValueChange = { v -> onUpdateArtwork { it.copy(description = v) } },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meta_description_input")
                    )

                    Text("License / Copyright", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All Rights Reserved", "CC BY 4.0", "CC0 1.0", "CC BY-NC 4.0").forEach { preset ->
                            FilterChip(
                                selected = artworkMetadata.copyright == preset,
                                onClick = { onUpdateArtwork { it.copy(copyright = preset) } },
                                label = { Text(preset) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = artworkMetadata.copyright,
                        onValueChange = { v -> onUpdateArtwork { it.copy(copyright = v) } },
                        label = { Text("Copyright Notice") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Keywords", style = MaterialTheme.typography.titleSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKeyword,
                            onValueChange = { newKeyword = it },
                            label = { Text("Add keyword") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                onAddKeyword(newKeyword)
                                newKeyword = ""
                            },
                            enabled = newKeyword.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add keyword")
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        artworkMetadata.keywords.forEach { kw ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveKeyword(kw) },
                                label = { Text(kw) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove $kw",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    Text("Custom Metadata Attributes", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = attrKey,
                            onValueChange = { attrKey = it },
                            label = { Text("Key (e.g. Seed)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = attrVal,
                            onValueChange = { attrVal = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onAddCustomAttribute(attrKey, attrVal)
                                attrKey = ""
                                attrVal = ""
                            },
                            enabled = attrKey.isNotBlank() && attrVal.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add attribute")
                        }
                    }
                    artworkMetadata.customAttributes.forEach { attr ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${attr.key}: ${attr.value}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = { onRemoveCustomAttribute(attr.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove attribute",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }

            1 -> {
                val layer = layers.getOrElse(inspectedLayerIdx) { layers.first() }
                var newLayerTag by remember(inspectedLayerIdx) { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select Layer (1–8)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        layers.forEachIndexed { idx, item ->
                            FilterChip(
                                selected = idx == inspectedLayerIdx,
                                onClick = { inspectedLayerIdx = idx },
                                label = { Text("L${item.slotNumber} · ${item.name}") }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Layer ${layer.slotNumber} · ${layer.name} (${layer.blendMode.displayName}, ${(layer.opacity * 100).toInt()}%)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = layer.metadata.traitCategory,
                                onValueChange = { v ->
                                    onUpdateLayerMetadata(inspectedLayerIdx) { it.copy(traitCategory = v) }
                                },
                                label = { Text("Trait / Role Category") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("layer_meta_category_input")
                            )

                            OutlinedTextField(
                                value = layer.metadata.traitValue,
                                onValueChange = { v ->
                                    onUpdateLayerMetadata(inspectedLayerIdx) { it.copy(traitValue = v) }
                                },
                                label = { Text("Trait Value") },
                                placeholder = { Text("e.g. Electric Cyan Rim") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("layer_meta_value_input")
                            )

                            OutlinedTextField(
                                value = layer.metadata.notes,
                                onValueChange = { v ->
                                    onUpdateLayerMetadata(inspectedLayerIdx) { it.copy(notes = v) }
                                },
                                label = { Text("Layer Notes") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newLayerTag,
                                    onValueChange = { newLayerTag = it },
                                    label = { Text("Layer Tag") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val clean = newLayerTag.trim()
                                        if (clean.isNotEmpty()) {
                                            onUpdateLayerMetadata(inspectedLayerIdx) { meta ->
                                                if (clean in meta.tags) meta else meta.copy(tags = meta.tags + clean)
                                            }
                                            newLayerTag = ""
                                        }
                                    },
                                    enabled = newLayerTag.isNotBlank()
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add layer tag")
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                layer.metadata.tags.forEach { tag ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            onUpdateLayerMetadata(inspectedLayerIdx) { meta ->
                                                meta.copy(tags = meta.tags.filterNot { it == tag })
                                            }
                                        },
                                        label = { Text(tag) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove $tag",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.DataObject,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "octacanvas.layer8.v1",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(liveJsonPreview))
                                copiedBanner = true
                            },
                            modifier = Modifier.testTag("copy_metadata_json_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (copiedBanner) "Copied" else "Copy JSON")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = liveJsonPreview,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
