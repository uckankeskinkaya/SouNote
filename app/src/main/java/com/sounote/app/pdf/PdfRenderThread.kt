package com.sounote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.sounote.app.data.LibraryRepository
import java.util.concurrent.ConcurrentHashMap

/** Çizilecek iş. [key] aynı işi iki kez istemeyi önler. */
sealed class RenderTask {
    abstract val page: Int
    abstract val key: String

    /** Hızlı kaydırmada gösterilen düşük çözünürlüklü sayfa. */
    data class Preview(override val page: Int) : RenderTask() {
        override val key get() = "p$page"
    }

    /** Sayfanın tamamı, ekran genişliğine yakın çözünürlükte. */
    data class Base(override val page: Int, val width: Int) : RenderTask() {
        override val key get() = "b${page}_$width"
    }

    /**
     * Yakınlaştırınca sayfanın sadece görünen kısmı, ekran çözünürlüğünde.
     * [region] sayfanın 0..1 oranlarıyla ifade edilen bölgesi; [pageWidthPx] sayfanın o anki ekran genişliği.
     */
    data class Detail(
        override val page: Int,
        val region: RectF,
        val pageWidthPx: Float,
        val outWidth: Int,
        val outHeight: Int,
    ) : RenderTask() {
        override val key get() = "d${page}_${pageWidthPx.toInt()}_${region.left}_${region.top}_${region.right}_${region.bottom}"
    }
}

/**
 * PdfRenderer'ı tek bir arka plan iş parçacığında tutar (PdfRenderer iş parçacığı güvenli değil).
 * [submit] bekleyen listeyi tamamen değiştirir: görünüm her değiştiğinde en güncel istek listesi işlenir.
 */
class PdfRenderThread(
    context: Context,
    uri: Uri,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpened(pageSizes: List<Size>)
        fun onRendered(task: RenderTask, bitmap: Bitmap)
        fun onFailed(error: Throwable)
    }

    private val thread = HandlerThread("sounote-pdf").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pending: List<RenderTask> = emptyList()
    private var pfd: ParcelFileDescriptor? = null
    private var pdf: PdfRenderer? = null

    @Volatile
    private var closed = false

    /** Çizilmekte olan ya da sonucu ana iş parçacığına gönderilmiş ama henüz işlenmemiş işler. */
    val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        val appContext = context.applicationContext
        handler.post {
            try {
                val fd = LibraryRepository.openFileDescriptor(appContext, uri)
                pfd = fd
                val renderer = PdfRenderer(fd)
                pdf = renderer
                if (renderer.pageCount == 0) throw IllegalStateException("PDF'te sayfa yok")
                val sizes = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { Size(it.width, it.height) }
                }
                main.post { if (!closed) listener.onOpened(sizes) }
            } catch (e: Throwable) {
                Log.e(TAG, "PDF açılamadı", e)
                main.post { if (!closed) listener.onFailed(e) }
            }
        }
    }

    fun submit(tasks: List<RenderTask>) {
        synchronized(lock) { pending = tasks }
        handler.removeCallbacks(drain)
        handler.post(drain)
    }

    fun close() {
        closed = true
        synchronized(lock) { pending = emptyList() }
        handler.post {
            runCatching { pdf?.close() }
            runCatching { pfd?.close() }
            thread.quitSafely()
        }
    }

    private val drain = Runnable {
        while (!closed) {
            val task = synchronized(lock) {
                pending.firstOrNull()?.also { pending = pending.subList(1, pending.size) }
            } ?: break
            if (!inFlight.add(task.key)) continue
            val bitmap = try {
                render(task)
            } catch (e: Throwable) {
                Log.w(TAG, "Sayfa çizilemedi: $task", e)
                null
            }
            if (bitmap == null) {
                inFlight.remove(task.key)
                continue
            }
            main.post {
                inFlight.remove(task.key)
                if (!closed) listener.onRendered(task, bitmap)
            }
        }
    }

    private fun render(task: RenderTask): Bitmap? {
        val renderer = pdf ?: return null
        renderer.openPage(task.page).use { page ->
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()
            return when (task) {
                is RenderTask.Preview -> renderWhole(page, PREVIEW_WIDTH)
                is RenderTask.Base -> renderWhole(page, task.width)
                is RenderTask.Detail -> {
                    val bitmap = Bitmap.createBitmap(task.outWidth, task.outHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    val scale = task.pageWidthPx / pageW
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-task.region.left * pageW * scale, -task.region.top * pageH * scale)
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    private fun renderWhole(page: PdfRenderer.Page, requestedWidth: Int): Bitmap {
        var width = requestedWidth
        var height = (width.toFloat() * page.height / page.width).toInt()
        // Çok uzun sayfalarda bellek patlamasın.
        if (height > MAX_SIDE) {
            width = (width.toFloat() * MAX_SIDE / height).toInt()
            height = MAX_SIDE
        }
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    companion object {
        private const val TAG = "PdfRenderThread"
        const val PREVIEW_WIDTH = 360
        private const val MAX_SIDE = 6000
    }
}
