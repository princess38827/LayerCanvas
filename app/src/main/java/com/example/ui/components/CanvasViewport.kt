package com.example.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.CanvasRenderer
import com.example.model.CanvasLayer
import com.example.model.DrawingTool
import com.example.model.LayerElement
import com.example.model.NormalizedPoint
import com.example.ui.ActiveDraftShape
import com.example.ui.ActiveDraftStroke
import kotlin.math.min

@Composable
fun CanvasViewport(
    layers: List<CanvasLayer>,
    activeLayerIndex: Int,
    activeTool: DrawingTool,
    selectedColor: Color,
    brushSizeFraction: Float,
    brushOpacity: Float,
    shapeFilled: Boolean,
    showGridOverlay: Boolean,
    draftStroke: ActiveDraftStroke?,
    draftShape: ActiveDraftShape?,
    onDragStart: (NormalizedPoint) -> Unit,
    onDragMove: (NormalizedPoint) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLoadShowcase: () -> Unit,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val activeLayer = layers.getOrElse(activeLayerIndex) { layers.first() }
    val allLayersEmpty = remember(layers) { layers.none { it.hasVisualContent } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val boardSizeDp = min(maxWidth.value, maxHeight.value).dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Compact HUD above the canvas frame
            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeLayer.isVisible) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "L${activeLayer.slotNumber} · ${activeLayer.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeLayer.isLocked) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(activeLayer.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_grid_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GridOn,
                        contentDescription = "Toggle alignment grid",
                        tint = if (showGridOverlay) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (zoomScale != 1f || panOffset != Offset.Zero) {
                    Surface(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("reset_zoom_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = stringResource(R.string.cd_fit_canvas),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${String.format("%.1f", zoomScale)}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 1:1 Interactive Canvas Frame
            val frameShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .size(boardSizeDp - 38.dp)
                    .aspectRatio(1f)
                    .clip(frameShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, frameShape)
                    .clipToBounds()
                    .testTag("drawing_canvas")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                        .pointerInput(activeTool, activeLayerIndex, activeLayer.isLocked) {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val canvasW = size.width.toFloat().coerceAtLeast(1f)
                                val canvasH = size.height.toFloat().coerceAtLeast(1f)

                                fun toNorm(pos: Offset): NormalizedPoint = NormalizedPoint(
                                    x = (pos.x / canvasW).coerceIn(0f, 1f),
                                    y = (pos.y / canvasH).coerceIn(0f, 1f)
                                )

                                var isMultiTouch = false
                                var startedDrawing = false

                                val startNorm = toNorm(firstDown.position)
                                onDragStart(startNorm)
                                startedDrawing = true
                                firstDown.consume()

                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        if (!isMultiTouch) {
                                            isMultiTouch = true
                                            if (startedDrawing) {
                                                onDragCancel()
                                                startedDrawing = false
                                            }
                                        }
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                        panOffset = if (zoomScale <= 1.01f) {
                                            Offset.Zero
                                        } else {
                                            val maxPan = (canvasW * (zoomScale - 1f)) / 2f
                                            Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxPan, maxPan),
                                                y = (panOffset.y + panChange.y).coerceIn(-maxPan, maxPan)
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (!isMultiTouch && pressedCount == 1) {
                                        val change = event.changes.firstOrNull { it.pressed }
                                        if (change != null && change.positionChanged()) {
                                            onDragMove(toNorm(change.position))
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (startedDrawing && !isMultiTouch) {
                                    onDragEnd()
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = size.width
                        drawCheckerboardBackdrop(size = size)

                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        val bounds = RectF(0f, 0f, canvasSize, canvasSize)

                        layers.forEachIndexed { idx, layer ->
                            val hasDraftOnThisLayer = idx == activeLayerIndex &&
                                (draftStroke != null || draftShape != null)
                            if (layer.isVisible && (layer.hasVisualContent || hasDraftOnThisLayer) && layer.opacity > 0f) {
                                layerPaint.reset()
                                layerPaint.isAntiAlias = true
                                layerPaint.isFilterBitmap = true
                                layerPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    layerPaint.blendMode = layer.blendMode.toAndroidBlendMode()
                                } else {
                                    layerPaint.xfermode = android.graphics.PorterDuffXfermode(layer.blendMode.porterDuffMode)
                                }

                                val saveCount = nativeCanvas.saveLayer(bounds, layerPaint)
                                CanvasRenderer.renderLayerIntoCanvas(layer, nativeCanvas, canvasSize)

                                // Render live draft element inside the active layer's saveLayer
                                if (hasDraftOnThisLayer) {
                                    val tempLayer = when {
                                        draftStroke != null -> layer.copy(
                                            backgroundFillArgb = null,
                                            elements = listOf(
                                                LayerElement.PathStroke(
                                                    points = draftStroke.points,
                                                    colorArgb = selectedColor.toArgb(),
                                                    strokeWidthFraction = brushSizeFraction,
                                                    opacity = brushOpacity,
                                                    isEraser = draftStroke.isEraser
                                                )
                                            )
                                        )
                                        draftShape != null -> layer.copy(
                                            backgroundFillArgb = null,
                                            elements = listOf(
                                                LayerElement.ShapeElement(
                                                    kind = draftShape.kind,
                                                    start = draftShape.start,
                                                    end = draftShape.end,
                                                    colorArgb = selectedColor.toArgb(),
                                                    strokeWidthFraction = brushSizeFraction,
                                                    filled = shapeFilled,
                                                    opacity = brushOpacity
                                                )
                                            )
                                        )
                                        else -> null
                                    }
                                    if (tempLayer != null) {
                                        CanvasRenderer.renderLayerIntoCanvas(tempLayer, nativeCanvas, canvasSize)
                                    }
                                }

                                nativeCanvas.restoreToCount(saveCount)
                            }
                        }

                        if (showGridOverlay) {
                            drawAlignmentGrid()
                        }
                    }
                }

                // Helpful first-launch prompt overlay when canvas has no strokes yet
                if (allLayersEmpty && draftStroke == null && draftShape == null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Draw anywhere on Layer ${activeLayer.slotNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Or load an 8-layer sample to inspect & export",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onLoadShowcase,
                                modifier = Modifier.testTag("load_showcase_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Demo")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboardBackdrop(size: Size) {
    val tile = 22f
    val light = Color(0xFF1A1F2C)
    val dark = Color(0xFF131722)
    drawRect(color = dark, size = size)
    val cols = (size.width / tile).toInt() + 1
    val rows = (size.height / tile).toInt() + 1
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if ((r + c) % 2 == 0) {
                drawRect(
                    color = light,
                    topLeft = Offset(c * tile, r * tile),
                    size = Size(tile, tile)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlignmentGrid() {
    val lineColor = Color.White.copy(alpha = 0.16f)
    val accentColor = Color(0xFF2CE6C8).copy(alpha = 0.25f)
    val step = size.width / 8f
    for (i in 1 until 8) {
        val pos = step * i
        val color = if (i == 4) accentColor else lineColor
        val width = if (i == 4) 1.5f else 1f
        drawLine(color, Offset(pos, 0f), Offset(pos, size.height), strokeWidth = width)
        drawLine(color, Offset(0f, pos), Offset(size.width, pos), strokeWidth = width)
    }
}
