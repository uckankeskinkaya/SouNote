package com.sounote.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.sounote.app.ErrorLog
import com.sounote.app.ink.InkTool
import com.sounote.app.ink.StrokeRenderer

/**
 * Düşük gecikmeli çizim (deneysel): kalemle çizilmekte olan çizgi, ekranın normal çizim döngüsünü
 * beklemeden doğrudan ön tampona (front buffer) çizilir. Kalem kalkınca çizgi normal görünüme
 * geçer ve ön tampon temizlenir. Sadece opak kalemlerde kullanılır (parça parça çizilebilsin diye).
 */
class FrontInk(context: Context) {

    /** Çizilecek parça: ekran koordinatına çevirmek için sayfanın konumu ve ölçeği de içinde. */
    class Segment(
        val points: FloatArray,
        val count: Int,
        val tool: InkTool,
        val color: Int,
        val width: Float,
        val pressureSensitive: Boolean,
        val left: Float,
        val top: Float,
        val scale: Float,
        val clip: RectF,
    )

    val surface = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    // Ön tampon kendi iş parçacığında çizer: ayrı bir çizici gerekir.
    private val strokeRenderer = StrokeRenderer()

    private val callback = object : CanvasFrontBufferedRenderer.Callback<Segment> {
        override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: Segment) {
            canvas.save()
            canvas.clipRect(param.clip)
            canvas.translate(param.left, param.top)
            canvas.scale(param.scale, param.scale)
            strokeRenderer.draw(canvas, param.tool, param.color, param.width, param.pressureSensitive, param.points, param.count)
            canvas.restore()
        }

        override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<Segment>) {
            // Kullanılmıyor: biten çizgiler normal görünümde çiziliyor.
        }
    }

    private val renderer: CanvasFrontBufferedRenderer<Segment>? = runCatching {
        CanvasFrontBufferedRenderer(surface, callback)
    }.onFailure { ErrorLog.record("Düşük gecikmeli çizim başlatılamadı", it) }.getOrNull()

    val available get() = renderer?.isValid() == true

    fun draw(segment: Segment) {
        val r = renderer ?: return
        if (r.isValid()) r.renderFrontBufferedLayer(segment)
    }

    fun clear() {
        val r = renderer ?: return
        if (r.isValid()) r.clear()
    }

    fun release() {
        runCatching { renderer?.release(true) }
    }

    companion object {
        /** Parça parça çizilince görünüşü bozulmayan (opak, düz devam eden) çizgi türleri. */
        fun supports(tool: InkTool) = tool == InkTool.PEN || tool == InkTool.BRUSH || tool == InkTool.CALLIGRAPHY
    }
}

/** PDF görünümü + (açıksa) üstünde düşük gecikmeli çizim katmanı. Dokunuşları PDF görünümü alır. */
class PdfContainer(context: Context) : FrameLayout(context) {
    val pdfView = PdfDocumentView(context)
    private var front: FrontInk? = null

    init {
        addView(pdfView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Belge açıkken ekran kararmasın (okurken/yazarken).
        keepScreenOn = true
    }

    fun setLowLatency(on: Boolean) {
        if (on == (front != null)) return
        if (on) {
            val f = FrontInk(context)
            // Ön tampon yüzeyi görünüm sırasında altta (dokunuşları PDF görünümü alsın),
            // ekranda ise en üstte (setZOrderOnTop).
            addView(f.surface, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            front = f
            pdfView.frontInk = f
        } else {
            pdfView.frontInk = null
            front?.let {
                it.release()
                removeView(it.surface)
            }
            front = null
        }
    }

    fun release() {
        setLowLatency(false)
        pdfView.release()
    }
}
