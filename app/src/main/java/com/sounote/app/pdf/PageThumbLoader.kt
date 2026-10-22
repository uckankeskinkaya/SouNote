package com.sounote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.sounote.app.data.LibraryRepository
import com.sounote.app.ink.ContentPainter
import com.sounote.app.ink.PageContent
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sayfa paneli için küçük sayfa görüntüleri (notlar dahil). Kendi PdfRenderer'ı ve iş parçacığı var;
 * böylece büyük görüntüleyiciyi yavaşlatmaz.
 */
class PageThumbLoader(context: Context, private val uri: Uri, imagesDir: File?) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("sounote-thumbs").apply { start() }
    private val handler = Handler(thread.looper)
    private var pfd: ParcelFileDescriptor? = null
    private var pdf: PdfRenderer? = null
    private val painter = ContentPainter(imagesDir)

    @Volatile
    private var closed = false

    private class Entry(val content: PageContent, val bitmap: Bitmap)

    private val cache = object : LruCache<Int, Entry>(32 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Entry) = value.bitmap.byteCount
    }

    /** Notları değiştiyse yeniden çizer; aksi halde önbellekten verir. */
    fun cached(page: Int, content: PageContent): Bitmap? = cache.get(page)?.takeIf { it.content === content }?.bitmap

    suspend fun load(page: Int, content: PageContent): Bitmap? {
        cached(page, content)?.let { return it }
        return suspendCancellableCoroutine { cont ->
            handler.post {
                val bmp = if (closed || !cont.isActive) null else runCatching { render(page, content) }
                    .onFailure { Log.w("PageThumbLoader", "Sayfa küçük resmi çizilemedi: $page", it) }
                    .getOrNull()
                if (bmp != null) cache.put(page, Entry(content, bmp))
                if (cont.isActive) cont.resume(bmp)
            }
        }
    }

    private fun render(index: Int, content: PageContent): Bitmap? {
        val r = pdf ?: LibraryRepository.openFileDescriptor(appContext, uri).let { fd ->
            pfd = fd
            PdfRenderer(fd).also { pdf = it }
        }
        if (index >= r.pageCount) return null
        return r.openPage(index).use { page ->
            val height = (WIDTH.toFloat() * page.height / page.width).toInt().coerceIn(1, WIDTH * 3)
            val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (!content.isEmpty) {
                val c = Canvas(bmp)
                val s = WIDTH.toFloat() / page.width
                c.scale(s, s)
                painter.draw(c, content)
            }
            bmp
        }
    }

    fun close() {
        closed = true
        handler.post {
            runCatching { pdf?.close() }
            runCatching { pfd?.close() }
            thread.quitSafely()
        }
    }

    companion object {
        private const val WIDTH = 220
    }
}
