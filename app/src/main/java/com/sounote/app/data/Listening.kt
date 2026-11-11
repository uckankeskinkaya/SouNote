package com.sounote.app.data

import android.content.Context
import java.io.File

/**
 * Listening pratiği: katalogdaki ses kaynakları filesDir/listening/ altına indirilir (internetsiz de çalar).
 * Her kaydın kaldığı yer ve dinlerken alınan not tercihlerde ("listening": pos_<anahtar>, note_<anahtar>).
 */
class ListeningStore(context: Context) {
    private val dir = File(context.filesDir, "listening")
    private val prefs = context.getSharedPreferences("listening", Context.MODE_PRIVATE)

    private fun key(res: Resource) = res.driveId ?: res.url.hashCode().toString()

    fun file(res: Resource) = File(dir, key(res) + ".audio")

    fun downloaded(res: Resource) = file(res).let { it.exists() && it.length() > 0 }

    /** İndirir (önce geçici dosyaya; yarım kalan dosya çalınmaya çalışılmaz). */
    fun download(res: Resource): File {
        dir.mkdirs()
        val target = file(res)
        val tmp = File(dir, target.name + ".part")
        CatalogRepo.download(res, tmp)
        if (!tmp.renameTo(target)) throw java.io.IOException("Ses kaydedilemedi")
        return target
    }

    fun position(res: Resource): Int = prefs.getInt("pos_" + key(res), 0)

    fun savePosition(res: Resource, ms: Int) {
        prefs.edit().putInt("pos_" + key(res), ms).apply()
    }

    fun note(res: Resource): String = prefs.getString("note_" + key(res), "").orEmpty()

    fun saveNote(res: Resource, text: String) {
        prefs.edit().putString("note_" + key(res), text).apply()
    }
}
