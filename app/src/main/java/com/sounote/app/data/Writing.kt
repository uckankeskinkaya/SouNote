package com.sounote.app.data

import org.json.JSONArray
import java.io.IOException

/**
 * Yazı stüdyosu: yazılı notların basit biçimlendirmesi ve essay yapısı.
 * Biçim düz metnin içinde Markdown işaretleriyle tutulur ("# Başlık", "**kalın**", "_italik_"):
 * kayıt biçimi değişmez, eski notlar aynen açılır.
 */
object EssayText {
    /** "Yeni essay" şablonu (yapı zorunlu değil, kullanıcı silebilir). */
    const val TEMPLATE = "## Introduction\n\n\n## Body\n\n\n## Conclusion\n\n"

    data class Section(val title: String, val text: String) {
        val words get() = countWords(text)
    }

    private val heading = Regex("^#{1,3}\\s+(.*)$")

    fun isHeading(line: String) = heading.matches(line)

    /** Başlıklara göre bölümler; ilk başlıktan önceki metin başlıksız bölüm olur. */
    fun sections(body: String): List<Section> {
        val out = ArrayList<Section>()
        var title = ""
        val cur = StringBuilder()
        for (line in body.lineSequence()) {
            if (line.startsWith(Sketches.MARKER)) continue
            val m = heading.matchEntire(line)
            if (m != null) {
                if (title.isNotEmpty() || cur.isNotBlank()) out += Section(title, cur.toString().trim())
                title = m.groupValues[1].trim()
                cur.clear()
            } else {
                cur.append(line).append('\n')
            }
        }
        if (title.isNotEmpty() || cur.isNotBlank()) out += Section(title, cur.toString().trim())
        return out
    }

    private val PARTS = listOf(
        listOf("introduction", "intro", "giriş"),
        listOf("body", "gelişme", "main"),
        listOf("conclusion", "sonuç"),
    )

    /** Giriş–gelişme–sonuç başlıklarından hangileri var (sıra: giriş, gelişme, sonuç). */
    fun structure(body: String): List<Boolean> {
        val titles = sections(body).map { it.title.lowercase() }
        return PARTS.map { names -> titles.any { t -> names.any { t.startsWith(it) } } }
    }

    fun isEssay(body: String) = structure(body).count { it } >= 2

    /** Biçim işaretleri olmadan kelime sayısı. */
    fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { w -> w.isNotBlank() && w !in setOf("☐", "☑", "•", "#", "##", "###") }

    /** PDF ve paylaşım için işaretleri kaldırır. */
    fun plain(text: String): String = text.lineSequence().joinToString("\n") { line ->
        (heading.matchEntire(line)?.groupValues?.get(1) ?: line)
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])"), "$1")
    }
}

/** Yazma koçunun bir önerisi: metindeki ifade → önerilen hali + neden. */
data class CoachSuggestion(val original: String, val suggestion: String, val reason: String)

enum class CoachMode(val label: String, private val focus: String) {
    GRAMMAR("Dil bilgisi", "dil bilgisi (grammar) hataları: zaman, özne-yüklem uyumu, article, preposition, noktalama"),
    VOCABULARY("Kelime seçimi", "kelime seçimi: tekrar eden, fazla basit ya da yanlış bağlamda kullanılan kelimeler için daha akademik/uygun alternatifler"),
    SENTENCES("Cümle yapısı", "cümle yapısı: çok uzun/kopuk cümleler, bağlaç (linker) eksikliği, cümle başlarının tekrarı"),
    ORGANIZATION("Essay yapısı", "essay organizasyonu"),
    ;

    /** Öneri listesi isteyen modlar (organizasyon serbest değerlendirme döndürür). */
    val perPhrase get() = this != ORGANIZATION

    internal fun prompt(text: String): String = if (perPhrase) {
        """
Sen bir İngilizce yazma koçusun (hazırlık okulu L4 / B1+ öğrencisi). Aşağıdaki metni SADECE şu açıdan incele: $focus.
Kurallar:
- En fazla 10 öneri ver; en önemlilerinden başla. Doğru olan şeyi değiştirme.
- "original": metinden HARFİ HARFİNE kopyalanmış kısa bir parça (bir kelime ya da cümle parçası; metinde aynen geçmeli).
- "suggestion": o parçanın önerilen hali (sadece değişen parça, açıklama değil).
- "reason": Türkçe, tek cümlelik kısa açıklama.
- SADECE JSON dizisi döndür, başka hiçbir şey yazma: [{"original": "...", "suggestion": "...", "reason": "..."}]
- Hiç öneri yoksa [] döndür.

Metin:
<<<
${text.take(10_000)}
>>>
""".trim()
    } else {
        """
Sen bir İngilizce yazma koçusun (hazırlık okulu L4 / B1+). Aşağıdaki essay'in ORGANİZASYONUNU değerlendir. Metni yeniden yazma.
Türkçe ve kısa madde madde yaz:
1. **Giriş:** hook ve thesis statement var mı, net mi?
2. **Gelişme:** her paragrafın topic sentence'ı, destekleyici örnekler, paragraflar arası bağlaçlar.
3. **Sonuç:** thesis'i yeniden ifade ediyor mu, yeni bilgi var mı?
4. **Genel:** kelime sayısı ve uzunluk dengesi, en önemli 3 iyileştirme önerisi.
Puan verme; somut ve yapıcı ol.

Metin:
<<<
${text.take(10_000)}
>>>
""".trim()
    }
}

/** Mevcut asistan servisiyle (OpenAI uyumlu) yazma koçu. Metni asla kendisi değiştirmez; öneri döndürür. */
object WritingCoach {
    fun suggestions(config: AssistantConfig, text: String, mode: CoachMode): List<CoachSuggestion> {
        val reply = AssistantClient.chat(config, listOf(ChatMessage("user", mode.prompt(text))))
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end < start) throw IOException("Koç önerileri beklenen biçimde gelmedi, tekrar dene")
        val arr = JSONArray(reply.substring(start, end + 1))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val original = o.optString("original").trim()
            val suggestion = o.optString("suggestion").trim()
            if (original.isEmpty() || suggestion.isEmpty() || original == suggestion) null
            else CoachSuggestion(original, suggestion, o.optString("reason").trim())
        }
    }

    fun review(config: AssistantConfig, text: String): String =
        AssistantClient.chat(config, listOf(ChatMessage("user", CoachMode.ORGANIZATION.prompt(text))))
}
