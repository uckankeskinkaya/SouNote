package com.sounote.app.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

/**
 * Bir sayfanın notlarını (resim, fosforlu, yazı, kalem) tuvale çizer; tuval sayfa noktası
 * koordinatında olmalı. Ekran dışı çizimler için: kütüphane kapağı, sayfa paneli, sayfayı resim
 * olarak paylaşma. Resimler diskten doğrudan okunur (arka planda kullanılmalı).
 */
class ContentPainter(private val imagesDir: File?) {
    private val strokes = StrokeRenderer()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    fun draw(canvas: Canvas, content: PageContent) {
        val dir = imagesDir
        if (dir != null) {
            for (im in content.images) {
                val bmp: Bitmap = runCatching { BitmapFactory.decodeFile(File(dir, im.file).path) }.getOrNull() ?: continue
                rect.set(im.x, im.y, im.x + im.width, im.y + im.height)
                canvas.drawBitmap(bmp, null, rect, bitmapPaint)
            }
        }
        content.strokes.forEach { if (it.tool == InkTool.HIGHLIGHTER) strokes.draw(canvas, it) }
        content.texts.forEach { TextBoxes.draw(canvas, it) }
        content.strokes.forEach { if (it.tool != InkTool.HIGHLIGHTER) strokes.draw(canvas, it) }
        // Not: ezber bantları burada kapalı (opak) çizilir.
    }
}
