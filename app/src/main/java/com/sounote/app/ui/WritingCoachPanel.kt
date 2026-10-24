package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.CoachMode
import com.sounote.app.data.CoachSuggestion
import com.sounote.app.data.EssayText
import com.sounote.app.data.Sketches
import com.sounote.app.data.WritingCoach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Yazı stüdyosunun yan paneli. [panel] 1: yazma koçu (mevcut asistan servisiyle; öneriler sadece
 * kullanıcı "Uygula" deyince metne girer), 2: yapı (bölümler ve kelime sayıları; çevrimdışı çalışır).
 */
@Composable
internal fun WritingSidePanel(
    vm: LibraryViewModel,
    panel: Int,
    body: String,
    onApply: (CoachSuggestion) -> Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    showHeader: Boolean = true,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp)) {
                Text(
                    if (panel == 1) "🎓 Yazma koçu" else "📑 Yapı",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(SouIcons.Close, contentDescription = "Paneli kapat") }
            }
        }
        val text = remember(body) { body.lineSequence().filterNot { it.startsWith(Sketches.MARKER) }.joinToString("\n") }
        if (panel == 2) StructurePanel(text) else CoachPanel(vm, text, onApply, canUndo, onUndo)
    }
}

@Composable
private fun StructurePanel(text: String) {
    val sections = remember(text) { EssayText.sections(text) }
    val structure = remember(text) { EssayText.structure(text) }
    val total = EssayText.countWords(text)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Toplam $total kelime", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Giriş", "Gelişme", "Sonuç").forEachIndexed { i, label ->
                Text(
                    (if (structure[i]) "✓ " else "○ ") + label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (structure[i]) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (structure[i]) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (sections.isEmpty()) {
            Text(
                "Henüz başlık yok. H1/H2 düğmeleriyle başlık koy ya da \"Essay şablonu\" ile giriş–gelişme–sonuç bölümlerini ekle. Yapı zorunlu değil; serbest de yazabilirsin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sections.forEach { sec ->
            Column(Modifier.fillMaxWidth().card(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sec.title.ifBlank { "(başlıksız)" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${sec.words} kelime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    EssayText.plain(sec.text).ifBlank { "Boş" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Text(
            "İpucu: Giriş = dikkat çekici bir cümle + thesis statement. Her gelişme paragrafı bir topic sentence ve örnekle başlar. " +
                "Sonuç thesis'i farklı sözcüklerle tekrar eder, yeni bilgi eklemez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoachPanel(
    vm: LibraryViewModel,
    text: String,
    onApply: (CoachSuggestion) -> Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(CoachMode.GRAMMAR) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val suggestions = remember { mutableStateListOf<CoachSuggestion>() }

    fun inspect() {
        if (busy) return
        if (EssayText.countWords(text) < 5) {
            error = "İncelemek için biraz daha yaz (en az birkaç cümle)."
            return
        }
        busy = true
        error = null
        info = null
        review = null
        suggestions.clear()
        val m = mode
        val plain = EssayText.plain(text)
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    if (m.perPhrase) WritingCoach.suggestions(vm.assistantConfig, plain, m) to null
                    else emptyList<CoachSuggestion>() to WritingCoach.review(vm.assistantConfig, plain)
                }
            }
            busy = false
            r.onSuccess { (list, rev) ->
                // Sadece metinde aynen geçen öneriler gösterilir (uygulanabilir olsun).
                val found = list.filter { text.contains(it.original) }
                suggestions.addAll(found)
                review = rev
                info = when {
                    rev != null -> null
                    found.isEmpty() -> "Bu açıdan bir öneri yok 👏"
                    found.size < list.size -> "${found.size} öneri (metinde bulunamayan ${list.size - found.size} öneri gösterilmedi)"
                    else -> "${found.size} öneri"
                }
            }.onFailure {
                ErrorLog.record("Yazma koçu", it)
                error = (it.message ?: "Bağlantı hatası") + " · Editör çalışmaya devam eder; internet gelince tekrar dene."
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        if (!vm.assistantConfig.ready) {
            Text(
                "Yazma koçu uygulamadaki asistan servisini kullanır. Önce asistanı kur (adres, model, anahtar):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistantSetup(vm.assistantConfig) {
                vm.updateAssistantConfig(it)
                vm.showMessage("Asistan ayarları kaydedildi")
            }
        } else {
            CoachBody(vm, mode, { mode = it }, busy, error, info, review, suggestions, canUndo, onUndo, onRun = ::inspect) { sug ->
                if (onApply(sug)) {
                    suggestions.remove(sug)
                    info = "Uygulandı ✓"
                } else {
                    info = "Bu ifade metinde artık yok (değişmiş olabilir)"
                    suggestions.remove(sug)
                }
            }
        }
    }
}

@Composable
private fun CoachBody(
    vm: LibraryViewModel,
    mode: CoachMode,
    onMode: (CoachMode) -> Unit,
    busy: Boolean,
    error: String?,
    info: String?,
    review: String?,
    suggestions: MutableList<CoachSuggestion>,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onRun: () -> Unit,
    onApply: (CoachSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Koç metnini değiştirmez: her öneriyi sen \"Uygula\" dersen girer. Metin yalnızca senin kurduğun asistan servisine gönderilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            CoachMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { onMode(m) }, label = { Text("${m.emoji} ${m.label}") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRun, enabled = !busy) { Text(if (busy) "İnceleniyor…" else "İncele") }
            if (canUndo) OutlinedButton(onClick = onUndo) { Text("↩ Son uygulamayı geri al") }
        }
        if (busy) LoadingState("Koç metnini okuyor…")
        error?.let { Banner(it, BannerKind.ERROR) }
        info?.let { Banner(it, BannerKind.INFO) }
        review?.let { r ->
            Column(Modifier.fillMaxWidth().card(14.dp).padding(14.dp)) {
                MarkdownText(r, MaterialTheme.colorScheme.onSurface)
            }
        }
        suggestions.toList().forEach { sug ->
            Column(Modifier.fillMaxWidth().card(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    sug.original,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(8.dp),
                )
                Text(
                    sug.suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalSouColors.current.success.copy(alpha = 0.16f))
                        .padding(8.dp),
                )
                if (sug.reason.isNotBlank()) {
                    Text(sug.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onApply(sug) }) { Text("Uygula") }
                    TextButton(onClick = { suggestions.remove(sug) }) { Text("Yoksay") }
                }
            }
        }
    }
}
