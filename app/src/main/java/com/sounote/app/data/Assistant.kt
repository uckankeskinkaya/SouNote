package com.sounote.app.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** OpenAI uyumlu sohbet servisi ayarları (DeepSeek ve benzerleri). Anahtar sadece bu cihazda saklanır. */
data class AssistantConfig(val baseUrl: String, val model: String, val apiKey: String) {
    val ready get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** Evren anahtarı (evren_llm_…) başka bir adrese girildiyse Evren adresine çevirir. */
    fun fixed(): AssistantConfig =
        if (apiKey.trim().startsWith("evren_llm_") && !baseUrl.contains("ssyz.org.tr")) {
            copy(baseUrl = EVREN_URL, model = if (model.isBlank() || model.startsWith("deepseek-chat")) EVREN_MODEL else model)
        } else this

    companion object {
        private const val PREFS = "assistant"
        const val EVREN_URL = "https://evren-llmapi.ssyz.org.tr/v1"
        const val EVREN_MODEL = "auto"
        const val DEEPSEEK_URL = "https://api.deepseek.com"
        const val DEEPSEEK_MODEL = "deepseek-chat"

        fun load(context: Context): AssistantConfig {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AssistantConfig(
                p.getString("baseUrl", null) ?: EVREN_URL,
                p.getString("model", null) ?: EVREN_MODEL,
                p.getString("apiKey", null) ?: "",
            ).fixed()
        }

        fun save(context: Context, c: AssistantConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("baseUrl", c.baseUrl.trim().trimEnd('/'))
                .putString("model", c.model.trim())
                .putString("apiKey", c.apiKey.trim())
                .apply()
        }
    }
}

/** Evren gibi servislerde ilk kullanımdan önce kullanım şartlarının onaylanması gerekir (403 terms_not_accepted). */
class TermsNotAcceptedException(message: String) : IOException(message)

data class ChatMessage(val role: String, val content: String)

object AssistantClient {
    /** /chat/completions çağrısı; cevabın metnini döndürür. Evren 429'da birkaç saniye bekletir: bir kez tekrar denenir. */
    fun chat(config: AssistantConfig, messages: List<ChatMessage>, temperature: Double = 0.4): String =
        try {
            chatOnce(config, messages, temperature)
        } catch (e: IOException) {
            if (e is TermsNotAcceptedException || e.message?.contains("HTTP 429") != true) throw e
            Thread.sleep(5_000)
            chatOnce(config, messages, temperature)
        }

    @Volatile private var bestCache: Triple<String, String, Long>? = null

    /**
     * Soru üretme ve denetleme gibi ağır işler için yapılandırma: model "auto" ise servisin en güçlü görünen modeli
     * (6 saat önbellekli). Liste alınamazsa olduğu gibi döner.
     */
    fun heavy(config: AssistantConfig): AssistantConfig {
        if (!config.model.equals("auto", ignoreCase = true)) return config
        val now = System.currentTimeMillis()
        bestCache?.let { (key, model, at) -> if (key == config.baseUrl + config.apiKey.takeLast(6) && now - at < 6 * 3_600_000L) return config.copy(model = model) }
        val best = runCatching { recommendedModel(models(config)) }.getOrNull() ?: return config
        bestCache = Triple(config.baseUrl + config.apiKey.takeLast(6), best, now)
        return config.copy(model = best)
    }

    private fun chatOnce(config: AssistantConfig, messages: List<ChatMessage>, temperature: Double): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 150_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
                if (code == 403 && text.contains("terms_not_accepted")) throw TermsNotAcceptedException(msg ?: "Kullanım şartları onaylanmamış")
                throw IOException(
                    when (code) {
                        401 -> "Anahtar yanlış ya da iptal edilmiş. Evren → API Anahtarları'ndan yeni anahtar oluşturup asistan ayarlarına yapıştır (HTTP 401)"
                        403 -> "Bu anahtarın erişim izni yok (HTTP 403)"
                        404 -> "Adres ya da model bulunamadı (HTTP 404)"
                        429 -> "Evren şu an yoğun ya da günlük kullanım sınırın doldu. Biraz bekleyip tekrar dene (HTTP 429)"
                        else -> "Servis hatası (HTTP $code)"
                    } + (msg?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                )
            }
            return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        } finally {
            c.disconnect()
        }
    }

    private fun base(config: AssistantConfig) = config.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")

    private fun request(config: AssistantConfig, path: String, body: JSONObject?): Pair<Int, String> {
        val c = URL(base(config) + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /** Servisin sunduğu modeller (GET /models), sohbet için uygun olmayanlar (embedding, rerank, ses…) çıkarılmış. */
    fun models(config: AssistantConfig): List<String> {
        val (code, text) = request(config, "/models", null)
        if (code == 401) throw IOException("Anahtar yanlış ya da iptal edilmiş (HTTP 401)")
        if (code !in 200..299) throw IOException("Model listesi alınamadı (HTTP $code)")
        val arr = JSONObject(text).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            .filter { id -> NOT_CHAT.none { it in id.lowercase() } }
            .distinct()
    }

    private val NOT_CHAT = listOf("embed", "rerank", "whisper", "tts", "speech", "audio", "guard", "moderation", "bge", "e5-", "ocr", "image")

    /**
     * Listeden asistan işleri (Türkçe sohbet, essay puanlama, JSON üretme) için en uygun görünen model: büyük, talimatla eğitilmiş,
     * genel amaçlı modeller öne; kod, küçük ve görüntü modelleri geri. Hiçbiri seçilemezse null ("auto" kalır).
     */
    fun recommendedModel(ids: List<String>): String? = ids.maxByOrNull { modelScore(it) }?.takeIf { modelScore(it) > 0 }

    fun modelScore(id: String): Double {
        val s = id.lowercase()
        if (s == "auto") return 0.0
        // Parametre sayısı: "70b", "8x7b", "120b", "a22b" gibi; MoE'de toplam sayılır
        val size = Regex("""(\d+)x(\d+(?:\.\d+)?)b""").find(s)?.let { it.groupValues[1].toDouble() * it.groupValues[2].toDouble() }
            ?: Regex("""(\d+(?:\.\d+)?)b(?![a-z])""").findAll(s).map { it.groupValues[1].toDouble() }.maxOrNull()
            ?: 20.0
        var score = kotlin.math.ln(1 + size) * 10
        if ("instruct" in s || "chat" in s || "-it" in s) score += 6
        if (listOf("qwen3", "qwen2.5", "llama-3.3", "llama3.3", "llama-4", "gpt-oss", "gemma-3", "gemma3", "deepseek", "mistral-large", "mistral-small", "kimi", "glm").any { it in s }) score += 8
        if (listOf("coder", "code", "math").any { it in s }) score -= 15
        if (listOf("vl", "vision").any { it in s }) score -= 6
        if (listOf("mini", "tiny", "nano", "small").any { it in s } && "mistral-small" !in s) score -= 8
        if ("base" in s && "instruct" !in s) score -= 10
        return score
    }

    /** Kullanım şartlarının metni ve sürümü (GET /terms/text). */
    fun termsText(config: AssistantConfig): Pair<String, Int> {
        val (code, text) = request(config, "/terms/text", null)
        if (code !in 200..299) throw IOException("Şartlar alınamadı (HTTP $code)")
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return text.trim() to 1
        val body = listOf("text", "content", "terms", "metin").firstNotNullOfOrNull { k -> json.optString(k).takeIf { it.isNotBlank() } }
            ?: json.toString(2)
        return body.trim() to json.optInt("version", 1)
    }

    /** Kullanıcı kabul ettikten sonra şartları onaylar (POST /terms/accept). */
    fun acceptTerms(config: AssistantConfig, version: Int) {
        val (code, text) = request(config, "/terms/accept", JSONObject().put("version", version))
        if (code !in 200..299) throw IOException("Onay gönderilemedi (HTTP $code): ${text.take(200)}")
    }

    /**
     * Metinden soru-cevap bilgi kartları üretir. Model JSON dizisi döndürür: [{"q": "...", "a": "..."}].
     * Cevabın etrafında açıklama ya da ``` olsa da ilk [...] bloğu okunur.
     */
    fun generateCards(config: AssistantConfig, source: String, count: Int = 10): List<Pair<String, String>> {
        val prompt = """
Aşağıdaki ders metninden en önemli bilgileri ölçen $count bilgi kartı hazırla (İngilizce hazırlık L4 öğrencisi için).
Kurallar:
- Ön yüz (q) kısa bir soru ya da boşluk doldurma; arka yüz (a) kısa ve net cevap.
- Kelime varsa: q = İngilizce kelime/ifade ve örnek cümle, a = Türkçe anlamı.
- Grammar varsa: q = kuralı sınayan cümle, a = doğru kullanım ve kısa açıklama.
- Sadece metindeki bilgiye dayan, uydurma.
- SADECE JSON dizisi döndür, başka hiçbir şey yazma: [{"q": "...", "a": "..."}]

Metin:
<<<
${source.take(12_000)}
>>>
""".trim()
        val reply = chat(config, listOf(ChatMessage("user", prompt)))
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("Asistan kartları beklenen biçimde vermedi, tekrar dene")
        val arr = JSONArray(reply.substring(start, end + 1))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val q = o.optString("q").ifBlank { o.optString("front") }.trim()
            val a = o.optString("a").ifBlank { o.optString("back") }.trim()
            if (q.isEmpty() || a.isEmpty()) null else q to a
        }.ifEmpty { throw IOException("Metinden kart çıkarılamadı") }
    }

    /**
     * Sınav öncesi çalışma planı: [days] gün için (1 = yarın) günde bir-iki kısa görev.
     * Cevap JSON: [{"day": 1, "task": "..."}]
     */
    fun studyPlan(config: AssistantConfig, exam: String, details: String, days: Int): List<Pair<Int, String>> {
        val prompt = """
İngilizce hazırlık L4 (B1+) öğrencisi için sınava kadar gerçekçi bir çalışma planı hazırla.
Sınav: $exam
Sınava $days gün var (1. gün = yarın, $days. gün = sınav günü; sınav günü sadece kısa bir tekrar).
$details
Kurallar:
- Her gün için 1 ya da 2 görev; her görev tek satır, en fazla 90 karakter, Türkçe ve somut (ne çalışılacak, nasıl).
- Konuları günlere dağıt; son iki günde tekrar ve deneme olsun.
- SADECE JSON dizisi döndür, başka hiçbir şey yazma: [{"day": 1, "task": "..."}]
""".trim()
        val reply = chat(config, listOf(ChatMessage("user", prompt)))
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) throw IOException("Asistan planı beklenen biçimde vermedi, tekrar dene")
        val arr = JSONArray(reply.substring(start, end + 1))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val d = o.optInt("day", 0)
            val t = o.optString("task").trim()
            if (d !in 1..days || t.isEmpty()) null else d to t.take(140)
        }.sortedBy { it.first }.ifEmpty { throw IOException("Plan çıkarılamadı") }
    }

    /** Deneme sınavı sorusu: 4 şık, doğru şıkkın sırası ve kısa açıklama. */
    data class Question(val q: String, val options: List<String>, val answer: Int, val why: String)

    /**
     * Deneme sınavı: metinden (yoksa konu başlıklarından) çoktan seçmeli sorular.
     * Cevap JSON: [{"q": "...", "options": ["A","B","C","D"], "answer": 0, "why": "..."}]
     */
    fun practiceTest(config: AssistantConfig, topic: String, source: String, count: Int): List<Question> {
        val strong = heavy(config)
        val material = if (source.isBlank()) {
            "No course text is given: write the questions from the topic title, at Level 4 (B1+, SpeakOut B1+) exam standard."
        } else {
            "Base the questions ONLY on the course material below (its grammar points, vocabulary and texts):\n<<<\n${source.take(14_000)}\n>>>"
        }
        val seen = HashSet<String>()
        val kept = ArrayList<QuestionQuality.Mcq>()
        // En fazla 3 tur: her turda eksik kadar (+ yedek) soru istenir, temizlenir, denetlenir
        for (round in 0 until 3) {
            val need = count - kept.size
            if (need <= 0) break
            val avoid = if (kept.isEmpty()) "" else "\nDo NOT repeat or paraphrase these existing questions:\n" + kept.joinToString("\n") { "- " + it.q.take(140) }
            val prompt = """
${QuestionQuality.EXAM_STYLE}

Write ${need + 3} multiple-choice questions on the topic "$topic".
$material
Mix item types: about half sentence-completion/cloze on the grammar points, a third vocabulary in context, the rest short reading
(put the paragraph inside "q", then the question). "why": ONE sentence in Turkish explaining why the answer is right and the main distractor wrong.$avoid
Return ONLY a JSON array, nothing else:
[{"q": "...", "options": ["...", "...", "...", "..."], "answer": 0, "why": "..."}]
""".trim()
            val res = runCatching { chat(strong, listOf(ChatMessage("user", prompt)), temperature = 0.7) }
            val reply = res.getOrNull() ?: if (kept.isEmpty()) throw res.exceptionOrNull()!! else break
            val start = reply.indexOf('[')
            val end = reply.lastIndexOf(']')
            if (start < 0 || end <= start) { if (round == 2 && kept.isEmpty()) throw IOException("Asistan soruları beklenen biçimde vermedi, tekrar dene"); continue }
            val arr = runCatching { JSONArray(reply.substring(start, end + 1)) }.getOrNull() ?: continue
            val fresh = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val opts = o.optJSONArray("options") ?: return@mapNotNull null
                QuestionQuality.clean(QuestionQuality.Mcq(o.optString("q"), (0 until opts.length()).map { opts.optString(it) }, o.optInt("answer", -1), o.optString("why")))
            }
            val unique = QuestionQuality.distinctBy(fresh, seen) { it.q }
            val verdict = QuestionQuality.verify(strong, unique)
            kept += unique.filterIndexed { i, _ -> verdict[i] }.take(need)
        }
        if (kept.isEmpty()) throw IOException("Uygun soru çıkarılamadı, tekrar dene")
        return kept.map { Question(it.q, it.options, it.answer, it.why) }
    }

    /**
     * Writing pratiği: yazıyı L4 (B1+) opinion yazısı ölçütlerine göre 100 üzerinden puanlar.
     * Cevap JSON: total, summary, criteria[{name,score,max,comment}], strengths[], mistakes[{wrong,right,why}], improved.
     */
    fun gradeEssay(config: AssistantConfig, type: EssayType, topic: String, text: String): EssayFeedback {
        val prompt = """
Sen Ankara Üniversitesi YDYO İngilizce Hazırlık L4 (B1+, SpeakOut) yazma sınavı değerlendiricisisin. Öğrencinin ${type.label} yazısını puanla.
Beklenen yapı: ${type.brief} Hedef uzunluk: ${type.minWords}-${type.maxWords} kelime.

Ölçütler (toplam 100):
1. "Görev ve içerik" (20): soruya net bir görüşle cevap, nedenler ve destekleyici örnekler/açıklamalar yeterli ve konuyla ilgili.
2. "Düzen ve paragraf yapısı" (20): giriş-tez cümlesi, konu cümleleri, gelişme, sonuç; mantıklı sıra.
3. "Bağlaçlar ve bütünlük" (20): linkers (Firstly, In addition, However, In conclusion...) doğru ve çeşitli; cümleler arası akış.
4. "Dil bilgisi" (20): zaman uyumu, cümle yapısı, article/preposition, özne-yüklem uyumu; B1+ yapılar (relative clauses, conditionals, passive...).
5. "Kelime bilgisi ve yazım" (20): uygun ve çeşitli kelime, collocations, yazım ve noktalama.
Uzunluk hedefin çok altındaysa "Görev ve içerik" puanını düşür. B1+ öğrencisine adil ol; ne çok cömert ne çok sert.

Açıklamaları Türkçe yaz; İngilizce örnekleri İngilizce bırak. En fazla 10 hata göster (en önemlileri).
"improved": öğrencinin yazısının düzeltilmiş ve biraz geliştirilmiş hali (aynı fikirler, aynı uzunluk civarı, B1+ düzeyinde; yeni fikir ekleme).
SADECE şu JSON'u döndür, başka hiçbir şey yazma:
{"total": 0, "summary": "2-3 cümle genel değerlendirme", "criteria": [{"name": "...", "score": 0, "max": 20, "comment": "..."}],
 "strengths": ["..."], "mistakes": [{"wrong": "öğrencinin yazdığı", "right": "doğrusu", "why": "kısa açıklama"}], "improved": "..."}

Konu: $topic

Öğrencinin yazısı:
<<<
${text.take(8_000)}
>>>
""".trim()
        return EssayFeedback.parse(chat(config, listOf(ChatMessage("user", prompt))))
    }

    /**
     * Speaking pratiği: konuşmanın yazıya dökülmüş hali (cihazın konuşma tanıması) L4 ölçütleriyle 100 üzerinden.
     * Telaffuz metinden ölçülemez; ölçütler akıcılık, dil bilgisi, kelime, görev.
     */
    fun gradeSpeaking(config: AssistantConfig, topic: String, transcript: String, seconds: Int, words: Int): EssayFeedback {
        val prompt = """
Sen Ankara Üniversitesi YDYO İngilizce Hazırlık L4 (B1+, SpeakOut) konuşma sınavı değerlendiricisisin.
Öğrenci aşağıdaki konuda $seconds saniye konuştu ($words kelime, dakikada ${if (seconds > 0) words * 60 / seconds else 0} kelime).
Metin cihazın konuşma tanımasıyla yazıya döküldü: noktalama ve büyük harf yok, bazı kelimeler yanlış tanınmış olabilir; bunları hata sayma.
Telaffuzu değerlendirme (metinden anlaşılmaz).

Ölçütler (toplam 100):
1. "Görev ve içerik" (25): konuya cevap, fikirler, nedenler ve örnekler; yeterli uzunluk.
2. "Akıcılık ve bütünlük" (25): konuşma hızı, fikirlerin sırası, bağlaçlar (first of all, because, however, for example...), tekrarlar.
3. "Dil bilgisi" (25): zamanlar, cümle yapısı, B1+ yapılar (conditionals, relative clauses, present perfect...).
4. "Kelime bilgisi" (25): uygun ve çeşitli kelime, collocations.
B1+ öğrencisine adil ol. Açıklamaları Türkçe yaz; İngilizce örnekleri İngilizce bırak. En fazla 8 hata göster.
"improved": aynı fikirlerle, B1+ düzeyinde, konuşma diliyle örnek bir cevap (yaklaşık aynı uzunlukta).
SADECE şu JSON'u döndür, başka hiçbir şey yazma:
{"total": 0, "summary": "2-3 cümle genel değerlendirme", "criteria": [{"name": "...", "score": 0, "max": 25, "comment": "..."}],
 "strengths": ["..."], "mistakes": [{"wrong": "öğrencinin söylediği", "right": "doğrusu", "why": "kısa açıklama"}], "improved": "..."}

Konu: $topic

Öğrencinin konuşması:
<<<
${transcript.take(8_000)}
>>>
""".trim()
        return EssayFeedback.parse(chat(config, listOf(ChatMessage("user", prompt))))
    }

    /** Speaking pratiği için yeni konu (tek cümle, İngilizce). */
    fun suggestSpeakingTopic(config: AssistantConfig): String =
        chat(
            config,
            listOf(
                ChatMessage(
                    "user",
                    "İngilizce hazırlık L4 (B1+) öğrencisi için 1-2 dakikalık konuşma pratiği konusu yaz. Sadece konuyu tek bir " +
                        "İngilizce soru ya da yönerge olarak yaz (ör. \"Talk about ...\"), başka hiçbir şey yazma.",
                ),
            ),
        ).lineSequence().map { it.trim().trim('"', '*', '-', ' ') }.firstOrNull { it.length > 10 } ?: throw IOException("Konu alınamadı")

    /** Writing pratiği için yeni bir konu önerir (tek cümle, İngilizce soru). */
    fun suggestTopic(config: AssistantConfig, type: EssayType): String =
        chat(
            config,
            listOf(
                ChatMessage(
                    "user",
                    "İngilizce hazırlık L4 (B1+) öğrencisi için tek bir ${type.label} konusu yaz. Sadece konu sorusunu İngilizce yaz " +
                        "(ör. \"... Do you agree or disagree?\"), başka hiçbir şey yazma.",
                ),
            ),
        ).lineSequence().map { it.trim().trim('"', '*', '-', ' ') }.firstOrNull { it.length > 10 } ?: throw IOException("Konu alınamadı")

    /** Asistanın uygulamayı, kullanıcıyı ve sınav takvimini tanıması için sistem talimatı. */
    fun systemPrompt(catalog: Catalog, userName: String, now: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("d MMMM yyyy EEEE HH:mm", Locale.forLanguageTag("tr"))
        val upcoming = catalog.exams.filter { it.time > now - 86_400_000L }.take(12).joinToString("\n") { e ->
            val topics = e.topics.mapNotNull { catalog.topic(it)?.second?.name }.joinToString(", ")
            "- ${fmt.format(Date(e.time))}${if (e.slot.isNotBlank()) " (${e.slot})" else ""}: ${e.title}" +
                (if (topics.isNotBlank()) " — konular: $topics" else "")
        }
        val courses = catalog.courses.joinToString("\n") { c -> "- ${c.name}: " + c.topics.joinToString(", ") { it.name } }
        return """
Sen SouNote uygulamasının içindeki yardımcı asistansın. Adın "SouNote Asistan".
Kullanıcı${if (userName.isNotBlank()) " ($userName)" else ""} Ankara Üniversitesi Yabancı Diller Yüksekokulu İngilizce hazırlık L4 (B1+, SpeakOut) öğrencisi.
Bugün: ${fmt.format(Date(now))}.

Nasıl cevap vereceksin:
- Türkçe, kısa, net ve samimi yaz. İngilizce örnekleri İngilizce ver, gerekirse Türkçesini parantezle ekle.
- Grammar sorularında kuralı, ipucunu ve 2-3 örneği ver; istenirse alıştırma sor ve cevabını kontrol et.
- Writing (opinion paragraph/essay) metni gelirse: yapı (giriş-thesis, body, conclusion), linkers, grammar ve kelime hatalarını madde madde göster, düzeltilmiş halini ver.
- Kullanıcının açık belgesinin metni verildiyse cevabı o metne dayandır; metinde olmayan şeyi uydurma, emin değilsen söyle.
- Bilmediğin bir tarih/kuralı uydurma.
- Sadece sorulana cevap ver; konu dışına çıkma, kendini tekrar etme, uzun giriş/kapanış cümleleri yazma.
- Türkçen düzgün ve doğal olsun; anlamsız, yarım ya da karışık dilli cümle kurma. Emin değilsen "emin değilim" de.
- Alıştırma/soru hazırlarken her sorunun tek doğru cevabı olsun, şıklar birbirinden farklı olsun; cevabı kendin çözüp kontrol et.

Uygulama (SouNote) hakkında bilmen gerekenler (kullanıcı nasıl yapılır diye sorarsa anlat):
- Ana sayfa: kaldığın yerden devam, hızlı başla (yeni defter, hızlı not, yazılı not, PDF içe aktar, klasör bağla), yaklaşan sınavlar.
- Kütüphane: PDF ve defterler, kendi klasörler (emoji kapak), etiketler, arama, çöp kutusu (Ayarlar'da, 30 gün).
- Dersler sekmesi: L4 kaynakları konulara ya da haftalara göre; PDF'ler "İndir" ile kütüphaneye gelir.
- Sınavlar sekmesi: L4 takvimi, geri sayım, hatırlatıcı bildirimleri, kendi sınavını ekleme.
- Belge ekranı: kalem (basınç, kalem türleri), fosforlu, silgi, yazı kutusu, lasso (seç/taşı/döndür/karta çevir), şekil aracı, damga, ezber bandı, lazer, resim ekleme,
  ses kaydı (kayıttayken yazılan nota dokununca kayıt o andan çalar), sayfa paneli (ekle/sil/taşı/çoğalt), yer imleri, arama, notlu PDF paylaş/kaydet/yazdır, gece okuma, pomodoro, bilgi kartları.
- Hareketler: kalem yazar, parmak kaydırır; iki parmakla dokun geri al, üç parmakla ileri al; kalemin yan tuşu silgi; çizgi sonunda beklet = düzgün şekil.
- Yazılı notlar: klavyeyle not, yapılacaklar listesi, çizim alanı, kalemle yaz (el yazısı yazıya döner), PDF'e/deftere çevir.
- Ayarlar: tema ve renk, parmakla çiz, uygulama kilidi, yedekleme (Drive'a yedek), asistan ayarları.

Hazırlık programı kuralları (Ankara Üniversitesi YDYO Öğrenci El Kitabı 2026-2027):
- Kur başarı notu = kur içi değerlendirme %40 + Kur Atlama Sınavı %60; kuru geçmek için en az 70.
- Kur içi: Kısa Sınav 1 %10, Ara Sınav (Progress Test) 1 %20, Kısa Sınav 2 %10, Ara Sınav 2 %20, Sözlü Performans 1 ve 2 (L4 Güz: video ödevi, sunum) %10'ar, Yazma Sınavı 1 ve 2 %10'ar.
- Kur Atlama Sınavı: Dinleme-Okuma-Dil Kullanımı %60, Yazma %20, Sözlü %20.
- L4 kurunu geçenler Güz Yeterlik Sınavına girer; en az 70 alan hazırlığı bitirir. Derslere en az %85 devam zorunlu (%15 devamsızlık hakkı).
- L4 Güz sunumu: bireysel, 8-10 dakika + 5 dakika soru-cevap; konular sunumdan iki hafta önce verilir.
- Uygulamadaki "Writing" (ana sayfa) ekranında essay yazılır, asistan 5 ölçütle (görev, düzen, bağlaçlar, dil bilgisi, kelime) 100 üzerinden puanlar.
- Uygulamadaki "Not ortalamam" (Sınavlar sekmesi / ana sayfa → Ortalama) bu hesabı yapar.

L4 ders konuları:
$courses

Yaklaşan L4 sınav ve ödevleri:
${upcoming.ifBlank { "(kayıt yok)" }}
""".trim()
    }
}

/** PDF'ten metin çıkarır (asistana bağlam olarak). Sayfalar 0'dan başlar. */
object PdfText {
    private fun load(context: Context, source: Uri): PDDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        return if (source.scheme == "file") {
            PDDocument.load(File(requireNotNull(source.path)))
        } else {
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) } ?: throw IOException("PDF açılamadı")
        }
    }

    /** Sayfanın bir bölgesindeki metin ([rect]: PDF noktası, sol üst köşe başlangıç). */
    fun region(context: Context, source: Uri, page: Int, rect: android.graphics.RectF, maxChars: Int = 8_000): String =
        load(context, source).use { doc ->
            if (page !in 0 until doc.numberOfPages) return@use ""
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripperByArea()
            stripper.addRegion("sel", android.graphics.RectF(rect).apply { inset(-2f, -2f) })
            stripper.extractRegions(doc.getPage(page))
            stripper.getTextForRegion("sel").replace(Regex("[ \\t]+"), " ").trim().take(maxChars)
        }

    fun extract(context: Context, source: Uri, from: Int, to: Int, maxChars: Int = 16_000): String {
        val doc = load(context, source)
        return doc.use {
            val stripper = PDFTextStripper().apply {
                startPage = (from + 1).coerceAtLeast(1)
                endPage = (to + 1).coerceAtMost(doc.numberOfPages)
            }
            stripper.getText(doc).replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(maxChars)
        }
    }
}
