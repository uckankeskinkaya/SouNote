package com.sounote.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer

/** Alıştırma soru türleri. [key] asistanla konuşulan JSON'daki ad. */
enum class ExerciseType(val key: String, val label: String, val brief: String) {
    MCQ("mcq", "Çoktan seçmeli", "4 şık, tek doğru"),
    GAP("gap", "Boşluk doldurma", "cümlede ___ ile gösterilen boşluğa tek kelime ya da kısa ifade; parantez içinde fiil/ipucu verilebilir"),
    REWRITE("rewrite", "Yeniden yazma", "verilen cümleyi anlamı bozmadan istenen yapıyla yeniden yaz (ör. relative clause, conditional, passive)"),
    OPEN("open", "Açık uçlu", "kısa cevaplı soru ya da 1-2 cümle yazdıran görev"),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key.lowercase().trim() }
    }
}

/**
 * Tek alıştırma maddesi. [answer]: doğru cevap (çoktan seçmelide doğru şıkkın metni, açık uçluda örnek cevap);
 * [accept]: boşluk doldurmada kabul edilen diğer yazımlar; [given]: yeniden yazmada dönüştürülecek cümle.
 */
data class Exercise(
    val type: ExerciseType,
    val question: String,
    val options: List<String> = emptyList(),
    val answerIndex: Int = -1,
    val answer: String = "",
    val accept: List<String> = emptyList(),
    val given: String = "",
    val why: String = "",
)

/** Değerlendirme: 1 = doğru, 0.5 = kısmen, 0 = yanlış. [fix]: öğrencinin cevabının doğru hali (varsa). */
data class ExerciseVerdict(val score: Float, val feedback: String = "", val fix: String = "")

object Exercises {

    /** Asistandan alıştırma ister. [source] boşsa konu başlığına göre hazırlar. Sorular temizlenir ve denetlenir. */
    fun generate(config: AssistantConfig, topic: String, source: String, count: Int, types: Set<ExerciseType>): List<Exercise> {
        val strong = AssistantClient.heavy(config)
        val kinds = (types.ifEmpty { ExerciseType.entries.toSet() }).joinToString("\n") { "- \"${it.key}\": ${it.brief}" }
        val material = if (source.isBlank()) {
            "No course text is given: write the items from the topic title, at Level 4 (B1+, SpeakOut B1+) exam standard."
        } else {
            "Base the items ONLY on the course material below:\n<<<\n${source.take(14_000)}\n>>>"
        }
        val seen = HashSet<String>()
        val kept = ArrayList<Exercise>()
        for (round in 0 until 3) {
            val need = count - kept.size
            if (need <= 0) break
            val avoid = if (kept.isEmpty()) "" else "\nDo NOT repeat or paraphrase these existing items:\n" + kept.joinToString("\n") { "- " + it.question.take(140) }
            val prompt = """
${QuestionQuality.EXAM_STYLE}

Write ${need + 3} practice items on the topic "$topic".
$material
Item types to use (spread them evenly):
$kinds
Format rules:
- "mcq": "options" 4 options, "answer" index of the correct option (0-3).
- "gap": "q" is a full sentence with ONE gap written ____ ; a hint in brackets is allowed, e.g. "(live)". "answer" fills the gap; "accept" lists other correct spellings (contracted/full form). The gap must have one clear answer.
- "rewrite": "given" is the original sentence, "q" the instruction with the key word in capitals (e.g. "Rewrite using SO AS TO"), "answer" the correct sentence. Like real exam rewrite items: combine two sentences, change the structure, keep the meaning.
- "open": "answer" a short model answer.
- "why": ONE sentence in Turkish (the rule or the clue).$avoid
Return ONLY a JSON array, nothing else:
[{"type": "mcq", "q": "...", "options": ["...", "...", "...", "..."], "answer": 0, "why": "..."},
 {"type": "gap", "q": "She ____ (live) here since 2020.", "answer": "has lived", "accept": ["has been living"], "why": "..."},
 {"type": "rewrite", "given": "...", "q": "...", "answer": "...", "why": "..."},
 {"type": "open", "q": "...", "answer": "...", "why": "..."}]
""".trim()
            val res = runCatching { AssistantClient.chat(strong, listOf(ChatMessage("user", prompt)), temperature = 0.7) }
            val reply = res.getOrNull() ?: if (kept.isEmpty()) throw res.exceptionOrNull()!! else break
            val parsedRes = runCatching { parse(reply) }
            if (parsedRes.isFailure && round == 2 && kept.isEmpty()) throw parsedRes.exceptionOrNull()!!
            val parsed = parsedRes.getOrDefault(emptyList())
            val cleaned = parsed.mapNotNull { e ->
                if (e.type != ExerciseType.MCQ) return@mapNotNull e
                val m = QuestionQuality.clean(QuestionQuality.Mcq(e.question, e.options, e.answerIndex, e.why)) ?: return@mapNotNull null
                e.copy(question = m.q, options = m.options, answerIndex = m.answer, answer = m.options[m.answer])
            }.filter { e -> types.isEmpty() || e.type in types }
            val unique = QuestionQuality.distinctBy(cleaned, seen) { it.given + " " + it.question }
            val mcqs = unique.filter { it.type == ExerciseType.MCQ }
            val gaps = unique.filter { it.type == ExerciseType.GAP }
            val mcqOk = QuestionQuality.verify(strong, mcqs.map { QuestionQuality.Mcq(it.question, it.options, it.answerIndex, it.why) })
            val gapOk = QuestionQuality.verifyGaps(strong, gaps)
            val bad = HashSet<Exercise>().apply {
                mcqs.forEachIndexed { i, e -> if (!mcqOk[i]) add(e) }
                gaps.forEachIndexed { i, e -> if (!gapOk[i]) add(e) }
            }
            kept += unique.filter { it !in bad }.take(need)
        }
        if (kept.isEmpty()) throw IOException("Uygun alıştırma çıkarılamadı, tekrar dene")
        return kept
    }

    fun parse(reply: String): List<Exercise> {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("Asistan alıştırmayı beklenen biçimde vermedi, tekrar dene")
        val arr = JSONArray(reply.substring(start, end + 1))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = ExerciseType.of(o.optString("type")) ?: return@mapNotNull null
            val q = o.optString("q").trim()
            val why = o.optString("why").trim()
            when (type) {
                ExerciseType.MCQ -> {
                    val opts = o.optJSONArray("options") ?: return@mapNotNull null
                    val options = (0 until opts.length()).map { QuestionQuality.stripLabel(opts.optString(it)) }.filter { it.isNotEmpty() }
                    val a = o.optInt("answer", -1)
                    if (q.isEmpty() || options.size < 2 || a !in options.indices) null
                    else Exercise(type, q, options = options, answerIndex = a, answer = options[a], why = why)
                }
                else -> {
                    val answer = o.optString("answer").trim()
                    if (q.isEmpty() || answer.isEmpty()) return@mapNotNull null
                    val accept = o.optJSONArray("accept")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
                    Exercise(type, q, answer = answer, accept = accept, given = o.optString("given").trim(), why = why)
                }
            }
        }.ifEmpty { throw IOException("Alıştırma çıkarılamadı") }
    }

    /** Karşılaştırma için: küçük harf, tek boşluk, tırnak/kesme birliği, sondaki noktalama yok. */
    fun normalize(s: String): String {
        val t = Normalizer.normalize(s, Normalizer.Form.NFKC)
            .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', '!', '?', ',', ';', ':')
            .trim()
        return t
    }

    /**
     * Uygulamanın kendi kontrol edebildiği maddeler: çoktan seçmeli ve birebir tutan boşluk doldurma.
     * null = asistana sorulmalı (yeniden yazma, açık uçlu, tutmayan boşluk).
     */
    fun checkLocally(e: Exercise, choice: Int, text: String): ExerciseVerdict? = when (e.type) {
        ExerciseType.MCQ -> if (choice < 0) ExerciseVerdict(0f, "Cevap verilmedi") else ExerciseVerdict(if (choice == e.answerIndex) 1f else 0f)
        ExerciseType.GAP -> when {
            text.isBlank() -> ExerciseVerdict(0f, "Cevap verilmedi")
            (listOf(e.answer) + e.accept).any { normalize(it) == normalize(text) } -> ExerciseVerdict(1f)
            else -> null
        }
        else -> if (text.isBlank()) ExerciseVerdict(0f, "Cevap verilmedi") else null
    }

    /** Asistan, öğrencinin yazdığı cevapları değerlendirir; sırası [items] ile aynı. */
    fun grade(config: AssistantConfig, items: List<Pair<Exercise, String>>): List<ExerciseVerdict> {
        if (items.isEmpty()) return emptyList()
        val list = items.mapIndexed { i, (e, user) ->
            JSONObject().put("id", i + 1).put("type", e.type.key).put("question", e.question)
                .apply { if (e.given.isNotBlank()) put("given", e.given) }
                .put("model_answer", e.answer).put("student_answer", user)
        }
        val prompt = """
Sen İngilizce hazırlık L4 (B1+) öğretmenisin. Öğrencinin alıştırma cevaplarını değerlendir.
Kurallar:
- Örnek cevap tek doğru değildir: dilbilgisi doğru, anlamı koruyan ve yönergeye uyan başka cevaplar da doğrudur.
- Küçük yazım hatası anlamı bozmuyorsa "score": 0.5; tamamen doğruysa 1; yanlışsa 0.
- "feedback": Türkçe, tek kısa cümle (neden doğru/yanlış). "fix": yanlış ya da kısmen doğruysa öğrencinin cevabının düzeltilmiş hali, doğruysa boş.
- SADECE JSON dizisi döndür, başka hiçbir şey yazma: [{"id": 1, "score": 1, "feedback": "...", "fix": ""}]

Cevaplar:
${JSONArray(list).toString(1)}
""".trim()
        val reply = AssistantClient.chat(AssistantClient.heavy(config), listOf(ChatMessage("user", prompt)), temperature = 0.2)
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("Değerlendirme beklenen biçimde gelmedi, tekrar dene")
        val arr = JSONArray(reply.substring(start, end + 1))
        val byId = HashMap<Int, ExerciseVerdict>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = o.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f)
            byId[o.optInt("id", i + 1)] = ExerciseVerdict(s, o.optString("feedback").trim(), o.optString("fix").trim())
        }
        return items.indices.map { byId[it + 1] ?: ExerciseVerdict(0f, "Değerlendirilemedi") }
    }
}
