package com.sounote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.sounote.app.data.DocRef
import com.sounote.app.data.LibraryRepository
import com.sounote.app.ink.ContentPainter
import com.sounote.app.ink.InkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object PdfFiles {
    /** Açılamayan PDF'te istisna fırlatır (bozuk, şifreli ya da boş). */
    fun validate(file: File) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { if (it.pageCount == 0) throw IOException("PDF'te sayfa yok") }
        }
    }
}

/**
 * Kütüphane kapakları: ilk sayfanın küçük görüntüsü (üzerindeki notlarla).
 * Bellekte + cacheDir/thumbs altında diskte tutulur; PDF ya da ilk sayfanın notları değişince yenilenir.
 */
object Thumbnails {
    private const val WIDTH = 360

    private val memory = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val permits = Semaphore(2)

    private fun key(context: Context, doc: DocRef) =
        "${doc.id}_${doc.lastModified}_${InkStore(context).pageStamp(doc.id, 0)}"

    fun cached(context: Context, doc: DocRef): Bitmap? = memory.get(key(context, doc))

    suspend fun load(context: Context, doc: DocRef): Bitmap? = withContext(Dispatchers.IO) {
        val key = key(context, doc)
        memory.get(key)?.let { return@withContext it }
        val file = File(File(context.cacheDir, "thumbs").apply { mkdirs() }, "$key.jpg")
        val bitmap = (if (file.exists()) BitmapFactory.decodeFile(file.path) else null)
            ?: permits.withPermit { render(context, doc) }?.also { bmp ->
                runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
            ?: return@withContext null
        memory.put(key, bitmap)
        bitmap
    }

    private fun render(context: Context, doc: DocRef): Bitmap? = runCatching {
        LibraryRepository.openFileDescriptor(context, doc.uri).use { pfd ->
            PdfRenderer(pfd).use { pdf ->
                pdf.openPage(0).use { page ->
                    val height = (WIDTH.toFloat() * page.height / page.width).toInt().coerceIn(1, WIDTH * 4)
                    Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val store = InkStore(context)
                        val content = store.loadPage(doc.id, 0)
                        if (!content.isEmpty) {
                            val c = Canvas(bmp)
                            val s = WIDTH.toFloat() / page.width
                            c.scale(s, s)
                            ContentPainter(store.imagesDir(doc.id)).draw(c, content)
                        }
                    }
                }
            }
        }
    }.getOrNull()
}
