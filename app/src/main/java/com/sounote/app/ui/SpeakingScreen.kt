@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.AssistantClient
import com.sounote.app.data.SpeakingAttempt
import com.sounote.app.data.SpeakingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class Stage { HOME, PREP, SPEAK, GRADING, RESULT }

/**
 * Speaking pratiği: konu seç → hazırlan → konuş (cihazın İngilizce konuşma tanıması canlı yazıya döker) →
 * asistan L4 ölçütleriyle puanlar. Denemeler filesDir/speaking'e kaydedilir.
 */
@Composable
fun SpeakingScreen(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SpeakingStore(context) }
    var attempts by remember { mutableStateOf(store.all()) }
    var stage by remember { mutableStateOf(Stage.HOME) }
    var topic by remember { mutableStateOf(SpeakingStore.TOPICS.random()) }
    var minutes by remember { mutableIntStateOf(2) }
    var prepSeconds by remember { mutableIntStateOf(30) }
    var current by remember { mutableStateOf<SpeakingAttempt?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) stage = if (prepSeconds > 0) Stage.PREP else Stage.SPEAK
        else vm.showMessage("Konuşmak için mikrofon izni gerekiyor")
    }

    fun start() {
        error = null
        if (!available) {
            error = "Bu cihazda konuşma tanıma yok. Google uygulamasını kurup tekrar dene."
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            stage = if (prepSeconds > 0) Stage.PREP else Stage.SPEAK
        }
    }

    fun grade(transcript: String, seconds: Int) {
        val base = SpeakingAttempt(UUID.randomUUID().toString(), topic, transcript, seconds, System.currentTimeMillis(), null)
        vm.markActive()
        if (!vm.assistantConfig.ready || base.words < 5) {
            runCatching { store.save(base) }
            attempts = store.all()
            current = base
            stage = Stage.RESULT
            return
        }
        stage = Stage.GRADING
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { AssistantClient.gradeSpeaking(vm.assistantConfig, topic, transcript, seconds, base.words) }
            }
            val a = base.copy(feedback = r.getOrNull())
            r.onFailure {
                ErrorLog.record("Speaking değerlendirme", it)
                error = it.message ?: "Değerlendirilemedi"
            }
            runCatching { withContext(Dispatchers.IO) { store.save(a) } }
            attempts = store.all()
            current = a
            stage = Stage.RESULT
        }
    }

    BackHandler(enabled = stage != Stage.HOME && stage != Stage.GRADING) { stage = Stage.HOME }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (stage == Stage.HOME) onDismiss() else stage = Stage.HOME }) {
                        Icon(if (stage == Stage.HOME) SouIcons.Close else SouIcons.ArrowBack, contentDescription = "Geri")
                    }
                    Text("Speaking pratiği", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                when (stage) {
                    Stage.HOME -> SpeakingHome(
                        vm, topic, { topic = it }, minutes, { minutes = it }, prepSeconds, { prepSeconds = it },
                        attempts, error,
                        onStart = ::start,
                        onOpen = { current = it; error = null; stage = Stage.RESULT },
                        onDelete = { a -> store.delete(a.id); attempts = store.all() },
                    )
                    Stage.PREP -> PrepStage(topic, prepSeconds) { stage = Stage.SPEAK }
                    Stage.SPEAK -> SpeakStage(topic, minutes * 60, onError = { error = it; stage = Stage.HOME }) { text, secs -> grade(text, secs) }
                    Stage.GRADING -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                        Text("Asistan konuşmanı değerlendiriyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Stage.RESULT -> current?.let { a ->
                        SpeakingResult(a, error, onAgain = { error = null; stage = Stage.HOME }) {
                            vm.saveTextNote(UUID.randomUUID().toString(), "Speaking: ${a.topic.take(40)}", buildString {
                                appendLine("Konu: ${a.topic}")
                                a.feedback?.let { appendLine("Puan: ${it.total}/100") }
                                appendLine()
                                appendLine(a.transcript)
                                a.feedback?.let { fb ->
                                    if (fb.mistakes.isNotEmpty()) {
                                        appendLine()
                                        appendLine("— Düzeltmeler —")
                                        fb.mistakes.forEach { appendLine("• ${it.wrong} → ${it.right} (${it.why})") }
                                    }
                                    if (fb.improved.isNotBlank()) {
                                        appendLine()
                                        appendLine("— Örnek cevap —")
                                        appendLine(fb.improved)
                                    }
                                }
                            })
                            vm.showMessage("Yazılı notlara kaydedildi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakingHome(
    vm: LibraryViewModel,
    topic: String,
    onTopic: (String) -> Unit,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    prep: Int,
    onPrep: (Int) -> Unit,
    attempts: List<SpeakingAttempt>,
    error: String?,
    onStart: () -> Unit,
    onOpen: (SpeakingAttempt) -> Unit,
    onDelete: (SpeakingAttempt) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var suggesting by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr")) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Konu", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(topic, onTopic, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTopic(SpeakingStore.TOPICS.filter { it != topic }.random()) }) {
                        Icon(SouIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Başka konu", modifier = Modifier.padding(start = 6.dp))
                    }
                    if (vm.assistantConfig.ready) {
                        OutlinedButton(enabled = !suggesting, onClick = {
                            suggesting = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { AssistantClient.suggestSpeakingTopic(vm.assistantConfig) } }
                                    .onSuccess(onTopic).onFailure { vm.showMessage(it.message ?: "Konu alınamadı") }
                                suggesting = false
                            }
                        }) {
                            Icon(SouIcons.StarShine, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (suggesting) "…" else "Asistan önersin", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Text("Konuşma süresi", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { m -> FilterChip(selected = minutes == m, onClick = { onMinutes(m) }, label = { Text("$m dk") }) }
                }
                Text("Hazırlık", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Yok", 30 to "30 sn", 60 to "1 dk").forEach { (s, l) -> FilterChip(selected = prep == s, onClick = { onPrep(s) }, label = { Text(l) }) }
                }
                if (!vm.assistantConfig.ready) {
                    Text(
                        "Asistan kurulu değilse sadece konuşman yazıya dökülür; puan için Ayarlar > Asistan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onStart, enabled = topic.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(SouIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Başla", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (attempts.isNotEmpty()) {
            item { SectionHeader("Denemelerin") }
            items(attempts, key = { it.id }) { a ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().card().clickable { onOpen(a) }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(a.feedback?.total?.toString() ?: "–", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(a.topic, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fmt.format(Date(a.created))} · ${a.words} kelime · ${a.wpm} kelime/dk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(a) }) { Icon(SouIcons.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun PrepStage(topic: String, seconds: Int, onDone: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        onDone()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text("Hazırlan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(topic, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(left / seconds.toFloat().coerceAtLeast(1f), size = 140.dp, stroke = 10.dp, showLabel = false)
            Text("$left", fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Text("Fikirlerini sırala: giriş, iki neden ve örnek, sonuç.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onDone) { Text("Hemen başla") }
    }
}

/** Konuşma: SpeechRecognizer her duraklamada sonuç verip durur; süre bitene kadar yeniden başlatılır. */
@Composable
private fun SpeakStage(topic: String, totalSeconds: Int, onError: (String) -> Unit, onFinish: (String, Int) -> Unit) {
    val context = LocalContext.current
    var committed by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(0f) }
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
    }

    fun finish() {
        if (!running) return
        running = false
        runCatching { recognizer.stopListening() }
        val text = (committed + " " + partial).trim()
        onFinish(text, elapsed.coerceAtLeast(1))
    }

    DisposableEffect(Unit) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (!running) return
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Mikrofon izni yok")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        onError("İngilizce konuşma tanıma bu cihazda yok (Google uygulaması > dil ekle)")
                    else -> {
                        // Sessizlik, eşleşme yok, ağ: aynı oturumda dinlemeyi sürdür
                        if (partial.isNotBlank()) committed = (committed + " " + partial).trim()
                        partial = ""
                        runCatching { recognizer.cancel() }
                        handler.postDelayed({ if (running) runCatching { recognizer.startListening(intent) } }, 250)
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                committed = (committed + " " + t.ifBlank { partial }).trim()
                partial = ""
                if (running) runCatching { recognizer.startListening(intent) }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { recognizer.startListening(intent) }.onFailure { onError("Konuşma tanıma başlatılamadı") }
        onDispose {
            running = false
            handler.removeCallbacksAndMessages(null)
            runCatching { recognizer.destroy() }
        }
    }
    LaunchedEffect(Unit) {
        while (running && elapsed < totalSeconds) {
            delay(1000)
            elapsed++
        }
        // Son kelimeler tanınsın diye kısa bekleme
        if (running) {
            runCatching { recognizer.stopListening() }
            delay(1200)
            finish()
        }
    }
    LaunchedEffect(committed, partial) { scroll.animateScrollTo(scroll.maxValue) }

    val pulse by animateFloatAsState(1f + level * 0.35f, label = "mic")
    val left = (totalSeconds - elapsed).coerceAtLeast(0)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
    ) {
        Text(topic, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Box(Modifier.size(110.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) { Icon(SouIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp)) }
        }
        Text("%d:%02d".format(left / 60, left % 60), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        ProgressBar(elapsed / totalSeconds.toFloat(), Modifier.fillMaxWidth(0.7f))
        Column(
            Modifier.weight(1f).fillMaxWidth().card().padding(16.dp).verticalScroll(scroll),
        ) {
            if (committed.isBlank() && partial.isBlank()) {
                Text("Konuşmaya başla; söylediklerin burada yazıya dökülür.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(committed, style = MaterialTheme.typography.bodyLarge)
                if (partial.isNotBlank()) Text(partial, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
            Icon(SouIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Bitir", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun SpeakingResult(a: SpeakingAttempt, error: String?, onAgain: () -> Unit, onSaveNote: () -> Unit) {
    val sou = LocalSouColors.current
    var showImproved by remember { mutableStateOf(false) }
    val fb = a.feedback
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(16.dp)) {
                if (fb != null) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(fb.total / 100f, size = 96.dp, stroke = 9.dp, color = if (fb.total >= 70) sou.success else if (fb.total >= 50) sou.warning else sou.danger, showLabel = false)
                        Text("${fb.total}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = if (fb != null) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.topic, style = MaterialTheme.typography.titleSmall)
                    fb?.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(SouIcons.Timer, "%d:%02d".format(a.seconds / 60, a.seconds % 60), "süre", Modifier.weight(1f))
                StatTile(SouIcons.TextFields, "${a.words}", "kelime", Modifier.weight(1f))
                StatTile(SouIcons.GraphicEq, "${a.wpm}", "kelime/dk", Modifier.weight(1f))
            }
        }
        if (fb == null) {
            item {
                Text(
                    error ?: if (a.words < 5) "Konuşma algılanmadı; mikrofona yakın ve net konuş." else "Puan için Ayarlar > Asistan'dan asistanı kur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fb.criteria.forEachIndexed { i, c -> CriterionRow(c.name, c.score, c.max, c.comment, i) }
                    Text("Telaffuz değerlendirilmez; konuşma tanıma bazı kelimeleri yanlış yazmış olabilir.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (fb.strengths.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("İyi yaptıkların", style = MaterialTheme.typography.titleSmall)
                        fb.strengths.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (fb.mistakes.isNotEmpty()) {
                item { Text("Düzeltmeler (${fb.mistakes.size})", style = MaterialTheme.typography.titleSmall) }
                items(fb.mistakes) { m ->
                    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.wrong, color = sou.danger, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodyMedium)
                        Text("→ ${m.right}", color = sou.success, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        if (m.why.isNotBlank()) Text(m.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (fb.improved.isNotBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Örnek cevap", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showImproved = !showImproved }) { Text(if (showImproved) "Gizle" else "Göster") }
                        }
                        if (showImproved) Text(fb.improved, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Senin konuşman", style = MaterialTheme.typography.titleSmall)
                Text(a.transcript.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAgain) { Text("Yeni deneme") }
                OutlinedButton(onClick = onSaveNote) { Text("Notlara kaydet") }
            }
        }
    }
}
