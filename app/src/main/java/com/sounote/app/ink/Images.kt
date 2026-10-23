package com.sounote.app.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Sayfaya eklenen resimler: içe alma (küçültüp kaydetme) ve ekranda göstermek için önbellek. */
object Images {
    private const val MAX_SIDE = 2048

    class Saved(val file: String, val width: Int, val height: Int)

    /**
     * Galeriden seçilen resmi en uzun kenarı [MAX_SIDE] olacak şekilde küçültüp [dir] içine kaydeder.
     * ImageDecoder fotoğrafın döndürme bilgisini (EXIF) de uygular.
     */
    fun import(context: Context, uri: Uri, dir: File): Saved {
        dir.mkdirs()
        var mime = ""
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            mime = info.mimeType
            val w = info.size.width
            val h = info.size.height
            val scale = MAX_SIDE.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // Saydamlık varsa (PNG, ekran görüntüsü) kayıpsız; fotoğraflar JPEG.
        val png = bitmap.hasAlpha() || mime == "image/png"
        val name = UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
        val file = File(dir, name)
        file.outputStream().use {
            val ok = if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            if (!ok) throw IOException("Resim kaydedilemedi")
        }
        return Saved(name, bitmap.width, bitmap.height)
    }

    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loading = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Önbellekteyse resmi döndürür; değilse arka planda yükler ve bitince [onLoaded] çağrılır. */
    fun get(file: File, onLoaded: () -> Unit): Bitmap? {
        val key = file.path
        cache.get(key)?.let { return it }
        if (loading.add(key)) {
            executor.execute {
                val bmp = runCatching { BitmapFactory.decodeFile(key) }.getOrNull()
                main.post {
                    loading.remove(key)
                    if (bmp != null) {
                        cache.put(key, bmp)
                        onLoaded()
                    }
                }
            }
        }
        return null
    }
}
