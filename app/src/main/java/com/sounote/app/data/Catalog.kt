package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** Ders kaynağı: Drive dosyası, PDF bağlantısı ya da web sayfası. */
data class Resource(val title: String, val url: String, val kind: String) {
    val isPdf get() = kind == "pdf"
    /** Drive paylaşım bağlantısından dosya kimliği. */
    val driveId: String?
        get() = Regex("drive\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:export=download&)?id=)([A-Za-z0-9_-]{10,})")
            .find(url)?.groupValues?.get(1)
}

data class Topic(val id: String, val name: String, val resources: List<Resource>)

data class Course(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Int,
    val topics: List<Topic>,
)

/** Sınav: ortak katalogdan ([global]) ya da kullanıcının kendi eklediği. */
data class Exam(
    val id: String,
    val course: String,
    val title: String,
    /** Başlangıç zamanı (epoch ms, cihazın saat dilimi). */
    val time: Long,
    val place: String = "",
    val notes: String = "",
    /** Katalogdaki konu kimlikleri (sınava hangi konuların girdiği). */
    val topics: List<String> = emptyList(),
    val global: Boolean = false,
    /** Hangi kurlar için (boş = herkes). */
    val levels: List<String> = emptyList(),
    /** exam = sınav, task = ödev/teslim, presentation = sunum. */
    val kind: String = "exam",
    /** Saat yerine gösterilecek yazı (ör. "4. ders", "10. hafta"). */
    val slot: String = "",
) {
    val kindEmoji get() = when (kind) {
        "task" -> "📝"
        "presentation" -> "🎤"
        else -> "📅"
    }
}

data class Catalog(
    val updated: String,
    val courses: List<Course>,
    val exams: List<Exam>,
    val levels: List<String> = emptyList(),
) {
    fun topic(id: String): Pair<Course, Topic>? {
        for (c in courses) c.topics.find { it.id == id }?.let { return c to it }
        return null
    }

    companion object {
        val EMPTY = Catalog("", emptyList(), emptyList())
    }
}

/**
 * Herkes için ortak ders kataloğu: GitHub'daki catalog/catalog.json okunur, son hali cihazda saklanır
 * (internet yokken de görünür).
 */
object CatalogRepo {
    private val URLS = listOf(
        "https://raw.githubusercontent.com/uckankeskinkaya/SouNote/main/catalog/catalog.json",
        "https://raw.githubusercontent.com/uckankeskinkaya/SouNote/ccr-aefb7f13-i7jc0n/catalog/catalog.json",
    )
    private const val FILE = "catalog.json"

    fun cached(context: Context): Catalog =
        runCatching { parse(File(context.filesDir, FILE).readText()) }.getOrDefault(Catalog.EMPTY)

    /** İnternetten indirir; olmazsa hata fırlatır (eski kayıt yerinde kalır). */
    fun fetch(context: Context): Catalog {
        var last: Exception? = null
        for (u in URLS) {
            try {
                val text = httpGet("$u?t=${System.currentTimeMillis() / 60_000}")
                val catalog = parse(text)
                File(context.filesDir, FILE).writeText(text)
                return catalog
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Katalog alınamadı")
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(text: String): Catalog {
        val o = JSONObject(text)
        val courses = o.optJSONArray("courses").objects().map { c ->
            Course(
                id = c.getString("id"),
                name = c.optString("name", c.getString("id")),
                emoji = c.optString("emoji"),
                color = parseColor(c.optString("color")),
                topics = c.optJSONArray("topics").objects().map { t ->
                    Topic(
                        id = t.getString("id"),
                        name = t.optString("name"),
                        resources = t.optJSONArray("resources").objects().map { r ->
                            Resource(r.optString("title"), r.optString("url"), r.optString("kind", "link"))
                        },
                    )
                },
            )
        }
        val exams = o.optJSONArray("exams").objects().mapNotNull { e ->
            val time = parseTime(e.optString("date"), e.optString("time")) ?: return@mapNotNull null
            Exam(
                id = "g:" + e.optString("id", e.optString("date") + e.optString("course")),
                course = e.optString("course"),
                title = e.optString("title"),
                time = time,
                place = e.optString("place"),
                notes = e.optString("notes"),
                topics = e.optJSONArray("topics").strings(),
                global = true,
                levels = e.optJSONArray("levels").strings(),
                kind = e.optString("kind", "exam"),
                slot = e.optString("slot"),
            )
        }
        return Catalog(o.optString("updated"), courses, exams, o.optJSONArray("levels").strings())
    }

    fun parseTime(date: String, time: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse("$date ${time.ifBlank { "09:00" }}")!!.time
    }.getOrNull()

    private fun parseColor(s: String): Int =
        runCatching { android.graphics.Color.parseColor(s) }.getOrDefault(0xFF5B7FD6.toInt())

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    /** Herkese açık Drive dosyasını ya da PDF bağlantısını indirir; PDF değilse hata. */
    fun download(res: Resource, target: File) {
        val url = res.driveId?.let { "https://drive.usercontent.google.com/download?id=$it&export=download&confirm=t" } ?: res.url
        var current = URL(url)
        repeat(5) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            try {
                when (c.responseCode) {
                    in 300..399 -> {
                        current = URL(current, c.getHeaderField("Location") ?: throw IOException("Yönlendirme yok"))
                        return@repeat
                    }
                    200 -> {
                        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                        val head = target.inputStream().use { s -> ByteArray(5).also { s.read(it) } }
                        if (String(head) != "%PDF-") {
                            target.delete()
                            throw IOException("Dosya PDF değil ya da herkese açık paylaşılmamış")
                        }
                        return
                    }
                    else -> throw IOException("HTTP ${c.responseCode}")
                }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Çok fazla yönlendirme")
    }
}

/** Kullanıcının kendi eklediği sınavlar: filesDir/exams.json (yedeğe girer). */
class ExamStore(context: Context) {
    private val file = File(context.filesDir, FILE)

    fun load(): List<Exam> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Exam(
                id = o.getString("id"),
                course = o.optString("course"),
                title = o.optString("title"),
                time = o.getLong("time"),
                place = o.optString("place"),
                notes = o.optString("notes"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(exams: List<Exam>) {
        val arr = JSONArray()
        exams.filter { !it.global }.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("course", it.course).put("title", it.title)
                    .put("time", it.time).put("place", it.place).put("notes", it.notes),
            )
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) throw IOException("Sınavlar kaydedilemedi")
    }

    companion object {
        const val FILE = "exams.json"
    }
}
