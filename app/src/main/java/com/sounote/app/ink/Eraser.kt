package com.sounote.app.ink

import kotlin.math.hypot
import kotlin.math.sqrt

enum class EraserMode { STROKE, PRECISE }

/** Silgi hesapları; tüm değerler sayfa noktası cinsinden. */
object Eraser {

    /**
     * Silginin (ax,ay)→(bx,by) hareketinin dokunduğu çizgileri tümüyle siler.
     * Değişiklik yoksa null döner.
     */
    fun eraseStrokes(
        strokes: List<Stroke>, ax: Float, ay: Float, bx: Float, by: Float, r: Float, highlighterOnly: Boolean,
    ): List<Stroke>? {
        var changed = false
        val result = strokes.filter { s ->
            val hit = (!highlighterOnly || s.tool == InkTool.HIGHLIGHTER) && strokeHit(s, ax, ay, bx, by, r)
            if (hit) changed = true
            !hit
        }
        return if (changed) result else null
    }

    /**
     * Hassas silgi: çizgilerin sadece silginin geçtiği kısmını keser, kalan parçaları ayrı çizgi yapar.
     * Silgi hareketi yarıçapın yarısı aralıklarla örneklenir ki hızlı harekette boşluk kalmasın.
     */
    fun erasePrecise(
        strokes: List<Stroke>, ax: Float, ay: Float, bx: Float, by: Float, r: Float, highlighterOnly: Boolean,
    ): List<Stroke>? {
        val dist = hypot(bx - ax, by - ay)
        val steps = (dist / (r * 0.5f)).toInt().coerceIn(0, 200) + 1
        var current = strokes
        var changed = false
        for (k in 1..steps) {
            val t = k.toFloat() / steps
            val cx = ax + (bx - ax) * t
            val cy = ay + (by - ay) * t
            val next = ArrayList<Stroke>(current.size + 2)
            for (s in current) {
                if (highlighterOnly && s.tool != InkTool.HIGHLIGHTER) {
                    next += s
                    continue
                }
                val pieces = cutCircle(s, cx, cy, r + s.maxHalfWidth * 0.5f)
                if (pieces == null) {
                    next += s
                } else {
                    changed = true
                    next += pieces
                }
            }
            current = next
        }
        return if (changed) current else null
    }

    private fun strokeHit(s: Stroke, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val reach = r + s.maxHalfWidth
        val b = s.bounds
        if (maxOf(ax, bx) < b.left - r || minOf(ax, bx) > b.right + r ||
            maxOf(ay, by) < b.top - r || minOf(ay, by) > b.bottom + r
        ) return false
        val p = s.points
        val n = s.pointCount
        if (n == 1) return distPointSeg(p[0], p[1], ax, ay, bx, by) <= reach
        for (i in 0 until n - 1) {
            if (distSegSeg(p[i * 3], p[i * 3 + 1], p[i * 3 + 3], p[i * 3 + 4], ax, ay, bx, by) <= reach) return true
        }
        return false
    }

    /** Çizgiyi daireyle keser. Daire çizgiye değmiyorsa null, değiyorsa kalan parçalar (boş olabilir). */
    private fun cutCircle(s: Stroke, cx: Float, cy: Float, r: Float): List<Stroke>? {
        val b = s.bounds
        if (cx + r < b.left || cx - r > b.right || cy + r < b.top || cy - r > b.bottom) return null
        val p = s.points
        val n = s.pointCount
        val r2 = r * r
        fun inside(i: Int): Boolean {
            val dx = p[i * 3] - cx
            val dy = p[i * 3 + 1] - cy
            return dx * dx + dy * dy <= r2
        }
        if (n == 1) return if (inside(0)) emptyList() else null

        val pieces = ArrayList<StrokeBuilder>()
        fun newPiece() = StrokeBuilder(s.tool, s.color, s.width, s.pressureSensitive, s.audioFile, s.audioMs)
        // Kural: cur != null ⇔ şu anki nokta dairenin dışında.
        var cur: StrokeBuilder? = null
        if (!inside(0)) cur = newPiece().also { it.add(p[0], p[1], p[2]) }
        var touched = false

        for (i in 0 until n - 1) {
            val ax = p[i * 3]
            val ay = p[i * 3 + 1]
            val ap = p[i * 3 + 2]
            val bx = p[i * 3 + 3]
            val by = p[i * 3 + 4]
            val bp = p[i * 3 + 5]
            val dx = bx - ax
            val dy = by - ay
            val fx = ax - cx
            val fy = ay - cy
            val a = dx * dx + dy * dy
            if (a < 1e-8f) continue
            val bq = 2f * (fx * dx + fy * dy)
            val c = fx * fx + fy * fy - r2
            val disc = bq * bq - 4f * a * c
            if (disc <= 0f) {
                cur?.add(bx, by, bp)
                continue
            }
            val sq = sqrt(disc)
            val t1 = (-bq - sq) / (2f * a)
            val t2 = (-bq + sq) / (2f * a)
            if (t2 <= 0f || t1 >= 1f) {
                cur?.add(bx, by, bp)
                continue
            }
            touched = true
            val piece = cur
            if (t1 > 0f && piece != null) {
                piece.add(ax + dx * t1, ay + dy * t1, ap + (bp - ap) * t1)
                pieces += piece
            }
            cur = null
            if (t2 < 1f) {
                cur = newPiece().also {
                    it.add(ax + dx * t2, ay + dy * t2, ap + (bp - ap) * t2)
                    it.add(bx, by, bp)
                }
            }
        }
        if (!touched) return null
        cur?.let { pieces += it }
        return pieces.filter { it.count >= 2 }.map { it.build() }
    }

    fun distPointSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 < 1e-8f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + dx * t), py - (ay + dy * t))
    }

    private fun distSegSeg(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return minOf(
            minOf(distPointSeg(ax, ay, cx, cy, dx, dy), distPointSeg(bx, by, cx, cy, dx, dy)),
            minOf(distPointSeg(cx, cy, ax, ay, bx, by), distPointSeg(dx, dy, ax, ay, bx, by)),
        )
    }

    private fun segmentsIntersect(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        fun cross(ox: Float, oy: Float, px: Float, py: Float, qx: Float, qy: Float) =
            (px - ox) * (qy - oy) - (py - oy) * (qx - ox)
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
