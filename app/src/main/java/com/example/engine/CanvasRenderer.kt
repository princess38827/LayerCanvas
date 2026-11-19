package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.example.model.ArtifactKind
import com.example.model.CanvasLayer
import com.example.model.LayerBlendMode
import com.example.model.LayerElement
import com.example.model.ShapeKind
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object CanvasRenderer {

    /**
     * Renders all visible layers bottom-to-top into a square ARGB_8888 Bitmap of [sizePx] x [sizePx].
     * Each layer is rendered onto an isolated offscreen buffer so per-layer Eraser strokes
     * do not erase layers beneath it, and layer opacity + blend mode are applied cleanly.
     */
    fun renderCompositeBitmap(
        layers: List<CanvasLayer>,
        sizePx: Int,
        transparentBackground: Boolean,
        backdropArgb: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val masterCanvas = Canvas(output)

        if (!transparentBackground) {
            masterCanvas.drawColor(backdropArgb)
        } else {
            masterCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val visibleLayers = layers.filter { it.isVisible && it.opacity > 0f && it.hasVisualContent }
        if (visibleLayers.isEmpty()) return output

        val layerBuffer = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layerBuffer)
        val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            for (layer in visibleLayers) {
                layerCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                renderLayerIntoCanvas(layer, layerCanvas, sizePx.toFloat())

                compositePaint.reset()
                compositePaint.isAntiAlias = true
                compositePaint.isFilterBitmap = true
                compositePaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                applyBlendMode(compositePaint, layer.blendMode)

                masterCanvas.drawBitmap(layerBuffer, 0f, 0f, compositePaint)
            }
        } finally {
            layerBuffer.recycle()
        }

        return output
    }

    fun renderLayerIntoCanvas(
        layer: CanvasLayer,
        canvas: Canvas,
        sizePx: Float
    ) {
        layer.backgroundFillArgb?.let { fillArgb ->
            canvas.drawColor(fillArgb)
        }

        for (element in layer.elements) {
            when (element) {
                is LayerElement.PathStroke -> drawPathStroke(canvas, element, sizePx)
                is LayerElement.ShapeElement -> drawShapeElement(canvas, element, sizePx)
                is LayerElement.TextElement -> drawTextElement(canvas, element, sizePx)
                is LayerElement.ImageElement -> drawImageElement(canvas, element, sizePx)
                is LayerElement.ArtifactElement -> drawArtifactElement(canvas, element, sizePx)
            }
        }
    }

    private fun drawPathStroke(
        canvas: Canvas,
        stroke: LayerElement.PathStroke,
        sizePx: Float
    ) {
        if (stroke.points.isEmpty()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (stroke.strokeWidthFraction * sizePx).coerceAtLeast(1f)
            if (stroke.isEraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                color = android.graphics.Color.TRANSPARENT
            } else {
                color = stroke.colorArgb
                alpha = (android.graphics.Color.alpha(stroke.colorArgb) * stroke.opacity.coerceIn(0f, 1f)).toInt()
            }
        }

        if (stroke.points.size == 1) {
            val pt = stroke.points[0]
            if (stroke.isEraser) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pt.x * sizePx, pt.y * sizePx, paint.strokeWidth / 2f, paint)
            } else {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pt.x * sizePx, pt.y * sizePx, paint.strokeWidth / 2f, paint)
            }
            return
        }

        val path = Path()
        val first = stroke.points.first()
        path.moveTo(first.x * sizePx, first.y * sizePx)

        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = ((prev.x + curr.x) / 2f) * sizePx
            val midY = ((prev.y + curr.y) / 2f) * sizePx
            path.quadTo(prev.x * sizePx, prev.y * sizePx, midX, midY)
        }
        val last = stroke.points.last()
        path.lineTo(last.x * sizePx, last.y * sizePx)

        canvas.drawPath(path, paint)
    }

    private fun drawShapeElement(
        canvas: Canvas,
        shape: LayerElement.ShapeElement,
        sizePx: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.colorArgb
            alpha = (android.graphics.Color.alpha(shape.colorArgb) * shape.opacity.coerceIn(0f, 1f)).toInt()
            strokeWidth = (shape.strokeWidthFraction * sizePx).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = if (shape.filled && shape.kind != ShapeKind.LINE) {
                Paint.Style.FILL
            } else {
                Paint.Style.STROKE
            }
        }

        val sx = shape.start.x * sizePx
        val sy = shape.start.y * sizePx
        val ex = shape.end.x * sizePx
        val ey = shape.end.y * sizePx

        when (shape.kind) {
            ShapeKind.LINE -> canvas.drawLine(sx, sy, ex, ey, paint)
            ShapeKind.RECTANGLE -> {
                val rect = RectF(min(sx, ex), min(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
                canvas.drawRect(rect, paint)
            }
            ShapeKind.CIRCLE -> {
                val rect = RectF(min(sx, ex), min(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
                canvas.drawOval(rect, paint)
            }
        }
    }

    private fun drawTextElement(
        canvas: Canvas,
        textEl: LayerElement.TextElement,
        sizePx: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textEl.colorArgb
            alpha = (android.graphics.Color.alpha(textEl.colorArgb) * textEl.opacity.coerceIn(0f, 1f)).toInt()
            textSize = (textEl.fontSizeFraction * sizePx).coerceAtLeast(8f)
            typeface = if (textEl.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val x = textEl.position.x * sizePx
        val y = textEl.position.y * sizePx - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(textEl.text, x, y, paint)
    }

    private fun drawImageElement(
        canvas: Canvas,
        imageEl: LayerElement.ImageElement,
        sizePx: Float
    ) {
        val bmp = imageEl.bitmap
        if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return

        val maxDim = maxOf(bmp.width, bmp.height).toFloat()
        val targetSpan = sizePx * imageEl.scale
        val scaleFactor = targetSpan / maxDim
        val drawW = bmp.width * scaleFactor
        val drawH = bmp.height * scaleFactor

        val left = (imageEl.centerX * sizePx) - (drawW / 2f)
        val top = (imageEl.centerY * sizePx) - (drawH / 2f)

        val matrix = Matrix().apply {
            postScale(scaleFactor, scaleFactor)
            postTranslate(left, top)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (imageEl.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        canvas.drawBitmap(bmp, matrix, paint)
    }

    fun drawArtifactElement(
        canvas: Canvas,
        artifact: LayerElement.ArtifactElement,
        sizePx: Float
    ) {
        val intensity = artifact.intensity.coerceIn(0.1f, 1f)
        val rng = Random(artifact.seed)
        val baseAlpha = (android.graphics.Color.alpha(artifact.colorArgb) * artifact.opacity.coerceIn(0f, 1f)).toInt()
        val rgb = artifact.colorArgb and 0x00FFFFFF

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (baseAlpha shl 24) or rgb
        }

        when (artifact.kind) {
            ArtifactKind.SCANLINES -> {
                val count = (24 + intensity * 68).toInt()
                val step = sizePx / count.toFloat()
                paint.style = Paint.Style.FILL
                paint.alpha = (baseAlpha * (0.22f + intensity * 0.45f)).toInt()
                val barHeight = (step * (0.25f + intensity * 0.35f)).coerceAtLeast(1f)
                for (i in 0 until count) {
                    val y = i * step
                    canvas.drawRect(0f, y, sizePx, y + barHeight, paint)
                }
            }

            ArtifactKind.FILM_GRAIN -> {
                val specks = (120 + intensity * 580).toInt()
                paint.style = Paint.Style.FILL
                for (i in 0 until specks) {
                    val x = rng.nextFloat() * sizePx
                    val y = rng.nextFloat() * sizePx
                    val r = (0.0014f + rng.nextFloat() * 0.0036f) * sizePx
                    paint.alpha = (baseAlpha * (0.15f + rng.nextFloat() * 0.65f)).toInt()
                    canvas.drawCircle(x, y, r, paint)
                }
            }

            ArtifactKind.GLITCH_BARS -> {
                val bars = (6 + intensity * 20).toInt()
                paint.style = Paint.Style.FILL
                for (i in 0 until bars) {
                    val y = rng.nextFloat() * sizePx * 0.94f
                    val h = (0.006f + rng.nextFloat() * 0.032f * intensity) * sizePx
                    val w = (0.12f + rng.nextFloat() * 0.68f) * sizePx
                    val x = rng.nextFloat() * (sizePx - w * 0.6f) - (w * 0.1f)
                    val tintColor = when (i % 3) {
                        1 -> 0xFF2CE6C8.toInt()
                        2 -> 0xFFFF6B7A.toInt()
                        else -> artifact.colorArgb
                    }
                    paint.color = tintColor
                    paint.alpha = (baseAlpha * (0.25f + rng.nextFloat() * 0.55f)).toInt()
                    canvas.drawRect(x, y, x + w, y + h, paint)
                }
            }

            ArtifactKind.HALFTONE -> {
                val grid = (8 + (1f - intensity) * 10).toInt().coerceIn(7, 20)
                val cell = sizePx / grid.toFloat()
                paint.style = Paint.Style.FILL
                for (row in 0 until grid) {
                    for (col in 0 until grid) {
                        val cx = (col + 0.5f) * cell
                        val cy = (row + 0.5f) * cell
                        val nx = (cx / sizePx) - 0.5f
                        val ny = (cy / sizePx) - 0.5f
                        val dist = kotlin.math.hypot(nx.toDouble(), ny.toDouble()).toFloat()
                        val factor = (1f - dist * 1.15f).coerceIn(0.18f, 1f)
                        val radius = cell * 0.36f * intensity * factor
                        paint.alpha = (baseAlpha * (0.35f + 0.55f * factor)).toInt()
                        canvas.drawCircle(cx, cy, radius.coerceAtLeast(1f), paint)
                    }
                }
            }

            ArtifactKind.VIEWFINDER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (0.0045f * sizePx).coerceAtLeast(1.5f)
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (baseAlpha * (0.45f + intensity * 0.55f)).toInt()

                val m = sizePx * 0.065f
                val arm = sizePx * (0.06f + intensity * 0.06f)
                // Four corner registration brackets
                canvas.drawLine(m, m, m + arm, m, paint)
                canvas.drawLine(m, m, m, m + arm, paint)
                canvas.drawLine(sizePx - m, m, sizePx - m - arm, m, paint)
                canvas.drawLine(sizePx - m, m, sizePx - m, m + arm, paint)
                canvas.drawLine(m, sizePx - m, m + arm, sizePx - m, paint)
                canvas.drawLine(m, sizePx - m, m, sizePx - m - arm, paint)
                canvas.drawLine(sizePx - m, sizePx - m, sizePx - m - arm, sizePx - m, paint)
                canvas.drawLine(sizePx - m, sizePx - m, sizePx - m, sizePx - m - arm, paint)

                // Center reticle
                val c = sizePx * 0.5f
                val ret = sizePx * 0.045f
                canvas.drawLine(c - ret, c, c + ret, c, paint)
                canvas.drawLine(c, c - ret, c, c + ret, paint)
                canvas.drawCircle(c, c, ret * 1.35f, paint)
            }

            ArtifactKind.BOKEH_DUST -> {
                val motes = (18 + intensity * 54).toInt()
                paint.style = Paint.Style.FILL
                for (i in 0 until motes) {
                    val x = rng.nextFloat() * sizePx
                    val y = rng.nextFloat() * sizePx
                    val r = (0.006f + rng.nextFloat() * 0.032f * intensity) * sizePx
                    paint.alpha = (baseAlpha * (0.12f + rng.nextFloat() * 0.35f)).toInt()
                    canvas.drawCircle(x, y, r, paint)
                    paint.alpha = (baseAlpha * 0.65f).toInt()
                    canvas.drawCircle(x, y, r * 0.28f, paint)
                }
            }

            ArtifactKind.HOLO_GRID -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (0.0032f * sizePx).coerceAtLeast(1.2f)
                val cx = sizePx * 0.5f
                val cy = sizePx * 0.5f
                val rings = (3 + intensity * 4).toInt()
                for (i in 1..rings) {
                    val radius = (sizePx * 0.44f) * (i.toFloat() / rings.toFloat())
                    paint.alpha = (baseAlpha * (0.28f + 0.45f * (1f - i / (rings + 1f)))).toInt()
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                // Radial ticks
                paint.alpha = (baseAlpha * 0.45f).toInt()
                val tickCount = 12
                for (t in 0 until tickCount) {
                    val angle = (t.toDouble() / tickCount) * 2.0 * Math.PI
                    val rInner = sizePx * 0.38f
                    val rOuter = sizePx * 0.46f
                    canvas.drawLine(
                        cx + (cos(angle) * rInner).toFloat(),
                        cy + (sin(angle) * rInner).toFloat(),
                        cx + (cos(angle) * rOuter).toFloat(),
                        cy + (sin(angle) * rOuter).toFloat(),
                        paint
                    )
                }
            }

            ArtifactKind.PRISM_SHARDS -> {
                val shards = (5 + intensity * 14).toInt()
                paint.style = Paint.Style.FILL
                for (i in 0 until shards) {
                    val cx = (0.1f + rng.nextFloat() * 0.8f) * sizePx
                    val cy = (0.1f + rng.nextFloat() * 0.8f) * sizePx
                    val span = (0.06f + rng.nextFloat() * 0.16f * intensity) * sizePx
                    val a1 = rng.nextDouble() * Math.PI * 2.0
                    val a2 = a1 + 1.4 + rng.nextDouble() * 1.1
                    val a3 = a2 + 1.4 + rng.nextDouble() * 1.1
                    val path = Path().apply {
                        moveTo(cx + (cos(a1) * span).toFloat(), cy + (sin(a1) * span).toFloat())
                        lineTo(cx + (cos(a2) * span).toFloat(), cy + (sin(a2) * span).toFloat())
                        lineTo(cx + (cos(a3) * span).toFloat(), cy + (sin(a3) * span).toFloat())
                        close()
                    }
                    paint.alpha = (baseAlpha * (0.14f + rng.nextFloat() * 0.32f)).toInt()
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun applyBlendMode(paint: Paint, blendMode: LayerBlendMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = blendMode.toAndroidBlendMode()
        } else {
            paint.xfermode = PorterDuffXfermode(blendMode.porterDuffMode)
        }
    }
}
