@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Exercise
import com.sounote.app.data.ExerciseType
import com.sounote.app.data.ExerciseVerdict
import com.sounote.app.data.Exercises
import com.sounote.app.data.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Alıştırma: asistan karışık türde madde hazırlar. İki mod:
 * "Önce cevapla" — öğrenci cevaplar, uygulama çoktan seçmeli ve birebir tutan boşlukları kendi denetler,
 * gerisini (yeniden yazma, açık uçlu, tutmayan boşluk) asistan değerlendirir;
 * "Cevabı göster" — her maddede cevap dokununca açılır.
 *
 * Kaynak: [course] verilirse konu seçilir ve dersin indirilmiş PDF'leri okunur; yoksa [sourceTitle] + [loadSource]
 * (açık belge, sayfa, seçim ya da not).
 */
@Composable
fun ExerciseScreen(
    vm: LibraryViewModel,
    course: Course? = null,
    initialTopic: Topic? = null,
    sourceTitle: String = "",
    loadSource: (suspend () -> String)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf(initialTopic) }
    var count by remember { mutableStateOf(10) }
    val types = remember { mutableStateListOf(*ExerciseType.entries.toTypedArray()) }
    var answerFirst by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<Exercise>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val choices = remember { mutableStateMapOf<Int, Int>() }
    val texts = remember { mutableStateMapOf<Int, String>() }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var verdicts by remember { mutableStateOf<List<ExerciseVerdict>?>(null) }
    val title = topic?.name ?: course?.name ?: sourceTitle.ifBlank { "Alıştırma" }

    fun reset() {
        choices.clear()
        texts.clear()
        revealed.clear()
        verdicts = null
        error = null
    }

    fun generate() {
        if (loading) return
        reset()
        loading = true
        scope.launch {
            val r = runCatching {
                val source = when {
                    loadSource != null -> loadSource()
                    course != null -> courseText(vm, course, topic)
                    else -> ""
                }
                val label = when {
                    course != null -> topic?.let { "${course.name} · ${it.name} (" + it.resources.take(8).joinToString(", ") { r -> r.title } + ")" } ?: course.name
                    else -> sourceTitle.ifBlank { "L4 genel tekrar" }
                }
                withContext(Dispatchers.IO) { Exercises.generate(vm.assistantConfig, label, source, count, types.toSet()) }
            }
            loading = false
            r.onSuccess { items = it }.onFailure {
                ErrorLog.record("Alıştırma", it)
                error = it.message ?: "Alıştırma hazırlanamadı"
            }
        }
    }

    fun check() {
        val list = items ?: return
        if (checking) return
        error = null
        val local = list.mapIndexed { i, e -> Exercises.checkLocally(e, choices[i] ?: -1, texts[i].orEmpty()) }
        val ask = list.indices.filter { local[it] == null }
        if (ask.isEmpty()) {
            verdicts = local.map { it!! }
            finish(vm, topic, verdicts!!)
            return
        }
        checking = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { Exercises.grade(vm.assistantConfig, ask.map { list[it] to texts[it].orEmpty() }) }
            }
            checking = false
            r.onSuccess { graded ->
                val out = local.toMutableList()
                ask.forEachIndexed { k, i -> out[i] = graded[k] }
                verdicts = out.map { it!! }
                finish(vm, topic, verdicts!!)
            }.onFailure {
                ErrorLog.record("Alıştırma değerlendirme", it)
                error = it.message ?: "Değerlendirilemedi"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (items != null && !loading) { items = null; reset() } else onDismiss() }) {
                        Icon(if (items != null) SouIcons.ArrowBack else SouIcons.Close, contentDescription = "Geri")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Alıştırma", style = MaterialTheme.typography.titleMedium)
                        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    items?.let { list ->
                        TextButton(onClick = {
                            saveToNotes(vm, title, list, if (answerFirst) verdicts else null, choices, texts)
                        }) { Text("Notlara kaydet") }
                    }
                }
                val list = items
                when {
                    loading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                        Text("Asistan $count madde hazırlıyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    list == null -> Setup(
                        vm, course, topic, { topic = it }, count, { count = it }, types, answerFirst, { answerFirst = it }, error, ::generate,
                    )
                    else -> Worksheet(
                        list, answerFirst, choices, texts, revealed, verdicts, checking, error,
                        onCheck = ::check,
                        onRetry = { reset() },
                        onNew = ::generate,
                    )
                }
            }
        }
    }
}

/** Sonuç: konuya bağlıysa puan konu haritasına yazılır. */
private fun finish(vm: LibraryViewModel, topic: Topic?, verdicts: List<ExerciseVerdict>) {
    if (verdicts.isEmpty()) return
    vm.markActive()
    topic?.let { vm.recordTestScore(it.id, (verdicts.sumOf { v -> v.score.toDouble() } * 100 / verdicts.size).toInt()) }
}

/** Konunun (ya da dersin) indirilmiş PDF'lerinin metni; hiç yoksa boş (asistan konu başlığından hazırlar). */
private suspend fun courseText(vm: LibraryViewModel, course: Course, topic: Topic?): String {
    val docs = (topic?.let { t -> t.resources.mapNotNull { vm.downloadedDoc(course, it) } } ?: vm.courseDocs(course)).take(6)
    val per = 14_000 / docs.size.coerceAtLeast(1)
    val sb = StringBuilder()
    for (d in docs) sb.append("### ").append(d.title).append('\n').append(runCatching { vm.documentText(d, 0, 9_999) }.getOrDefault("").take(per)).append("\n\n")
    return sb.toString()
}

@Composable
private fun Setup(
    vm: LibraryViewModel,
    course: Course?,
    topic: Topic?,
    onTopic: (Topic?) -> Unit,
    count: Int,
    onCount: (Int) -> Unit,
    types: MutableList<ExerciseType>,
    answerFirst: Boolean,
    onAnswerFirst: (Boolean) -> Unit,
    error: String?,
    onStart: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).card().padding(16.dp),
    ) {
        if (course != null) {
            Text("Konu", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = topic == null, onClick = { onTopic(null) }, label = { Text("Bütün ders") })
                course.topics.forEach { t -> FilterChip(selected = topic == t, onClick = { onTopic(t) }, label = { Text(t.name, maxLines = 1) }) }
            }
        }
        Text("Soru türleri", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ExerciseType.entries.forEach { t ->
                val on = t in types
                FilterChip(selected = on, onClick = {
                    if (on && types.size > 1) types.remove(t) else if (!on) types.add(t)
                }, label = { Text(t.label) })
            }
        }
        Text("Madde sayısı", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15).forEach { n -> FilterChip(selected = count == n, onClick = { onCount(n) }, label = { Text("$n") }) }
        }
        Text("Nasıl çalışmak istersin?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = answerFirst, onClick = { onAnswerFirst(true) }, label = { Text("Önce cevapla") })
            FilterChip(selected = !answerFirst, onClick = { onAnswerFirst(false) }, label = { Text("Cevabı göster") })
        }
        Text(
            if (answerFirst) "Cevaplarını yaz, \"Kontrol et\"e bas: yazdıklarını asistan değerlendirir."
            else "Her maddede cevap dokununca açılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!vm.assistantConfig.ready) {
            Text("Önce Ayarlar > Asistan'dan asistanı kur.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = onStart, enabled = vm.assistantConfig.ready, modifier = Modifier.fillMaxWidth()) { Text("Hazırla") }
    }
}

@Composable
private fun Worksheet(
    list: List<Exercise>,
    answerFirst: Boolean,
    choices: MutableMap<Int, Int>,
    texts: MutableMap<Int, String>,
    revealed: MutableMap<Int, Boolean>,
    verdicts: List<ExerciseVerdict>?,
    checking: Boolean,
    error: String?,
    onCheck: () -> Unit,
    onRetry: () -> Unit,
    onNew: () -> Unit,
) {
    val sou = LocalSouColors.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (verdicts != null) {
            item {
                val total = verdicts.sumOf { it.score.toDouble() }
                val pct = (total * 100 / verdicts.size).toInt()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(16.dp)) {
                    ProgressRing(pct / 100f, size = 72.dp, stroke = 7.dp, color = if (pct >= 70) sou.success else if (pct >= 50) sou.warning else sou.danger)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("%.1f / %d".format(total, verdicts.size), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${verdicts.count { it.score >= 1f }} doğru · ${verdicts.count { it.score in 0.01f..0.99f }} kısmen · ${verdicts.count { it.score <= 0f }} yanlış",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        itemsIndexed(list) { i, e ->
            ExerciseCard(
                i, e, answerFirst,
                choice = choices[i] ?: -1, onChoice = { if (verdicts == null) choices[i] = it },
                text = texts[i].orEmpty(), onText = { if (verdicts == null) texts[i] = it },
                revealed = revealed[i] == true || verdicts != null, onReveal = { revealed[i] = !(revealed[i] ?: false) },
                verdict = verdicts?.getOrNull(i),
            )
        }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    answerFirst && verdicts == null -> {
                        Button(onClick = onCheck, enabled = !checking && (choices.isNotEmpty() || texts.values.any { it.isNotBlank() })) {
                            if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Text(if (checking) "  Değerlendiriliyor…" else "Kontrol et")
                        }
                    }
                    answerFirst -> OutlinedButton(onClick = onRetry) { Text("Tekrar çöz") }
                    else -> OutlinedButton(onClick = { list.indices.forEach { revealed[it] = true } }) { Text("Hepsini göster") }
                }
                OutlinedButton(onClick = onNew, enabled = !checking) { Text("Yeni alıştırma") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    index: Int,
    e: Exercise,
    answerFirst: Boolean,
    choice: Int,
    onChoice: (Int) -> Unit,
    text: String,
    onText: (String) -> Unit,
    revealed: Boolean,
    onReveal: () -> Unit,
    verdict: ExerciseVerdict?,
) {
    val sou = LocalSouColors.current
    val vColor = when {
        verdict == null -> MaterialTheme.colorScheme.outlineVariant
        verdict.score >= 1f -> sou.success
        verdict.score > 0f -> sou.warning
        else -> sou.danger
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .then(if (verdict != null) Modifier.border(1.5.dp, vColor, RoundedCornerShape(18.dp)) else Modifier)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1} · ${e.type.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (verdict != null) {
                Text(
                    when {
                        verdict.score >= 1f -> "Doğru"
                        verdict.score > 0f -> "Kısmen"
                        else -> "Yanlış"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = vColor,
                )
            }
        }
        if (e.given.isNotBlank()) {
            Text(
                e.given,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
            )
        }
        Text(e.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (e.type == ExerciseType.MCQ) {
            e.options.forEachIndexed { i, o ->
                val showRight = revealed && i == e.answerIndex
                val showWrong = verdict != null && i == choice && i != e.answerIndex
                val border = when {
                    showRight -> sou.success
                    showWrong -> sou.danger
                    i == choice -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (showRight) sou.success.copy(alpha = 0.14f) else if (showWrong) sou.danger.copy(alpha = 0.14f) else Color.Transparent)
                        .border(if (i == choice || showRight) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp))
                        .clickable(enabled = verdict == null) { onChoice(i) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(50))
                            .background(if (i == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            "ABCDE".getOrElse(i) { '?' }.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == choice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(o, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                }
            }
        } else if (answerFirst) {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                enabled = verdict == null,
                placeholder = { Text(if (e.type == ExerciseType.GAP) "Boşluğa gelecek ifade" else "Cevabın") },
                minLines = if (e.type == ExerciseType.GAP) 1 else 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (verdict != null) {
            if (verdict.feedback.isNotBlank()) Text(verdict.feedback, style = MaterialTheme.typography.bodySmall, color = vColor)
            if (verdict.fix.isNotBlank() && verdict.score < 1f) {
                Text("Düzeltilmiş: ${verdict.fix}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!answerFirst && !revealed) {
            TextButton(onClick = onReveal) { Text("Cevabı göster") }
        }
        if (revealed) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(sou.success.copy(alpha = 0.10f)).padding(12.dp),
            ) {
                Text(
                    (if (e.type == ExerciseType.OPEN || e.type == ExerciseType.REWRITE) "Örnek cevap: " else "Cevap: ") + e.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (e.accept.isNotEmpty()) Text("Bu da olur: " + e.accept.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                if (e.why.isNotBlank()) Text(e.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!answerFirst) {
                    TextButton(onClick = onReveal, modifier = Modifier.align(Alignment.End)) { Text("Gizle") }
                }
            }
        }
    }
}

private fun saveToNotes(
    vm: LibraryViewModel,
    title: String,
    list: List<Exercise>,
    verdicts: List<ExerciseVerdict>?,
    choices: Map<Int, Int>,
    texts: Map<Int, String>,
) {
    val body = buildString {
        verdicts?.let { v -> appendLine("Puan: %.1f / %d".format(v.sumOf { it.score.toDouble() }, v.size)).appendLine() }
        list.forEachIndexed { i, e ->
            appendLine("${i + 1}. (${e.type.label}) ${e.question}")
            if (e.given.isNotBlank()) appendLine("   ${e.given}")
            e.options.forEachIndexed { k, o -> appendLine("   ${"ABCDE".getOrElse(k) { '?' }}) $o") }
            if (verdicts != null) {
                val mine = if (e.type == ExerciseType.MCQ) choices[i]?.let { e.options.getOrNull(it) } else texts[i]
                appendLine("   Senin cevabın: ${mine?.ifBlank { null } ?: "—"}")
            }
            appendLine("   Cevap: ${e.answer}")
            if (e.why.isNotBlank()) appendLine("   ${e.why}")
            appendLine()
        }
    }
    vm.saveTextNote(UUID.randomUUID().toString(), "Alıştırma: ${title.take(40)}", body)
    vm.showMessage("Yazılı notlara kaydedildi")
}
