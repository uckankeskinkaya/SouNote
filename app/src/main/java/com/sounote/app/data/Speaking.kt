package com.sounote.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Speaking pratiği denemesi: konu, konuşmanın yazıya dökülmüş hali, süre ve asistanın değerlendirmesi. */
data class SpeakingAttempt(
    val id: String,
    val topic: String,
    val transcript: String,
    val seconds: Int,
    val created: Long,
    val feedback: EssayFeedback?,
) {
    val words: Int get() = EssayText.countWords(transcript)

    /** Dakikada kelime (konuşma hızı). */
    val wpm: Int get() = if (seconds < 5) 0 else words * 60 / seconds
}

/** Denemeler filesDir/speaking/<id>.json (yedeğe girer). */
class SpeakingStore(context: Context) {
    private val dir = File(context.filesDir, DIR).apply { mkdirs() }

    fun all(): List<SpeakingAttempt> = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .mapNotNull { f -> runCatching { read(JSONObject(f.readText())) }.getOrNull() }
        .sortedByDescending { it.created }

    fun save(a: SpeakingAttempt) {
        val o = JSONObject().put("id", a.id).put("topic", a.topic).put("transcript", a.transcript)
            .put("seconds", a.seconds).put("created", a.created)
        a.feedback?.let { o.put("feedback", it.toJson()) }
        val tmp = File(dir, "${a.id}.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(File(dir, "${a.id}.json"))) throw IOException("Deneme kaydedilemedi")
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun read(o: JSONObject) = SpeakingAttempt(
        id = o.getString("id"),
        topic = o.optString("topic"),
        transcript = o.optString("transcript"),
        seconds = o.optInt("seconds"),
        created = o.optLong("created"),
        feedback = o.optJSONObject("feedback")?.let { runCatching { EssayFeedback.parse(it.toString()) }.getOrNull() },
    )

    companion object {
        const val DIR = "speaking"

        /** L4 (B1+, SpeakOut) konuşma konuları. */
        val TOPICS = listOf(
            "Talk about a habit you would like to change. Why, and how will you do it?",
            "Describe a big change in your life. How did it affect you?",
            "What will universities look like in the future?",
            "Would you prefer to work from home or in an office? Why?",
            "Talk about a memorable trip you have taken.",
            "Does social media bring people closer together or push them apart?",
            "Describe a person who has had a strong influence on you.",
            "What are the advantages and disadvantages of living in a big city?",
            "If you could change one thing about your education, what would it be?",
            "Talk about a skill you would like to learn and explain why.",
            "Is it better to save money or to spend it on experiences?",
            "Describe your ideal job and what makes it ideal for you.",
        )
    }
}
