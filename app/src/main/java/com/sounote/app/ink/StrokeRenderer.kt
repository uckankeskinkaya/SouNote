package com.sounote.app.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Çizgileri tuvale çizer. Tuval sayfa noktası koordinatında olmalı.
 *
 * Kalınlığı değişen kalemlerde (dolma, fırça, kaligrafi) her noktanın kalınlık katsayısı
 * [LEVELS] kademeye yuvarlanır; aynı kademedeki ardışık noktalar tek bir yuvarlak uçlu yol
 * olarak çizilir. Hızlıdır ve göze pürüzsüz görünür. PDF'e aktarma da aynı kademeleri kullanır.
 *
 * Fosforlu: MULTIPLY karışımı; altındaki yazı koyu kalır. Kurşun kalem: yarı saydam.
 * Kesik çizgi: sabit kalınlık, kesik kesik.
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
        paint.blendMode = if (tool == InkTool.HIGHLIGHTER) BlendMode.MULTIPLY else null
        if (tool == InkTool.PENCIL) paint.alpha = (PENCIL_ALPHA * 255).roundToInt()
        paint.pathEffect = if (tool == InkTool.DASHED) {
            val (on, off) = dashIntervals(width)
            DashPathEffect(floatArrayOf(on, off), 0f)
        } else {
            null
        }
        val levels = levels(tool, width, pressureSensitive, pts, n)

        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pts[0], pts[1], width * (levels?.get(0) ?: LEVELS) / LEVELS / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        if (levels == null) {
            drawRun(canvas, pts, 0, n - 1, width)
            return
        }
        var runStart = 0
        var runLevel = levels[0]
        for (i in 1 until n) {
            if (i == n - 1) {
                drawRun(canvas, pts, runStart, i, width * runLevel / LEVELS)
            } else if (levels[i] != runLevel) {
                drawRun(canvas, pts, runStart, i, width * runLevel / LEVELS)
                runStart = i
                runLevel = levels[i]
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
        const val LEVELS = 12
        const val MAX_PRESSURE_FACTOR = 1.6f
        const val PENCIL_ALPHA = 0.78f

        /** Kaligrafi ucunun açısı: bu yönde giden çizgi en ince olur (sağ üste doğru). */
        private val NIB_ANGLE = (-PI / 4).toFloat()

        /** Dolma kalem: basınç 0.5 → seçilen kalınlık; hafif basınç incelir, sert basınç kalınlaşır. */
        fun pressureFactor(p: Float) = (0.35f + 1.3f * p).coerceIn(0.35f, MAX_PRESSURE_FACTOR)

        /** Fırça: basınca dolma kalemden çok daha duyarlı. */
        private fun brushFactor(p: Float) = (0.15f + 1.9f * p).coerceIn(0.15f, 2f)

        /** Çizginin en kalın yeri / seçilen kalınlık (silgi ve sınır hesabı için). */
        fun maxFactor(tool: InkTool, pressureSensitive: Boolean) = when (tool) {
            InkTool.PEN -> if (pressureSensitive) MAX_PRESSURE_FACTOR else 1f
            InkTool.BRUSH -> 2f
            InkTool.CALLIGRAPHY -> 1.2f
            else -> 1f
        }

        /** Kesik çizginin dolu/boş uzunlukları. */
        fun dashIntervals(width: Float) = max(width * 3f, 3f) to max(width * 2.5f, 2.5f)

        /** Her noktanın kalınlık kademesi; kalınlığı sabit kalemlerde null. */
        fun levels(tool: InkTool, width: Float, pressureSensitive: Boolean, pts: FloatArray, n: Int): IntArray? {
            fun q(f: Float) = (f * LEVELS).roundToInt().coerceAtLeast(1)
            return when (tool) {
                InkTool.PEN -> if (pressureSensitive) IntArray(n) { q(pressureFactor(pts[it * 3 + 2])) } else null
                InkTool.BRUSH -> {
                    // Uçlara doğru incelir: başta ve sonda kalınlığın birkaç katı boyunca.
                    val dist = FloatArray(n)
                    for (i in 1 until n) {
                        dist[i] = dist[i - 1] + hypot(pts[i * 3] - pts[i * 3 - 3], pts[i * 3 + 1] - pts[i * 3 - 2])
                    }
                    val total = dist[n - 1]
                    val taper = max(width * 3f, 4f)
                    IntArray(n) { i ->
                        val base = if (pressureSensitive) brushFactor(pts[i * 3 + 2]) else 1f
                        val t = min(1f, min(dist[i], total - dist[i]) / taper)
                        q(base * (0.2f + 0.8f * t))
                    }
                }
                InkTool.CALLIGRAPHY -> IntArray(n) { i ->
                    // Yön: komşu noktalardan. Uç açısına paralel giderken ince, dik giderken kalın.
                    val a = max(0, i - 1)
                    val b = min(n - 1, i + 1)
                    val dx = pts[b * 3] - pts[a * 3]
                    val dy = pts[b * 3 + 1] - pts[a * 3 + 1]
                    if (a == b || (abs(dx) < 1e-4f && abs(dy) < 1e-4f)) {
                        q(0.7f)
                    } else {
                        q(0.15f + 1.05f * abs(sin(atan2(dy, dx) - NIB_ANGLE)))
                    }
                }
                else -> null
            }
        }
    }
}
