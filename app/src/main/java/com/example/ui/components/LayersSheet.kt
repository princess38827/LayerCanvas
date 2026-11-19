package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CanvasLayer
import com.example.model.LayerBlendMode

@Composable
fun LayersPanelContent(
    layers: List<CanvasLayer>,
    activeLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onToggleLock: (Int) -> Unit,
    onOpacityChange: (Int, Float) -> Unit,
    onOpacityChangeFinished: () -> Unit = {},
    onBlendModeChange: (Int, LayerBlendMode) -> Unit,
    onMoveLayer: (index: Int, delta: Int) -> Unit,
    onRenameLayer: (Int) -> Unit,
    onClearLayer: (Int) -> Unit,
    onEditLayerMetadata: (Int) -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    undoLabel: String? = null,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Render top-to-bottom in the UI (Layer 8 at top down to Layer 1 at bottom)
    val reversedIndices = (layers.lastIndex downTo 0).toList()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "8-Layer Stack",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (undoLabel != null) "Undo: $undoLabel"
                    else "${layers.count { it.isVisible }} visible · ${layers.count { it.hasVisualContent }} with artwork",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("layers_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.cd_undo)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("layers_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = stringResource(R.string.cd_redo)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "TOP → BASE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("layers_lazy_column")
        ) {
            itemsIndexed(reversedIndices, key = { _, idx -> layers[idx].id }) { _, layerIdx ->
                val layer = layers[layerIdx]
                val isSelected = layerIdx == activeLayerIndex

                LayerRowCard(
                    layer = layer,
                    layerIdx = layerIdx,
                    totalCount = layers.size,
                    isSelected = isSelected,
                    onSelect = { onSelectLayer(layerIdx) },
                    onToggleVisibility = { onToggleVisibility(layerIdx) },
                    onToggleLock = { onToggleLock(layerIdx) },
                    onOpacityChange = { onOpacityChange(layerIdx, it) },
                    onOpacityChangeFinished = onOpacityChangeFinished,
                    onBlendModeChange = { onBlendModeChange(layerIdx, it) },
                    onMoveUp = { onMoveLayer(layerIdx, +1) },
                    onMoveDown = { onMoveLayer(layerIdx, -1) },
                    onRename = { onRenameLayer(layerIdx) },
                    onClear = { onClearLayer(layerIdx) },
                    onEditMetadata = { onEditLayerMetadata(layerIdx) }
                )
            }
        }
    }
}

@Composable
private fun LayerRowCard(
    layer: CanvasLayer,
    layerIdx: Int,
    totalCount: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onOpacityChangeFinished: () -> Unit,
    onBlendModeChange: (LayerBlendMode) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onEditMetadata: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("layer_card_${layer.slotNumber}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slot Badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (layer.backgroundFillArgb != null) Color(layer.backgroundFillArgb)
                            else if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "0${layer.slotNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = layer.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (layer.metadata.traitValue.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = layer.metadata.traitValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${layer.blendMode.displayName} · ${(layer.opacity * 100).toInt()}% · ${layer.elements.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.testTag("layer_lock_${layer.slotNumber}")
                ) {
                    Icon(
                        imageVector = if (layer.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = stringResource(R.string.cd_layer_lock),
                        tint = if (layer.isLocked) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.testTag("layer_visibility_${layer.slotNumber}")
                ) {
                    Icon(
                        imageVector = if (layer.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(R.string.cd_layer_visibility),
                        tint = if (layer.isVisible) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Always-visible per-layer Opacity Slider (for all 8 layers)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Opacity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = layer.opacity,
                    onValueChange = onOpacityChange,
                    onValueChangeFinished = onOpacityChangeFinished,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("layer_opacity_slider_${layer.slotNumber}")
                )
                Text(
                    text = "${(layer.opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (layer.opacity < 1f) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(38.dp)
                )
            }

            AnimatedVisibility(visible = isSelected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Opacity Presets for precision tapping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (presetVal, label) ->
                            val isApprox = kotlin.math.abs(layer.opacity - presetVal) < 0.02f
                            FilterChip(
                                selected = isApprox,
                                onClick = {
                                    onOpacityChangeFinished()
                                    onOpacityChange(presetVal)
                                    onOpacityChangeFinished()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("layer_${layer.slotNumber}_opacity_$label")
                            )
                        }
                    }

                    // Blend Mode Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LayerBlendMode.entries.forEach { mode ->
                            val picked = layer.blendMode == mode
                            FilterChip(
                                selected = picked,
                                onClick = { onBlendModeChange(mode) },
                                label = {
                                    Text(mode.displayName, style = MaterialTheme.typography.labelMedium)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Layer Utility Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = layerIdx < totalCount - 1,
                                modifier = Modifier.testTag("layer_up_${layer.slotNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = stringResource(R.string.cd_move_layer_up),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = layerIdx > 0,
                                modifier = Modifier.testTag("layer_down_${layer.slotNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = stringResource(R.string.cd_move_layer_down),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onRename,
                                modifier = Modifier.testTag("layer_rename_${layer.slotNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename layer",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onClear,
                                enabled = !layer.isLocked && layer.hasVisualContent,
                                modifier = Modifier.testTag("layer_clear_${layer.slotNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_clear_layer),
                                    tint = if (!layer.isLocked && layer.hasVisualContent)
                                        MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        TextButton(
                            onClick = onEditMetadata,
                            modifier = Modifier.testTag("layer_metadata_btn_${layer.slotNumber}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Label,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Layer Metadata")
                        }
                    }
                }
            }
        }
    }
}
