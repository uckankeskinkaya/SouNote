package com.sounote.app.ink

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Çizgileri tuvale çizer. Tuval sayfa noktası koordinatında olmalı.
 *
 * Kalınlığı değişen kalemler (basınçlı dolma/jel, fırça, kaligrafi) tek parça dış hat olarak doldurulur
 * ([StrokeOutline]); PDF'e aktarma da aynı hattı kullanır. Sabit kalınlıklılar yuvarlak uçlu tek yol.
 *
 * Fosforlu: MULTIPLY karışımı; altındaki yazı koyu kalır. Kurşun kalem: yarı saydam.
 * Kesik çizgi: sabit kalınlık, kesik kesik. Noktalı: yuvarlak noktalar.
 * Neon: geniş saydam parıltı + dolu çizgi + ince açık çekirdek.
 */
class StrokeRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun draw(canvas: Canvas, stroke: Stroke) =
        draw(canvas, stroke.tool, stroke.color, stroke.width, stroke.pressureSensitive, stroke.points, stroke.pointCount)

    /** Ezber bandı: kapalıyken opak, açıkken (altı okunsun diye) çok soluk. */
    fun drawTape(canvas: Canvas, stroke: Stroke, revealed: Boolean) {
        tapeAlpha = if (revealed) TAPE_REVEALED_ALPHA else 1f
        draw(canvas, stroke)
        tapeAlpha = 1f
    }

    private var tapeAlpha = 1f

    fun draw(
        canvas: Canvas,
        tool: InkTool,
        color: Int,
        width: Float,
        pressureSensitive: Boolean,
        pts: FloatArray,
        n: Int,
        /** Uçlarda incelme (bitmiş çizgi); ön tamponda parça parça çizerken false. */
        taper: Boolean = true,
    ) {
        if (n <= 0) return
        paint.color = color
        paint.blendMode = if (tool == InkTool.HIGHLIGHTER) BlendMode.MULTIPLY else null
        if (tool == InkTool.PENCIL) paint.alpha = (PENCIL_ALPHA * 255).roundToInt()
        if (tool == InkTool.TAPE) paint.alpha = (tapeAlpha * 255).roundToInt()
        // Bant düz kesik uçlu, diğerleri yuvarlak uçlu.
        paint.strokeCap = if (tool == InkTool.TAPE) Paint.Cap.BUTT else Paint.Cap.ROUND
        paint.pathEffect = when (tool) {
            InkTool.DASHED -> {
                val (on, off) = dashIntervals(width)
                DashPathEffect(floatArrayOf(on, off), 0f)
            }
            InkTool.DOTTED -> DashPathEffect(floatArrayOf(DOT_ON, dotGap(width)), 0f)
            else -> null
        }
        if (tool == InkTool.NEON && n > 1) {
            drawNeon(canvas, color, width, pts, n)
            return
        }
        if (StrokeOutline.variable(tool, pressureSensitive)) {
            drawOutline(canvas, StrokeOutline.build(tool, width, pressureSensitive, pts, n, taper))
            return
        }
        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pts[0], pts[1], width / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        drawRun(canvas, pts, 0, n - 1, width)
    }

    /** Dış hattı orta noktalı eğrilerle yumuşatıp doldurur. */
    private fun drawOutline(canvas: Canvas, o: StrokeOutline.Outline) {
        paint.style = Paint.Style.FILL
        paint.pathEffect = null
        val v = o.poly
        val c = o.count
        if (c >= 3) {
            path.rewind()
            path.moveTo((v[(c - 1) * 2] + v[0]) / 2f, (v[(c - 1) * 2 + 1] + v[1]) / 2f)
            for (i in 0 until c) {
                val j = (i + 1) % c
                path.quadTo(v[i * 2], v[i * 2 + 1], (v[i * 2] + v[j * 2]) / 2f, (v[i * 2 + 1] + v[j * 2 + 1]) / 2f)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
        for (i in 0 until o.dotCount) canvas.drawCircle(o.dots[i * 3], o.dots[i * 3 + 1], o.dots[i * 3 + 2], paint)
    }

    /** Neon: üç kat (parıltı, renk, açık çekirdek). */
    private fun drawNeon(canvas: Canvas, color: Int, width: Float, pts: FloatArray, n: Int) {
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.alpha = (NEON_GLOW_ALPHA * 255).roundToInt()
        drawRun(canvas, pts, 0, n - 1, width * NEON_GLOW)
        paint.alpha = 255
        drawRun(canvas, pts, 0, n - 1, width)
        paint.color = neonCore(color)
        drawRun(canvas, pts, 0, n - 1, width * NEON_CORE)
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
        const val MAX_PRESSURE_FACTOR = 1.38f
        const val PENCIL_ALPHA = 0.78f
        const val TAPE_REVEALED_ALPHA = 0.16f
        const val NEON_GLOW = 2.6f
        const val NEON_GLOW_ALPHA = 0.28f
        const val NEON_CORE = 0.35f

        /** Noktalı çizgi: sıfıra yakın "dolu" parça + yuvarlak uç = nokta. */
        const val DOT_ON = 0.01f
        fun dotGap(width: Float) = max(width * 2.2f, 2f)

        /** Neon çekirdeği: rengin beyaza yakın hali. */
        fun neonCore(color: Int): Int {
            fun mix(c: Int) = (c + (255 - c) * 0.75f).roundToInt()
            return android.graphics.Color.rgb(
                mix(android.graphics.Color.red(color)),
                mix(android.graphics.Color.green(color)),
                mix(android.graphics.Color.blue(color)),
            )
        }

        /** Çizginin en kalın yeri / seçilen kalınlık (silgi ve sınır hesabı için). */
        fun maxFactor(tool: InkTool, pressureSensitive: Boolean) = when (tool) {
            InkTool.PEN -> if (pressureSensitive) MAX_PRESSURE_FACTOR else 1f
            InkTool.BRUSH -> 1.8f
            InkTool.CALLIGRAPHY -> 1.18f
            InkTool.NEON -> NEON_GLOW
            else -> 1f
        }

        /** Kesik çizginin dolu/boş uzunlukları. */
        fun dashIntervals(width: Float) = max(width * 3f, 3f) to max(width * 2.5f, 2.5f)
    }
}
