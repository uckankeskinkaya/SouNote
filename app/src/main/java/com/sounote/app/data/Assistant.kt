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
    /** /chat/completions çağrısı; cevabın metnini döndürür. */
    fun chat(config: AssistantConfig, messages: List<ChatMessage>): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.4)
            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
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
                        401, 403 -> "Anahtar geçersiz ya da yetkisiz (HTTP $code)"
                        404 -> "Adres ya da model bulunamadı (HTTP 404)"
                        429 -> "Çok fazla istek ya da kota doldu (HTTP 429)"
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

L4 ders konuları:
$courses

Yaklaşan L4 sınav ve ödevleri:
${upcoming.ifBlank { "(kayıt yok)" }}
""".trim()
    }
}

/** PDF'ten metin çıkarır (asistana bağlam olarak). Sayfalar 0'dan başlar. */
object PdfText {
    fun extract(context: Context, source: Uri, from: Int, to: Int, maxChars: Int = 16_000): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = if (source.scheme == "file") {
            PDDocument.load(File(requireNotNull(source.path)))
        } else {
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) } ?: throw IOException("PDF açılamadı")
        }
        return doc.use {
            val stripper = PDFTextStripper().apply {
                startPage = (from + 1).coerceAtLeast(1)
                endPage = (to + 1).coerceAtMost(doc.numberOfPages)
            }
            stripper.getText(doc).replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(maxChars)
        }
    }
}
