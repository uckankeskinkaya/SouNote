package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Writing pratiği türü: hedef kelime aralığı ve sınav süresi (dakika). */
enum class EssayType(val label: String, val minWords: Int, val maxWords: Int, val minutes: Int, val brief: String) {
    OPINION_ESSAY(
        "Opinion Essay", 250, 300, 50,
        "Giriş (genel cümleler + tez cümlesi), iki gelişme paragrafı (her biri bir nedeni destekler), sonuç (tezin yeniden ifadesi + son düşünce).",
    ),
    OPINION_PARAGRAPH(
        "Opinion Paragraph", 120, 160, 25,
        "Konu cümlesi (görüş), iki-üç destekleyici neden ve örnek, sonuç cümlesi.",
    ),
}

/** Yazı değerlendirmesinde bir ölçüt: ad, puan / üst sınır, yorum. */
data class EssayCriterion(val name: String, val score: Int, val max: Int, val comment: String)

data class EssayMistake(val wrong: String, val right: String, val why: String)

/** Asistanın değerlendirmesi (100 üzerinden). */
data class EssayFeedback(
    val total: Int,
    val criteria: List<EssayCriterion>,
    val strengths: List<String>,
    val mistakes: List<EssayMistake>,
    val improved: String,
    val summary: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("total", total)
        .put("summary", summary)
        .put("criteria", JSONArray().apply { criteria.forEach { put(JSONObject().put("name", it.name).put("score", it.score).put("max", it.max).put("comment", it.comment)) } })
        .put("strengths", JSONArray(strengths))
        .put("mistakes", JSONArray().apply { mistakes.forEach { put(JSONObject().put("wrong", it.wrong).put("right", it.right).put("why", it.why)) } })
        .put("improved", improved)

    companion object {
        /** Modelin cevabından (etrafında açıklama ya da ``` olsa da) ilk {...} bloğunu okur. */
        fun parse(text: String): EssayFeedback {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw IOException("Değerlendirme beklenen biçimde gelmedi, tekrar dene")
            val o = JSONObject(text.substring(start, end + 1))
            fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
            val criteria = arr("criteria").let { a ->
                List(a.length()) { i ->
                    val c = a.getJSONObject(i)
                    EssayCriterion(c.optString("name"), c.optInt("score"), c.optInt("max", 20), c.optString("comment"))
                }
            }
            return EssayFeedback(
                total = o.optInt("total", criteria.sumOf { it.score }).coerceIn(0, 100),
                criteria = criteria,
                strengths = arr("strengths").let { a -> List(a.length()) { a.optString(it) } }.filter { it.isNotBlank() },
                mistakes = arr("mistakes").let { a ->
                    List(a.length()) { i ->
                        val m = a.getJSONObject(i)
                        EssayMistake(m.optString("wrong"), m.optString("right"), m.optString("why"))
                    }
                }.filter { it.wrong.isNotBlank() },
                improved = o.optString("improved"),
                summary = o.optString("summary"),
            )
        }
    }
}

data class Essay(
    val id: String,
    val type: EssayType,
    val topic: String,
    val text: String,
    val created: Long,
    val updated: Long,
    val seconds: Int = 0,
    val feedback: EssayFeedback? = null,
) {
    val words get() = wordCount(text)

    companion object {
        fun wordCount(t: String) = t.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
    }
}

/** filesDir/essays/<id>.json (yedeğe girer). */
class EssayStore(context: Context) {
    private val dir = File(context.filesDir, DIR).apply { mkdirs() }

    fun all(): List<Essay> = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .mapNotNull { f -> runCatching { read(JSONObject(f.readText())) }.getOrNull() }
        .sortedByDescending { it.updated }

    fun save(e: Essay) {
        val o = JSONObject().put("id", e.id).put("type", e.type.name).put("topic", e.topic).put("text", e.text)
            .put("created", e.created).put("updated", e.updated).put("seconds", e.seconds)
        e.feedback?.let { o.put("feedback", it.toJson()) }
        val tmp = File(dir, "${e.id}.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(File(dir, "${e.id}.json"))) throw IOException("Yazı kaydedilemedi")
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun read(o: JSONObject) = Essay(
        id = o.getString("id"),
        type = runCatching { EssayType.valueOf(o.optString("type")) }.getOrDefault(EssayType.OPINION_ESSAY),
        topic = o.optString("topic"),
        text = o.optString("text"),
        created = o.optLong("created"),
        updated = o.optLong("updated"),
        seconds = o.optInt("seconds"),
        feedback = o.optJSONObject("feedback")?.let { runCatching { EssayFeedback.parse(it.toString()) }.getOrNull() },
    )

    companion object {
        const val DIR = "essays"

        /** L4 düzeyine uygun örnek opinion konuları. */
        val TOPICS = listOf(
            "Some people think that university education should be free for everyone. Do you agree or disagree?",
            "Social media does more harm than good to young people. Do you agree?",
            "Online classes are as effective as face-to-face classes. What is your opinion?",
            "Students should be required to learn a foreign language at school. Do you agree?",
            "Smartphones should be banned in classrooms. What is your opinion?",
            "It is better to live in a big city than in a small town. Do you agree or disagree?",
            "Working from home is better than working in an office. What is your opinion?",
            "Artificial intelligence will make our lives better. Do you agree?",
            "Children should do household chores. Do you agree or disagree?",
            "Fast food should be taxed more heavily. What is your opinion?",
            "Travelling abroad is the best way to learn about other cultures. Do you agree?",
            "Exams are not the best way to measure students' success. What is your opinion?",
        )
    }
}
