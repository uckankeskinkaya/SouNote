package com.sounote.app.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import kotlin.math.max

/** Metin kutularının yerleşimi ve çizimi; tuval sayfa noktası koordinatında. */
object TextBoxes {
    private val layouts = LruCache<TextBox, StaticLayout>(256)

    fun paint(box: TextBox) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = box.fontSize
        color = box.color
        typeface = if (box.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        // Yakınlaştırınca harfler kademeli değil pürüzsüz büyüsün.
        isLinearText = true
        isSubpixelText = true
    }

    fun layout(box: TextBox): StaticLayout {
        layouts.get(box)?.let { return it }
        val text = box.text
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint(box), max(1, box.width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        layouts.put(box, layout)
        return layout
    }

    /** Kutunun kapladığı alan (dokunma için biraz pay bırakılmış). */
    fun bounds(box: TextBox, out: RectF = RectF()): RectF {
        val l = layout(box)
        var w = box.fontSize
        for (i in 0 until l.lineCount) w = max(w, l.getLineWidth(i))
        out.set(box.x, box.y, box.x + w, box.y + max(l.height.toFloat(), box.fontSize * 1.2f))
        return out
    }

    /** Arka plan kutusunun yazıdan taşma payı. */
    fun backgroundPad(box: TextBox) = box.fontSize * 0.3f

    /** Dönmüş kutunun kapladığı alan (eksenlere hizalı). */
    fun visualBounds(box: TextBox, out: RectF = RectF()): RectF {
        bounds(box, out)
        if (box.rotation != 0f) Rotation.boundsOf(out, box.rotation, out)
        return out
    }

    fun draw(canvas: Canvas, box: TextBox) {
        val rotated = box.rotation != 0f
        if (rotated) {
            val c = bounds(box, RectF())
            canvas.save()
            canvas.rotate(box.rotation, c.centerX(), c.centerY())
        }
        drawUnrotated(canvas, box)
        if (rotated) canvas.restore()
    }

    private fun drawUnrotated(canvas: Canvas, box: TextBox) {
        if (box.background != 0) {
            val r = bounds(box, RectF())
            val pad = backgroundPad(box)
            r.inset(-pad, -pad)
            canvas.drawRoundRect(r, pad, pad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = box.background })
        }
        canvas.save()
        canvas.translate(box.x, box.y)
        layout(box).draw(canvas)
        canvas.restore()
    }
}
