package com.sounote.app.data

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Listening kaydını çalan oynatıcı. Ekranlardan bağımsızdır (VM'de tutulur): Listening ekranından çalışma kâğıdına
 * geçince ses kesilmez, belge ekranındaki küçük çubuk aynı oynatıcıyı kullanır. Konum, ekran açıkken [tick] ile güncellenir.
 */
class ListeningPlayer(private val store: ListeningStore) {
    var res by mutableStateOf<Resource?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var loop by mutableStateOf(false)
        private set

    /** Kayıt sonuna gelince (çalışma serisi için). */
    var onCompleted: (() -> Unit)? = null
    private var mp: MediaPlayer? = null

    /** Kaydı açar (zaten açıksa dokunmaz); kaldığı yerden başlar. Açılamazsa false. */
    fun load(r: Resource): Boolean {
        if (res?.url == r.url && mp != null) return true
        release()
        val p = MediaPlayer()
        return try {
            p.setDataSource(store.file(r).absolutePath)
            p.prepare()
            duration = p.duration.coerceAtLeast(0)
            val saved = store.position(r)
            // Sona çok yakınsa baştan başla
            if (saved in 1 until duration - 3_000) p.seekTo(saved)
            position = p.currentPosition
            p.setOnCompletionListener {
                playing = false
                position = duration
                store.savePosition(r, 0)
                onCompleted?.invoke()
            }
            mp = p
            res = r
            speed = 1f
            loop = false
            true
        } catch (e: Exception) {
            p.release()
            store.file(r).delete()
            false
        }
    }

    fun toggle() {
        val p = mp ?: return
        if (p.isPlaying) {
            p.pause()
            playing = false
            res?.let { store.savePosition(it, p.currentPosition) }
        } else {
            if (p.currentPosition >= duration - 200) p.seekTo(0)
            p.start()
            playing = true
        }
    }

    fun seekBy(ms: Int) {
        val p = mp ?: return
        seekTo(p.currentPosition + ms)
    }

    fun seekTo(ms: Int) {
        val p = mp ?: return
        val to = ms.coerceIn(0, duration)
        p.seekTo(to)
        position = to
    }

    /** Hızı değiştirir; cihaz desteklemiyorsa false. */
    fun changeSpeed(s: Float): Boolean {
        val p = mp ?: return false
        val wasPlaying = p.isPlaying
        val ok = runCatching { p.playbackParams = p.playbackParams.setSpeed(s) }.isSuccess
        if (ok) speed = s
        // Hız ayarı duraklamışken çalmayı başlatabiliyor
        if (!wasPlaying && p.isPlaying) p.pause()
        return ok
    }

    fun toggleLoop() {
        loop = !loop
        mp?.isLooping = loop
    }

    /** Çalarken konumu okur (ekranlar 200 ms'de bir çağırır). */
    fun tick() {
        mp?.let { position = it.currentPosition }
    }

    /** Kaydı kapatır, kaldığı yeri saklar. */
    fun release() {
        mp?.let { p ->
            res?.let { r -> runCatching { store.savePosition(r, p.currentPosition) } }
            p.release()
        }
        mp = null
        res = null
        playing = false
        position = 0
        duration = 0
    }
}
