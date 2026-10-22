package com.sounote.app.ink

import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Dönme hesapları (derece, saat yönü; ekran/sayfa koordinatı y aşağı). */
object Rotation {
    /** (x, y) noktasını (cx, cy) etrafında döndür. */
    fun rotate(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
    }

    /** Dikdörtgeni merkezi etrafında döndürünce kapladığı alan. */
    fun boundsOf(rect: RectF, degrees: Float, out: RectF): RectF {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        val corners = arrayOf(rect.left to rect.top, rect.right to rect.top, rect.right to rect.bottom, rect.left to rect.bottom)
        corners.forEachIndexed { i, (x, y) ->
            val (rx, ry) = rotate(x, y, cx, cy, degrees)
            xs[i] = rx
            ys[i] = ry
        }
        out.set(xs.min(), ys.min(), xs.max(), ys.max())
        return out
    }

    /** Açıyı -180..180 aralığına getir. */
    fun normalize(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }
}
