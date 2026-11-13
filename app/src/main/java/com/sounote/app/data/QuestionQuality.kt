package com.sounote.app.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Asistanın ürettiği soruların kalitesi: sınav tarzı yönerge, şık temizliği (aynı şık, "all of the above", cevabın hep A olması),
 * tekrar eden soruları ayıklama ve ikinci bir çağrıyla denetleme (model soruyu cevapsız çözer; anahtarla tutmayan ya da
 * birden fazla cevabı olan soru atılır, eksik kalan yenisiyle tamamlanır).
 */
object QuestionQuality {

    /** Ankara Ü. YDYO L4 (B1+) Progress Test / Quiz tarzı soru yazım kuralları (modele İngilizce verilir, daha iyi uyuyor). */
    val EXAM_STYLE = """
You write questions for Ankara University School of Foreign Languages, Level 4 (CEFR B1+, SpeakOut B1+) progress tests.
Real exam items look like this:
- Cloze / sentence completion: a natural sentence (12-25 words) with ONE gap "____"; four options of the SAME category
  (e.g. four linkers: although / despite / however / therefore; four verb forms: had lived / lives / has lived / is living).
- Vocabulary in context: the target word is tested inside a meaningful sentence, distractors are real B1+ words of the same part of speech.
- Short reading: a 70-120 word paragraph followed by a question about main idea, detail, inference, reference ("it" refers to…) or word meaning.
Quality rules (all mandatory):
1. Exactly ONE option is correct; the other three must be clearly wrong for a careful student, but plausible to a careless one.
2. Never repeat an option. Never use "all of the above", "none of the above", "both A and B".
3. Options must be parallel in form and similar in length; the correct one must not stand out (not always the longest).
4. Test understanding, not trivia: avoid questions answerable without reading the sentence, avoid obvious nonsense distractors.
5. Every item tests a DIFFERENT point; do not write two items about the same rule with the same sentence pattern.
6. Natural, error-free English (except the deliberate gap). B1+ level: not childish, not C1 academic.
7. Mix difficulty: about 30% medium, 50% challenging, 20% hard (two distractors that look right until you check the grammar).
""".trim()

    private val BANNED = listOf("all of the above", "none of the above", "both a and b", "all of them", "none of them")

    data class Mcq(val q: String, val options: List<String>, val answer: Int, val why: String)

    /** "A) text", "b. text" gibi önekleri atar. */
    fun stripLabel(s: String): String = s.trim().replace(Regex("^[(\\[]?[A-Da-d][).:\\]]\\s+"), "").trim()

    private fun norm(s: String) = Exercises.normalize(stripLabel(s))

    /**
     * Şıkları temizler: tam 4 farklı şık, yasak şık yok, doğru şık geçerli. Doğru cevabın yeri karıştırılır (modeller çoğunlukla
     * A'ya koyuyor). Uygun değilse null.
     */
    fun clean(m: Mcq, random: Random = Random.Default): Mcq? {
        val q = m.q.trim()
        val opts = m.options.map { stripLabel(it) }.filter { it.isNotEmpty() }
        if (q.length < 8 || opts.size != 4 || m.answer !in opts.indices) return null
        if (opts.map { norm(it) }.toSet().size != 4) return null
        if (opts.any { o -> BANNED.any { it in o.lowercase() } }) return null
        val correct = opts[m.answer]
        val shuffled = opts.shuffled(random)
        return Mcq(q, shuffled, shuffled.indexOf(correct), m.why.trim())
    }

    /** Soru kökü neredeyse aynı olanları ayıklar ([seen]: önceki turlardan kalanlar). */
    fun <T> distinctBy(items: List<T>, seen: MutableSet<String>, stem: (T) -> String): List<T> =
        items.filter { seen.add(norm(stem(it)).replace(Regex("[^a-z0-9 ]"), "").take(120)) }

    /**
     * Denetim: model soruları cevap anahtarı olmadan çözer. Anahtarla tutan ve tek cevaplı olanların sırası true.
     * Denetim çağrısı başarısız olursa hepsi kabul edilir (soru üretimini engellemesin).
     */
    fun verify(config: AssistantConfig, items: List<Mcq>): List<Boolean> {
        if (items.isEmpty()) return emptyList()
        val list = JSONArray().apply {
            items.forEachIndexed { i, m ->
                put(JSONObject().put("id", i + 1).put("question", m.q).put("options", JSONArray(m.options.mapIndexed { k, o -> "${'A' + k}) $o" })))
            }
        }
        val prompt = """
You are a strict English teacher checking a B1+ multiple-choice test before it is given to students.
Solve every question yourself. For each one, decide which option is correct (0 = A, 1 = B, 2 = C, 3 = D).
Set "ambiguous": true if more than one option could be correct, no option is correct, the sentence has an error, or the question is unclear.
Return ONLY a JSON array, nothing else: [{"id": 1, "answer": 0, "ambiguous": false}]

Questions:
$list
""".trim()
        val reply = runCatching { AssistantClient.chat(config, listOf(ChatMessage("user", prompt)), temperature = 0.0) }.getOrNull()
            ?: return items.map { true }
        val arr = runCatching {
            val s = reply.indexOf('[')
            val e = reply.lastIndexOf(']')
            JSONArray(reply.substring(s, e + 1))
        }.getOrNull() ?: return items.map { true }
        val ok = HashMap<Int, Boolean>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1) - 1
            if (id !in items.indices) continue
            ok[id] = !o.optBoolean("ambiguous", false) && o.optInt("answer", -1) == items[id].answer
        }
        // Denetçi bir soruyu atlamışsa kabul et
        return items.indices.map { ok[it] ?: true }
    }

    /** Boşluk doldurma denetimi: model boşluğu doldurur; kabul edilen cevaplarla tutanlar true. */
    fun verifyGaps(config: AssistantConfig, items: List<Exercise>): List<Boolean> {
        if (items.isEmpty()) return emptyList()
        val list = JSONArray().apply { items.forEachIndexed { i, e -> put(JSONObject().put("id", i + 1).put("sentence", e.question)) } }
        val prompt = """
You are a strict English teacher checking a B1+ gap-fill exercise. Fill each gap (____ or ___) with the single best answer,
following any hint in brackets. Set "ambiguous": true if several clearly different answers are equally correct or the sentence is unclear.
Return ONLY a JSON array, nothing else: [{"id": 1, "answer": "...", "ambiguous": false}]

Sentences:
$list
""".trim()
        val reply = runCatching { AssistantClient.chat(config, listOf(ChatMessage("user", prompt)), temperature = 0.0) }.getOrNull()
            ?: return items.map { true }
        val arr = runCatching { JSONArray(reply.substring(reply.indexOf('['), reply.lastIndexOf(']') + 1)) }.getOrNull()
            ?: return items.map { true }
        val ok = HashMap<Int, Boolean>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1) - 1
            if (id !in items.indices) continue
            val e = items[id]
            val given = Exercises.normalize(o.optString("answer"))
            ok[id] = !o.optBoolean("ambiguous", false) && (listOf(e.answer) + e.accept).any { Exercises.normalize(it) == given }
        }
        return items.indices.map { ok[it] ?: true }
    }
}
