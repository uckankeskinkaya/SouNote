package com.sounote.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Belgeye bağlı ses kaydı. */
class Recording(val file: File, val createdAt: Long, val durationMs: Long)

/** Ders ses kayıtları: filesDir/ink/<belge>/audio içinde .m4a dosyaları (notlarla birlikte yedeklenir, silinir). */
object AudioNotes {
    fun dir(context: Context, docId: String) = File(context.filesDir, "ink/$docId/audio")

    fun list(context: Context, docId: String): List<Recording> {
        val files = dir(context, docId).listFiles { f -> f.name.endsWith(".m4a") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            val duration = runCatching {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(f.path)
                        extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        release()
                    }
                }
            }.getOrDefault(0L)
            Recording(f, f.lastModified(), duration)
        }
    }
}

/** Mikrofon kaydı (AAC, .m4a). */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start(dir: File): File {
        dir.mkdirs()
        val name = "kayit-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".m4a"
        val f = File(dir, name)
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            f.delete()
            throw e
        }
        recorder = r
        file = f
        return f
    }

    /** Kaydı bitirir; hiç ses alınmadıysa dosya silinir ve null döner. */
    fun stop(): File? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok) {
            f?.delete()
            return null
        }
        return f
    }
}
