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
/** [week]: dersin kaçıncı haftasının kaynağı (0 = belirtilmemiş). */
data class Resource(
    val title: String,
    val url: String,
    val kind: String,
    val week: Int = 0,
    /** Haftanın günü: 1 = Pazartesi … 5 = Cuma, 0 = kendi çalışman (self-study). */
    val day: Int = 0,
    val order: Int = 0,
    /** Word/PowerPoint'in Drive'daki PDF kopyası (tools/drive_pdf.gs üretir); yoksa sadece Drive'da açılır. */
    val pdf: String = "",
) {
    val isPdf get() = kind == "pdf"
    /** Word/PowerPoint ve PDF kopyası var: kopyası indirilir. */
    val convertible get() = (kind == "pptx" || kind == "docx") && pdf.isNotBlank()
    private val pdfDriveId: String? get() = if (convertible) driveIdOf(pdf) else null
    /** Drive paylaşım bağlantısından dosya kimliği. */
    val driveId: String? get() = driveIdOf(url)
    /** İndirilecek dosyanın Drive kimliği: Word/PPT için PDF kopyası, PDF için kendisi. */
    val downloadId: String? get() = pdfDriveId ?: driveId
}

private fun driveIdOf(link: String): String? =
    Regex("drive\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:export=download&)?id=)([A-Za-z0-9_-]{10,})")
        .find(link)?.groupValues?.get(1)

/** Haftalık programda olup Drive'da bulunmayan materyal (sadece listede görünür). */
data class WeekItem(val week: Int, val day: Int, val title: String, val kind: String, val order: Int)

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
    /** Dönemin ilk haftasının pazartesisi (epoch ms; 0 = yok). */
    val termStart: Long = 0L,
    val weekItems: List<WeekItem> = emptyList(),
) {
    /** [time] dönemin kaçıncı haftasında (1'den başlar; dönem öncesi 0). */
    fun weekOf(time: Long): Int =
        if (termStart == 0L || time < termStart) 0 else ((time - termStart) / (7L * 24 * 60 * 60 * 1000)).toInt() + 1

    /** Haftanın pazartesisi. */
    fun weekStart(week: Int): Long = termStart + (week - 1) * 7L * 24 * 60 * 60 * 1000

    fun topic(id: String): Pair<Course, Topic>? {
        for (c in courses) c.topics.find { it.id == id }?.let { return c to it }
        return null
    }

    companion object {
        val EMPTY = Catalog("", emptyList(), emptyList())
    }
}

/**
 * Herkes için ortak ders kataloğu. Uygulamayla birlikte gelir (assets/catalog.json); [REMOTE_URL] verilirse
 * oradan daha yeni hali indirilip cihazda saklanır.
 */
object CatalogRepo {
    /** Herkese açık güncel katalog adresi (boş = sadece uygulamadaki liste). */
    private const val REMOTE_URL = "https://github.com/uckankeskinkaya/SouNote/releases/download/catalog/catalog.json"
    private const val FILE = "catalog.json"

    private fun bundled(context: Context): Catalog =
        runCatching { parse(context.assets.open(FILE).bufferedReader().use { it.readText() }) }.getOrDefault(Catalog.EMPTY)

    /** İndirilmiş kopya uygulamadakinden yeniyse o, değilse uygulamadaki. */
    fun cached(context: Context): Catalog {
        val inApp = bundled(context)
        val saved = runCatching { parse(File(context.filesDir, FILE).readText()) }.getOrNull()
        return if (saved != null && saved.updated > inApp.updated) saved else inApp
    }

    fun fetch(context: Context): Catalog {
        if (REMOTE_URL.isEmpty()) return cached(context)
        val text = httpGet(REMOTE_URL)
        parse(text)
        File(context.filesDir, FILE).writeText(text)
        return cached(context)
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
                            Resource(
                                r.optString("title"), r.optString("url"), r.optString("kind", "link"),
                                r.optInt("week"), r.optInt("day"), r.optInt("order"), r.optString("pdf"),
                            )
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
        return Catalog(
            o.optString("updated"), courses, exams, o.optJSONArray("levels").strings(),
            parseTime(o.optString("termStart"), "00:00") ?: 0L,
            o.optJSONArray("weekItems").objects().map {
                WeekItem(it.optInt("week"), it.optInt("day"), it.optString("title"), it.optString("kind"), it.optInt("order"))
            },
        )
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
        val url = res.downloadId?.let { "https://drive.usercontent.google.com/download?id=$it&export=download&confirm=t" } ?: res.url
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
