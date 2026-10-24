@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.ErrorLog
import com.sounote.app.ExamReminders
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.AssistantClient
import com.sounote.app.data.Course
import com.sounote.app.data.Exam
import com.sounote.app.data.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val TR = Locale.forLanguageTag("tr")

/*
 * Asistanlı çalışma araçları: sınav öncesi çalışma planı ve deneme sınavı.
 */

/** Sınavın konuları ve kaynak başlıkları (asistana bağlam). */
private fun examDetails(vm: LibraryViewModel, exam: Exam): String {
    val topics = vm.catalog.courses.flatMap { it.topics }.filter { it.id in exam.topics }
    if (topics.isEmpty()) return "Ders: ${exam.course}. Konular belirtilmemiş; L4 genel tekrarı planla."
    return "Ders: ${exam.course}. Sınav konuları:\n" + topics.joinToString("\n") { t ->
        "- ${t.name}: " + t.resources.take(6).joinToString(", ") { it.title }
    }
}

/** Bugünden [k] gün sonra saat 18:00. */
private fun dayAt(k: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, k)
    set(Calendar.HOUR_OF_DAY, 18)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Sınav öncesi plan: sınavı seç → asistan günlük görevler önerir → seçilenler planlayıcıya eklenir. */
@Composable
fun ExamPlanDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val exams = remember { vm.allExams.filter { it.time > now && it.kind == "exam" && !vm.isDone(it) }.take(8) }
    var selected by remember { mutableStateOf(exams.firstOrNull()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    val picked = remember { mutableStateListOf<Boolean>() }

    fun make() {
        val exam = selected ?: return
        val days = ExamReminders.daysUntil(exam.time).coerceIn(1, 21)
        busy = true
        error = null
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    AssistantClient.studyPlan(vm.assistantConfig, exam.title.ifBlank { exam.course }, examDetails(vm, exam), days)
                }
            }
            busy = false
            r.onSuccess {
                plan = it
                picked.clear()
                repeat(it.size) { picked.add(true) }
            }.onFailure {
                ErrorLog.record("Çalışma planı", it)
                error = it.message ?: "Plan hazırlanamadı"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "Sınav öncesi plan" else "Çalışma planı") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            ) {
                when {
                    !vm.assistantConfig.ready -> Text("Önce Ayarlar > Asistan'dan asistanı kur.")
                    exams.isEmpty() -> Text("Yaklaşan sınav yok.")
                    plan == null -> {
                        Text("Hangi sınav için?", style = MaterialTheme.typography.labelLarge)
                        exams.forEach { e ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selected = e },
                            ) {
                                RadioButton(selected = selected == e, onClick = { selected = e })
                                Column(Modifier.weight(1f)) {
                                    Text(e.title.ifBlank { e.course }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${e.course} · ${countdownText(e.time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (busy) LoadingState("Plan hazırlanıyor…")
                    }
                    else -> {
                        Text(
                            "İstemediklerinin işaretini kaldır. Görevler planlayıcıya ödev olarak eklenir (hatırlatma kurulmaz).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val fmt = SimpleDateFormat("d MMM EEE", TR)
                        plan!!.forEachIndexed { i, (day, task) ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picked[i] = !picked[i] },
                            ) {
                                Checkbox(checked = picked[i], onCheckedChange = { picked[i] = it })
                                Column(Modifier.padding(top = 4.dp)) {
                                    Text(fmt.format(Date(dayAt(day))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(task, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            val p = plan
            val exam = selected
            if (p == null) {
                Button(enabled = !busy && exam != null && vm.assistantConfig.ready, onClick = ::make) { Text("Plan hazırla") }
            } else if (exam != null) {
                Button(enabled = picked.any { it }, onClick = {
                    val tasks = p.filterIndexed { i, _ -> picked[i] }.map { (day, task) ->
                        Exam(
                            id = "m:" + UUID.randomUUID(),
                            course = exam.course,
                            title = task,
                            time = dayAt(day),
                            notes = "Plan: ${exam.title.ifBlank { exam.course }}",
                            kind = "task",
                            slot = Exam.PLAN_SLOT,
                        )
                    }
                    vm.addExams(tasks)
                    vm.showMessage("${tasks.size} görev planlayıcıya eklendi")
                    onDismiss()
                }) { Text("Planlayıcıya ekle") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (plan != null) plan = null else onDismiss() }) { Text(if (plan != null) "Geri" else "Vazgeç") }
        },
    )
}

/** Deneme sınavı ayarı: konu ve soru sayısı. Metin, dersin indirilmiş PDF'lerinden okunur. */
@Composable
fun PracticeSetupDialog(vm: LibraryViewModel, course: Course, onDismiss: () -> Unit) {
    var topic by remember { mutableStateOf<Topic?>(null) }
    var count by remember { mutableStateOf(10) }
    var start by remember { mutableStateOf(false) }
    if (start) {
        PracticeTestScreen(vm, course, topic, count, onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deneme sınavı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Konu", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(selected = topic == null, onClick = { topic = null }, label = { Text("Bütün ders") })
                    course.topics.forEach { t ->
                        FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(t.name, maxLines = 1) })
                    }
                }
                Text("Soru sayısı", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) }
                }
                if (!vm.assistantConfig.ready) {
                    Text("Önce Ayarlar > Asistan'dan asistanı kur.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(enabled = vm.assistantConfig.ready, onClick = { start = true }) { Text("Başla") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Deneme sınavı: sorular tek tek; şıkka dokununca doğru/yanlış ve açıklama; sonunda puan ve yanlışlar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PracticeTestScreen(vm: LibraryViewModel, course: Course, topic: Topic?, count: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<AssistantClient.Question>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(-1) }
    val answers = remember { mutableStateListOf<Int>() }
    val sou = LocalSouColors.current
    val title = topic?.name ?: course.name

    fun load() {
        error = null
        questions = null
        index = 0
        chosen = -1
        answers.clear()
        scope.launch {
            val r = runCatching {
                // Konunun (ya da dersin) indirilmiş PDF'lerinin metni; yoksa konu başlıklarından
                val docs = (topic?.let { t -> t.resources.mapNotNull { vm.downloadedDoc(course, it) } } ?: vm.courseDocs(course)).take(6)
                val per = 14_000 / docs.size.coerceAtLeast(1)
                val sb = StringBuilder()
                for (d in docs) sb.append("### ").append(d.title).append('\n').append(runCatching { vm.documentText(d, 0, 9_999) }.getOrDefault("").take(per)).append("\n\n")
                val label = topic?.let { "${course.name} · ${it.name} (" + it.resources.take(8).joinToString(", ") { r -> r.title } + ")" } ?: course.name
                withContext(Dispatchers.IO) { AssistantClient.practiceTest(vm.assistantConfig, label, sb.toString(), count) }
            }
            r.onSuccess { questions = it }.onFailure {
                ErrorLog.record("Deneme sınavı", it)
                error = it.message ?: "Sorular hazırlanamadı"
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                val qs = questions
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) { Icon(SouIcons.Close, contentDescription = "Kapat") }
                    Column(Modifier.weight(1f)) {
                        Text("Deneme sınavı", style = MaterialTheme.typography.titleMedium)
                        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (qs != null && index < qs.size) Text("${index + 1}/${qs.size}", style = MaterialTheme.typography.labelLarge)
                }
                if (qs != null) ProgressBar(index.toFloat() / qs.size, Modifier.fillMaxWidth().padding(vertical = 10.dp))
                when {
                    error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                        Button(onClick = ::load) { Text("Tekrar dene") }
                    }
                    qs == null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                        Text("Asistan $count soru hazırlıyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    index >= qs.size -> {
                        // Sonuç: puan ve yanlış yapılanlar
                        val right = qs.indices.count { answers.getOrNull(it) == qs[it].answer }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        ) {
                            ProgressRing(right.toFloat() / qs.size, size = 120.dp, stroke = 10.dp, color = if (right * 2 >= qs.size) sou.success else sou.danger)
                            Text("$right / ${qs.size} doğru", style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onDismiss) { Text("Kapat") }
                                Button(onClick = ::load) { Text("Yeni test") }
                            }
                            qs.forEachIndexed { i, q ->
                                if (answers.getOrNull(i) != q.answer) {
                                    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${i + 1}. ${q.q}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        answers.getOrNull(i)?.let { a -> if (a >= 0) Text("Senin cevabın: ${q.options[a]}", color = sou.danger, style = MaterialTheme.typography.bodySmall) }
                                        Text("Doğrusu: ${q.options[q.answer]}", color = sou.success, style = MaterialTheme.typography.bodySmall)
                                        if (q.why.isNotBlank()) Text(q.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val q = qs[index]
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        ) {
                            Text(q.q, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                            q.options.forEachIndexed { i, o ->
                                val answered = chosen >= 0
                                val bg = when {
                                    answered && i == q.answer -> sou.success.copy(alpha = 0.18f)
                                    answered && i == chosen -> sou.danger.copy(alpha = 0.18f)
                                    else -> Color.Transparent
                                }
                                val border = when {
                                    answered && i == q.answer -> sou.success
                                    answered && i == chosen -> sou.danger
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .background(bg)
                                        .border(1.5.dp, border, RoundedCornerShape(16.dp))
                                        .clickable(enabled = !answered) { chosen = i }
                                        .padding(14.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
                                    ) { Text("ABCDE".getOrElse(i) { '?' }.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                                    Text(o, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    if (answered && i == q.answer) Icon(SouIcons.CheckCircle, contentDescription = "Doğru", tint = sou.success)
                                }
                            }
                            if (chosen >= 0 && q.why.isNotBlank()) {
                                Text(q.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            enabled = chosen >= 0,
                            onClick = {
                                answers.add(chosen)
                                chosen = -1
                                index++
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(if (index == qs.size - 1) "Bitir" else "Sonraki") }
                    }
                }
            }
        }
    }
}
