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

    /** A–B tekrarı: bölümün başı ve sonu (ms). İkisi de doluyken B'ye gelince A'ya döner. */
    var abA by mutableStateOf<Int?>(null)
        private set
    var abB by mutableStateOf<Int?>(null)
        private set

    /** Tekrarlar arası bekleme (ms): cümleyi tekrar etmek/yazmak için. */
    var abGap by mutableIntStateOf(0)
        private set

    /** Tekrar arası beklemede (oynatıcı duraklatıldı, süre dolunca sürer). */
    private var gapUntil = 0L

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
            clearAb()
            true
        } catch (e: Exception) {
            p.release()
            store.file(r).delete()
            false
        }
    }

    fun toggle() {
        val p = mp ?: return
        if (gapUntil != 0L) {
            // Tekrar arası beklerken duraklat: bekleme iptal
            gapUntil = 0L
            playing = false
            return
        }
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

    /** A noktası: şu anki konum. B, A'dan önceyse silinir. */
    fun markA() {
        val p = mp ?: return
        abA = p.currentPosition
        if ((abB ?: Int.MAX_VALUE) <= p.currentPosition + 500) abB = null
    }

    /** B noktası: şu anki konum (A'dan en az 1 sn sonra olmalı); hemen A'ya döner. */
    fun markB(): Boolean {
        val p = mp ?: return false
        val a = abA ?: return false
        if (p.currentPosition < a + 1_000) return false
        abB = p.currentPosition
        seekTo(a)
        return true
    }

    fun changeAbGap(ms: Int) {
        abGap = ms
    }

    fun clearAb() {
        abA = null
        abB = null
        gapUntil = 0L
    }

    /** Çalarken konumu okur (ekranlar 200 ms'de bir çağırır); A–B tekrarını ve aradaki beklemeyi yürütür. */
    fun tick() {
        val p = mp ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (gapUntil != 0L) {
            if (now >= gapUntil) {
                gapUntil = 0L
                p.start()
            }
            return
        }
        position = p.currentPosition
        val a = abA
        val b = abB
        if (a != null && b != null && p.isPlaying && p.currentPosition >= b) {
            seekTo(a)
            if (abGap > 0) {
                p.pause()
                gapUntil = now + abGap
            }
        }
    }

    /** Kaydı kapatır, kaldığı yeri saklar. */
    fun release() {
        mp?.let { p ->
            res?.let { r -> runCatching { store.savePosition(r, p.currentPosition) } }
            p.release()
        }
        mp = null
        res = null
        clearAb()
        playing = false
        position = 0
        duration = 0
    }
}
