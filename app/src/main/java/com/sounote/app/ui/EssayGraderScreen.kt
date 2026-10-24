package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.AssistantClient
import com.sounote.app.data.Essay
import com.sounote.app.data.EssayFeedback
import com.sounote.app.data.EssayStore
import com.sounote.app.data.EssayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val GOOD = Color(0xFF2E7D4F)
private val WARN = Color(0xFFC2570C)
private val BAD = Color(0xFFC62828)

private fun scoreColor(score: Int, max: Int = 100): Color {
    val r = score / max.toFloat()
    return when {
        r >= 0.7f -> GOOD
        r >= 0.5f -> WARN
        else -> BAD
    }
}

private sealed interface WritingPage {
    data object List : WritingPage
    data class New(val type: EssayType) : WritingPage
    data class Edit(val essay: Essay) : WritingPage
    data class Result(val essay: Essay) : WritingPage
}

/** Writing pratiği: konu seç, süreyle yaz, asistan L4 ölçütleriyle puanlasın ve düzeltsin. */
@Composable
fun EssayGraderScreen(vm: LibraryViewModel, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf<WritingPage>(WritingPage.List) }
    LaunchedEffect(Unit) { vm.loadEssays() }

    Dialog(
        onDismissRequest = { if (page == WritingPage.List) onDismiss() else page = WritingPage.List },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize().souBackground(), color = Color.Transparent) {
            AnimatedContent(
                targetState = page,
                contentKey = { it::class },
                transitionSpec = { Motion.screenTransition<WritingPage>(targetState !is WritingPage.List).invoke(this) },
                label = "writing",
                modifier = Modifier.systemBarsPadding().imePadding(),
            ) { p ->
                when (p) {
                    WritingPage.List -> EssayList(vm, onBack = onDismiss, onNew = { page = WritingPage.New(it) }, onOpen = {
                        page = if (it.feedback != null) WritingPage.Result(it) else WritingPage.Edit(it)
                    })
                    is WritingPage.New -> NewEssay(vm, p.type, onBack = { page = WritingPage.List }) { topic, type ->
                        val now = System.currentTimeMillis()
                        page = WritingPage.Edit(Essay(UUID.randomUUID().toString(), type, topic, "", now, now))
                    }
                    is WritingPage.Edit -> EssayEditor(vm, p.essay, onBack = { page = WritingPage.List }) { graded ->
                        page = WritingPage.Result(graded)
                    }
                    is WritingPage.Result -> EssayResult(vm, p.essay, onBack = { page = WritingPage.List }) {
                        page = WritingPage.Edit(p.essay)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        IconButton(onClick = onBack) { Icon(SouIcons.ArrowBack, contentDescription = "Geri") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun EssayList(vm: LibraryViewModel, onBack: () -> Unit, onNew: (EssayType) -> Unit, onOpen: (Essay) -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr")) }
    var deleting by remember { mutableStateOf<Essay?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Writing pratiği", onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .appear(0)
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(LocalSouColors.current.hero))
                        .padding(20.dp),
                ) {
                    Text("Yeni yazı", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Konu seç, süreyle yaz; asistan L4 yazma ölçütlerine göre 100 üzerinden puanlasın, hatalarını ve " +
                            "düzeltilmiş halini göstersin.",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EssayType.entries.forEach { t ->
                            Button(
                                onClick = { onNew(t) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.pressScale(),
                            ) { Text(t.label, maxLines = 1) }
                        }
                    }
                }
            }
            val graded = vm.essays.mapNotNull { it.feedback?.total }
            if (graded.size >= 2) {
                item {
                    Row(Modifier.fillMaxWidth().appear(1).card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("İlerlemen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${graded.size} yazı · ortalama ${graded.average().toInt()} · son ${graded.first()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Son puanlar (eskiden yeniye) küçük çubuklar
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(40.dp)) {
                            graded.take(8).reversed().forEach { sc ->
                                Box(Modifier.width(8.dp).height((40 * sc / 100f).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(3.dp)).background(scoreColor(sc)))
                            }
                        }
                    }
                }
            }
            if (vm.essays.isNotEmpty()) {
                item { Text("Yazıların", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            }
            items(vm.essays, key = { it.id }) { e ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .pressScale(0.98f)
                        .card()
                        .clickable { onOpen(e) }
                        .padding(14.dp),
                ) {
                    val score = e.feedback?.total
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background((score?.let(::scoreColor) ?: MaterialTheme.colorScheme.outline).copy(alpha = 0.15f)),
                    ) {
                        Text(
                            score?.toString() ?: "✎",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = score?.let(::scoreColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(e.topic, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.type.label} · ${e.words} kelime · ${fmt.format(Date(e.updated))}" + if (score == null) " · taslak" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { deleting = e }) { Icon(SouIcons.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Yazı silinsin mi?") },
            text = { Text(e.topic, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { vm.deleteEssay(e); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewEssay(vm: LibraryViewModel, initial: EssayType, onBack: () -> Unit, onStart: (String, EssayType) -> Unit) {
    var type by remember { mutableStateOf(initial) }
    var own by remember { mutableStateOf("") }
    var suggesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Konu seç", onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EssayType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
            }
            Text(
                "${type.minWords}-${type.maxWords} kelime · ${type.minutes} dk · ${type.brief}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = own,
                onValueChange = { own = it },
                label = { Text("Kendi konun (hocanın verdiği soru)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(own.trim(), type) }, enabled = own.isNotBlank()) { Text("Bu konuyla başla") }
                OutlinedButton(
                    onClick = {
                        suggesting = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { AssistantClient.suggestTopic(vm.assistantConfig, type) } }
                                .onSuccess { own = it }
                                .onFailure { vm.showMessage(it.message ?: "Konu alınamadı") }
                            suggesting = false
                        }
                    },
                    enabled = vm.assistantConfig.ready && !suggesting,
                ) { Text(if (suggesting) "Düşünüyor…" else "Asistan konu önersin") }
            }
            Text("Örnek konular", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            EssayStore.TOPICS.forEachIndexed { i, t ->
                Text(
                    t,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .appear(i)
                        .pressScale(0.98f)
                        .card()
                        .clickable { onStart(t, type) }
                        .padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EssayEditor(vm: LibraryViewModel, start: Essay, onBack: () -> Unit, onGraded: (Essay) -> Unit) {
    var text by remember { mutableStateOf(start.text) }
    var seconds by remember { mutableStateOf(start.seconds) }
    var grading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTopic by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val type = start.type
    val words = Essay.wordCount(text)

    fun current() = start.copy(text = text, seconds = seconds, updated = System.currentTimeMillis())
    fun saveAndBack() {
        if (text.isNotBlank()) vm.saveEssay(current())
        onBack()
    }

    // Süre: yazarken saniye saniye artar.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (!grading) seconds++
        }
    }
    // Taslak otomatik kaydedilir.
    LaunchedEffect(text) {
        delay(1500)
        if (text.isNotBlank()) vm.saveEssay(current())
    }

    androidx.activity.compose.BackHandler { saveAndBack() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(type.label, ::saveAndBack) {
            val over = seconds > type.minutes * 60
            Text(
                "⏱ %d:%02d / %d dk".format(seconds / 60, seconds % 60, type.minutes),
                style = MaterialTheme.typography.labelLarge,
                color = if (over) BAD else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .clickable { showTopic = !showTopic }
                    .padding(14.dp),
            ) {
                Text(start.topic, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = if (showTopic) 6 else 1, overflow = TextOverflow.Ellipsis)
                if (showTopic) {
                    Text(type.brief, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            // Kelime sayısı: hedef aralığa göre renk
            val inRange = words in type.minWords..type.maxWords
            val progress by animateFloatAsState((words / type.maxWords.toFloat()).coerceIn(0f, 1f), label = "words")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (inRange) GOOD else if (words > type.maxWords) WARN else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    "  $words / ${type.minWords}-${type.maxWords} kelime",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (inRange) GOOD else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().card().padding(16.dp)) {
                if (text.isEmpty()) {
                    Text(
                        "Write your ${type.label.lowercase()} here…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (!vm.assistantConfig.ready) {
                    Text(
                        "Değerlendirme için Ayarlar → Yapay zekâ asistanı'nı kur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        grading = true
                        error = null
                        val essay = current()
                        vm.saveEssay(essay)
                        scope.launch {
                            val r = runCatching {
                                withContext(Dispatchers.IO) { AssistantClient.gradeEssay(vm.assistantConfig, essay.type, essay.topic, essay.text) }
                            }
                            grading = false
                            r.onSuccess { fb ->
                                val graded = essay.copy(feedback = fb, updated = System.currentTimeMillis())
                                vm.saveEssay(graded)
                                onGraded(graded)
                            }.onFailure {
                                ErrorLog.record("Writing değerlendirme", it)
                                error = it.message ?: "Değerlendirilemedi"
                            }
                        }
                    },
                    enabled = !grading && words >= 20 && vm.assistantConfig.ready,
                ) {
                    if (grading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Text("  Değerlendiriliyor…")
                    } else {
                        Text("Değerlendir")
                    }
                }
            }
        }
    }
}

@Composable
private fun EssayResult(vm: LibraryViewModel, essay: Essay, onBack: () -> Unit, onRevise: () -> Unit) {
    val fb = essay.feedback ?: return
    val context = LocalContext.current
    var showImproved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Değerlendirme", onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        ) {
            item { ScoreHeader(essay, fb) }
            item {
                Column(Modifier.fillMaxWidth().appear(1).card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fb.criteria.forEachIndexed { i, c -> CriterionRow(c.name, c.score, c.max, c.comment, i) }
                }
            }
            if (fb.strengths.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().appear(2).card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("İyi yaptıkların", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        fb.strengths.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (fb.mistakes.isNotEmpty()) {
                item { Text("Düzeltmeler (${fb.mistakes.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.appear(3)) }
                items(fb.mistakes) { m ->
                    Column(Modifier.fillMaxWidth().appear(3).card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.wrong, color = BAD, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodyMedium)
                        Text("→ ${m.right}", color = GOOD, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        if (m.why.isNotBlank()) Text(m.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (fb.improved.isNotBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Geliştirilmiş hali", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showImproved = !showImproved }) { Text(if (showImproved) "Gizle" else "Göster") }
                        }
                        if (showImproved) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(fb.improved, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                            }
                            TextButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Essay", fb.improved))
                                vm.showMessage("Kopyalandı")
                            }) { Text("Kopyala") }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Senin yazın (${essay.words} kelime)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(essay.text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Button(onClick = onRevise, modifier = Modifier.weight(1f)) { Text("Düzelt ve tekrar gönder") }
                    OutlinedButton(onClick = {
                        val body = buildString {
                            appendLine("Konu: ${essay.topic}")
                            appendLine("Puan: ${fb.total}/100")
                            appendLine()
                            appendLine(essay.text)
                            appendLine()
                            appendLine("— Düzeltmeler —")
                            fb.mistakes.forEach { appendLine("• ${it.wrong} → ${it.right} (${it.why})") }
                            if (fb.improved.isNotBlank()) {
                                appendLine()
                                appendLine("— Geliştirilmiş hali —")
                                appendLine(fb.improved)
                            }
                        }
                        vm.saveTextNote(UUID.randomUUID().toString(), "Writing: ${essay.topic.take(40)}", body)
                        vm.showMessage("Yazılı notlara kaydedildi")
                    }, modifier = Modifier.weight(1f)) { Text("Notlara kaydet") }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(essay: Essay, fb: EssayFeedback) {
    val color = scoreColor(fb.total)
    val anim by animateFloatAsState(fb.total / 100f, tween(900), label = "score")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().appear(0).card().padding(18.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 12.dp.toPx()
                drawArc(track, -90f, 360f, false, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * anim, false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(anim * 100).toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
                Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    fb.total >= 85 -> "Harika"
                    fb.total >= 70 -> "İyi gidiyor"
                    fb.total >= 50 -> "Geliştirilebilir"
                    else -> "Biraz daha pratik"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(fb.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${essay.type.label} · ${essay.words} kelime · %d dk".format(essay.seconds / 60),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CriterionRow(name: String, score: Int, max: Int, comment: String, index: Int) {
    val target = if (max > 0) score / max.toFloat() else 0f
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150L * index); started = true }
    val p by animateFloatAsState(if (started) target else 0f, tween(700), label = "crit")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$score / $max", style = MaterialTheme.typography.labelLarge, color = scoreColor(score, max))
        }
        LinearProgressIndicator(
            progress = { p },
            color = scoreColor(score, max),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        if (comment.isNotBlank()) Text(comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
