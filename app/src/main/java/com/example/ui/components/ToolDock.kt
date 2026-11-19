package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CanvasLayer
import com.example.model.DrawingTool
import com.example.model.StudioPalette

@Composable
fun ToolDock(
    layers: List<CanvasLayer>,
    activeLayerIndex: Int,
    activeTool: DrawingTool,
    selectedColor: Color,
    brushSizeFraction: Float,
    shapeFilled: Boolean,
    showLayersButton: Boolean,
    onSelectLayer: (Int) -> Unit,
    onOpenLayersSheet: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    onOpenColorPicker: () -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onActiveLayerOpacityChange: (Float) -> Unit,
    onActiveLayerOpacityChangeFinished: () -> Unit = {},
    onToggleShapeFilled: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onOpenArtifactsSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showInlineOpacitySlider by remember { mutableStateOf(false) }
    val activeLayer = layers.getOrElse(activeLayerIndex) { layers.first() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Quick 8-Layer Strip + Layers Manager CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    layers.forEachIndexed { idx, layer ->
                        val isSelected = idx == activeLayerIndex
                        Surface(
                            onClick = { onSelectLayer(idx) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("quick_layer_pill_${idx + 1}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (layer.opacity < 0.995f)
                                        "L${layer.slotNumber} ${(layer.opacity * 100).toInt()}%"
                                    else "L${layer.slotNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !layer.isVisible -> MaterialTheme.colorScheme.outline
                                                layer.hasVisualContent -> MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                // Quick Active Layer Opacity Toggle Pill
                Surface(
                    onClick = { showInlineOpacitySlider = !showInlineOpacitySlider },
                    shape = RoundedCornerShape(12.dp),
                    color = if (showInlineOpacitySlider) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (showInlineOpacitySlider) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("toggle_active_layer_opacity_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Opacity,
                            contentDescription = "Adjust active layer opacity",
                            tint = if (showInlineOpacitySlider) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${(activeLayer.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showInlineOpacitySlider) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (showLayersButton) {
                    Surface(
                        onClick = onOpenLayersSheet,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("open_layers_sheet_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Layers",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Expandable Inline Active-Layer Opacity Slider for rapid composition adjustments
            AnimatedVisibility(visible = showInlineOpacitySlider) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "L${activeLayer.slotNumber} Opacity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = activeLayer.opacity,
                            onValueChange = onActiveLayerOpacityChange,
                            onValueChangeFinished = onActiveLayerOpacityChangeFinished,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dock_active_layer_opacity_slider")
                        )
                        Text(
                            text = "${(activeLayer.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(38.dp)
                        )
                    }
                }
            }

            // Row 2: Tool Strip + Photo Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    icon = Icons.Rounded.Brush,
                    label = "Brush",
                    selected = activeTool == DrawingTool.BRUSH,
                    tag = "tool_brush",
                    onClick = { onSelectTool(DrawingTool.BRUSH) }
                )
                ToolButton(
                    icon = Icons.Rounded.AutoFixOff,
                    label = "Eraser",
                    selected = activeTool == DrawingTool.ERASER,
                    tag = "tool_eraser",
                    onClick = { onSelectTool(DrawingTool.ERASER) }
                )
                ToolButton(
                    icon = Icons.Rounded.HorizontalRule,
                    label = "Line",
                    selected = activeTool == DrawingTool.LINE,
                    tag = "tool_line",
                    onClick = { onSelectTool(DrawingTool.LINE) }
                )
                ToolButton(
                    icon = Icons.Rounded.CropSquare,
                    label = "Rect",
                    selected = activeTool == DrawingTool.RECTANGLE,
                    tag = "tool_rect",
                    onClick = { onSelectTool(DrawingTool.RECTANGLE) }
                )
                ToolButton(
                    icon = Icons.Rounded.Circle,
                    label = "Circle",
                    selected = activeTool == DrawingTool.CIRCLE,
                    tag = "tool_circle",
                    onClick = { onSelectTool(DrawingTool.CIRCLE) }
                )
                ToolButton(
                    icon = Icons.Rounded.TextFields,
                    label = "Text",
                    selected = activeTool == DrawingTool.TEXT,
                    tag = "tool_text",
                    onClick = { onSelectTool(DrawingTool.TEXT) }
                )
                ToolButton(
                    icon = Icons.Rounded.FormatColorFill,
                    label = "Fill",
                    selected = activeTool == DrawingTool.FILL,
                    tag = "tool_fill",
                    onClick = { onSelectTool(DrawingTool.FILL) }
                )
                ToolButton(
                    icon = Icons.Rounded.AddPhotoAlternate,
                    label = "Photo",
                    selected = false,
                    tag = "tool_photo",
                    onClick = onLaunchPhotoPicker
                )
                ToolButton(
                    icon = Icons.Rounded.AutoFixHigh,
                    label = "Artifacts",
                    selected = false,
                    tag = "tool_artifacts",
                    onClick = onOpenArtifactsSheet
                )
            }

            // Row 3: Swatch Palette + Brush Size / Shape Fill Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Color Picker Trigger
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable(onClick = onOpenColorPicker)
                        .testTag("open_color_picker_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ColorLens,
                        contentDescription = "Custom color picker",
                        tint = if (selectedColor.red * 0.299f + selectedColor.green * 0.587f + selectedColor.blue * 0.114f > 0.6f)
                            Color.Black.copy(alpha = 0.7f)
                        else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Scrollable quick artist swatches
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioPalette.quickSwatches.take(8).forEachIndexed { idx, swatch ->
                        val isPicked = swatch == selectedColor
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(swatch) }
                                .testTag("swatch_$idx")
                        )
                    }
                }

                if (activeTool == DrawingTool.RECTANGLE || activeTool == DrawingTool.CIRCLE) {
                    FilterChip(
                        selected = shapeFilled,
                        onClick = onToggleShapeFilled,
                        label = {
                            Text(
                                text = if (shapeFilled) "Filled" else "Stroke",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("toggle_shape_fill_chip")
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(128.dp)
                    ) {
                        Text(
                            text = "${(brushSizeFraction * 1000).toInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp)
                        )
                        Slider(
                            value = brushSizeFraction,
                            onValueChange = onBrushSizeChange,
                            valueRange = 0.004f..0.09f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("brush_size_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .height(42.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
