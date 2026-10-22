package com.sounote.app.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt

/**
 * Çizgileri tuvale çizer. Tuval sayfa noktası koordinatında olmalı.
 *
 * Kalem: basınca göre kalınlık değişir. Her noktada ayrı kalınlık yerine kalınlık
 * [LEVELS] kademeye yuvarlanır ve aynı kademedeki ardışık noktalar tek bir yuvarlak uçlu
 * yol olarak çizilir; hızlı ve göze pürüzsüz görünür.
 *
 * Fosforlu: sabit kalınlık, MULTIPLY karışımı; altındaki yazı koyu kalır, kapanmaz.
 * Kurşun kalem: sabit kalınlık, yarı saydam.
 */
class StrokeRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun draw(canvas: Canvas, stroke: Stroke) =
        draw(canvas, stroke.tool, stroke.color, stroke.width, stroke.pressureSensitive, stroke.points, stroke.pointCount)

    fun draw(
        canvas: Canvas,
        tool: InkTool,
        color: Int,
        width: Float,
        pressureSensitive: Boolean,
        pts: FloatArray,
        n: Int,
    ) {
        if (n <= 0) return
        paint.color = color
        when (tool) {
            InkTool.HIGHLIGHTER -> {
                paint.blendMode = BlendMode.MULTIPLY
                if (n == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(pts[0], pts[1], width / 2f, paint)
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = width
                    buildPath(pts, 0, n - 1)
                    canvas.drawPath(path, paint)
                }
            }
            InkTool.PENCIL -> {
                // Tek yol, sabit kalınlık, yarı saydam: üst üste gelen yerler doğal olarak koyulaşır.
                paint.blendMode = null
                paint.alpha = (PENCIL_ALPHA * 255).roundToInt()
                if (n == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(pts[0], pts[1], width / 2f, paint)
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = width
                    buildPath(pts, 0, n - 1)
                    canvas.drawPath(path, paint)
                }
            }
            InkTool.PEN -> {
                paint.blendMode = null
                fun level(i: Int) =
                    if (pressureSensitive) (pressureFactor(pts[i * 3 + 2]) * LEVELS).roundToInt() else LEVELS
                if (n == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(pts[0], pts[1], width * level(0) / LEVELS / 2f, paint)
                    return
                }
                paint.style = Paint.Style.STROKE
                var runStart = 0
                var runLevel = level(0)
                for (i in 1 until n) {
                    if (i == n - 1) {
                        drawRun(canvas, pts, runStart, i, width * runLevel / LEVELS)
                    } else {
                        val l = level(i)
                        if (l != runLevel) {
                            drawRun(canvas, pts, runStart, i, width * runLevel / LEVELS)
                            runStart = i
                            runLevel = l
                        }
                    }
                }
            }
        }
    }

    private fun drawRun(canvas: Canvas, pts: FloatArray, from: Int, to: Int, strokeWidth: Float) {
        paint.strokeWidth = strokeWidth
        buildPath(pts, from, to)
        canvas.drawPath(path, paint)
    }

    /** Noktalar arasından orta noktalı ikinci derece eğrilerle yumuşak yol. */
    private fun buildPath(pts: FloatArray, from: Int, to: Int) {
        path.rewind()
        path.moveTo(pts[from * 3], pts[from * 3 + 1])
        for (j in from + 1 until to) {
            val x = pts[j * 3]
            val y = pts[j * 3 + 1]
            val nx = pts[(j + 1) * 3]
            val ny = pts[(j + 1) * 3 + 1]
            path.quadTo(x, y, (x + nx) / 2f, (y + ny) / 2f)
        }
        path.lineTo(pts[to * 3], pts[to * 3 + 1])
    }

    companion object {
        private const val LEVELS = 12
        const val MAX_PRESSURE_FACTOR = 1.6f
        const val PENCIL_ALPHA = 0.78f

        /** Basınç 0.5 → seçilen kalınlık; hafif basınç incelir, sert basınç kalınlaşır. */
        fun pressureFactor(p: Float) = (0.35f + 1.3f * p).coerceIn(0.35f, MAX_PRESSURE_FACTOR)
    }
}
