package com.sounote.app.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import com.sounote.app.ink.GuideKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Cetvel ve açıölçer: ekranın üstünde duran (sayfayla kaymayan) cam bir araç.
 * Parmakla sürüklenir, iki parmakla döndürülür. Kalem kenarına yakın başlarsa çizgi kenara oturur:
 * cetvelde iki uzun kenar, açıölçerde yay ve taban çizgisi.
 * Koordinatlar ekran pikseli.
 */
class GuideOverlay(private val density: Float) {
    var kind = GuideKind.NONE
    private var cx = 0f
    private var cy = 0f
    private var angle = 0f

    private val rulerLength get() = 560f * density
    private val rulerThickness get() = 76f * density
    private val radius get() = 170f * density
    private val snapDistance get() = 22f * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E4BF0.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1A1C2E.toInt(); strokeWidth = 1f * density }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1C2E.toInt(); textSize = 10f * density; textAlign = Paint.Align.CENTER
    }
    private val angleLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E4BF0.toInt(); textSize = 14f * density; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    /** Ekranın ortasına, yatay olarak yerleştirir. */
    fun place(width: Int, height: Int) {
        cx = width / 2f
        cy = height / 2f
        angle = 0f
    }

    private fun localX(x: Float, y: Float) = (x - cx) * cos(angle) + (y - cy) * sin(angle)
    private fun localY(x: Float, y: Float) = -(x - cx) * sin(angle) + (y - cy) * cos(angle)
    private fun worldX(lx: Float, ly: Float) = cx + lx * cos(angle) - ly * sin(angle)
    private fun worldY(lx: Float, ly: Float) = cy + lx * sin(angle) + ly * cos(angle)

    /** Parmak aracın üstünde mi (sürüklemek için). */
    fun hit(x: Float, y: Float): Boolean {
        val lx = localX(x, y)
        val ly = localY(x, y)
        return when (kind) {
            GuideKind.RULER -> abs(lx) <= rulerLength / 2 && abs(ly) <= rulerThickness / 2
            GuideKind.PROTRACTOR -> ly <= 12f * density && hypot(lx, ly) <= radius
            GuideKind.NONE -> false
        }
    }

    /** Kalem bu noktada başlarsa hangi kenara oturacak: 0 = hiçbiri. */
    fun snapMode(x: Float, y: Float): Int {
        val lx = localX(x, y)
        val ly = localY(x, y)
        val s = snapDistance
        return when (kind) {
            GuideKind.RULER -> when {
                abs(lx) > rulerLength / 2 + s -> 0
                abs(ly + rulerThickness / 2) < s -> 1
                abs(ly - rulerThickness / 2) < s -> 2
                else -> 0
            }
            GuideKind.PROTRACTOR -> when {
                abs(hypot(lx, ly) - radius) < s && ly <= s -> 3
                abs(ly) < s && abs(lx) <= radius + s -> 4
                else -> 0
            }
            GuideKind.NONE -> 0
        }
    }

    /** Noktayı seçilen kenara izdüşürür. */
    fun snap(mode: Int, x: Float, y: Float, out: FloatArray) {
        val lx = localX(x, y)
        val ly = localY(x, y)
        var sx = lx
        var sy = ly
        when (mode) {
            1 -> sy = -rulerThickness / 2
            2 -> sy = rulerThickness / 2
            3 -> {
                val d = hypot(lx, ly).coerceAtLeast(1f)
                sx = lx / d * radius
                sy = (ly / d * radius).coerceAtMost(0f)
            }
            4 -> sy = 0f
            else -> {
                out[0] = x
                out[1] = y
                return
            }
        }
        out[0] = worldX(sx, sy)
        out[1] = worldY(sx, sy)
    }

    // ---- Parmakla sürükleme / döndürme ----

    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastAngle = 0f
    private var twoFinger = false

    /** Parmak olaylarını işler; araç tutuluyorsa true (sayfa kaymasın). */
    fun onTouch(event: MotionEvent): Boolean {
        if (kind == GuideKind.NONE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = hit(event.x, event.y)
                twoFinger = false
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (dragging && event.pointerCount >= 2) {
                twoFinger = true
                lastX = (event.getX(0) + event.getX(1)) / 2
                lastY = (event.getY(0) + event.getY(1)) / 2
                lastAngle = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                if (twoFinger && event.pointerCount >= 2) {
                    val mx = (event.getX(0) + event.getX(1)) / 2
                    val my = (event.getY(0) + event.getY(1)) / 2
                    val a = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                    cx += mx - lastX
                    cy += my - lastY
                    angle = snapAngle(angle + (a - lastAngle))
                    lastX = mx
                    lastY = my
                    lastAngle = a
                } else if (!twoFinger) {
                    cx += event.x - lastX
                    cy += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (dragging && twoFinger) {
                // Kalan parmakla sürüklemeye devam
                val keep = if (event.actionIndex == 0) 1 else 0
                twoFinger = false
                lastX = event.getX(keep)
                lastY = event.getY(keep)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = dragging
                dragging = false
                twoFinger = false
                return was
            }
        }
        return dragging
    }

    /** 0°, 15°, 30°, 45°… yakınında o açıya yapışır. */
    private fun snapAngle(a: Float): Float {
        val deg = Math.toDegrees(a.toDouble())
        val near = (deg / 15.0).roundToInt() * 15.0
        return if (abs(deg - near) < 1.5) Math.toRadians(near).toFloat() else a
    }

    private fun degrees(): Int {
        var d = Math.toDegrees(angle.toDouble()).roundToInt() % 180
        if (d < 0) d += 180
        return d
    }

    // ---- Çizim ----

    /** [pxPerMm]: sayfanın şu anki ölçeğinde 1 mm kaç piksel (cetvel gerçek ölçü göstersin). */
    fun draw(canvas: Canvas, pxPerMm: Float) {
        if (kind == GuideKind.NONE) return
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        when (kind) {
            GuideKind.RULER -> drawRuler(canvas, pxPerMm)
            GuideKind.PROTRACTOR -> drawProtractor(canvas)
            GuideKind.NONE -> {}
        }
        canvas.restore()
    }

    private val rect = RectF()
    private val path = Path()

    private fun drawRuler(canvas: Canvas, pxPerMm: Float) {
        val l = rulerLength / 2
        val t = rulerThickness / 2
        rect.set(-l, -t, l, t)
        val r = 10f * density
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, border)
        if (pxPerMm > 0.5f) {
            val count = ((2 * l) / pxPerMm).toInt()
            for (i in 0..count) {
                val x = -l + i * pxPerMm
                val len = when {
                    i % 10 == 0 -> 16f
                    i % 5 == 0 -> 11f
                    else -> 6f
                } * density
                canvas.drawLine(x, -t, x, -t + len, tick)
                canvas.drawLine(x, t, x, t - len, tick)
                if (i % 10 == 0 && i > 0 && i < count) canvas.drawText((i / 10).toString(), x, -t + 28f * density, label)
            }
        }
        canvas.drawText("${degrees()}°", 0f, t - 20f * density, angleLabel)
    }

    private fun drawProtractor(canvas: Canvas) {
        val r = radius
        path.rewind()
        path.moveTo(-r, 0f)
        path.arcTo(-r, -r, r, r, 180f, 180f, false)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, border)
        for (d in 0..180) {
            val a = Math.toRadians(180.0 + d).toFloat()
            val len = when {
                d % 10 == 0 -> 16f
                d % 5 == 0 -> 11f
                else -> 6f
            } * density
            val c = cos(a)
            val s = sin(a)
            canvas.drawLine(c * r, s * r, c * (r - len), s * (r - len), tick)
            if (d % 30 == 0) {
                val tr = r - 30f * density
                canvas.drawText("$d", c * tr, s * tr + 4f * density, label)
            }
        }
        canvas.drawCircle(0f, 0f, 3f * density, angleLabel)
        canvas.drawText("${degrees()}°", 0f, -r * 0.35f, angleLabel)
    }

    companion object {
        /** Sayfa noktası (1/72 inç) → mm. */
        const val PT_PER_MM = 72f / 25.4f
    }
}
