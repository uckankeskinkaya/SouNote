package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.AssistantClient
import com.sounote.app.data.AssistantConfig
import com.sounote.app.data.ChatMessage
import com.sounote.app.data.DocRef
import com.sounote.app.data.TermsNotAcceptedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asistanın nereden açıldığı: belge (sayfasıyla), yazılı not ya da genel. */
sealed interface AssistantSource {
    data object General : AssistantSource
    /** [selection]: lasso ile seçilen bölgenin metni (varsa "Seçim" bağlamı açılır). */
    data class Document(val doc: DocRef, val page: Int, val pageCount: Int, val selection: String? = null) : AssistantSource
    data class Note(val title: String, val text: String) : AssistantSource
    /** Bir dersin kütüphanedeki (indirilmiş) PDF'leri: ders özeti, kelimeler, sorular. */
    data class Course(val name: String, val docs: List<DocRef>) : AssistantSource
}

/** Konuşmalar uygulama açık kaldıkça (kaynağa göre) saklanır. */
private val histories = HashMap<String, List<ChatMessage>>()

/** Ders özeti bağlamının önbellek anahtarı (docTexts içinde). */
private const val COURSE_TEXT = 100

private fun AssistantSource.key() = when (this) {
    AssistantSource.General -> "general"
    is AssistantSource.Document -> "doc:" + doc.id
    is AssistantSource.Note -> "note:" + title
    is AssistantSource.Course -> "course:" + name
}

/** Yardımcı asistan: soru sor, belgeyi/notu özetlet, alıştırma iste, yazını kontrol ettir. */
@Composable
fun AssistantDialog(vm: LibraryViewModel, source: AssistantSource, prefill: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(histories[source.key()].orEmpty()) } }
    var input by remember { mutableStateOf(prefill) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Belgeden açıldıysa: 0 = bu sayfa, 1 = tüm belge, 2 = belgesiz, 3 = seçili bölge
    val selection = (source as? AssistantSource.Document)?.selection?.takeIf { it.isNotBlank() }
    var docScope by remember { mutableStateOf(if (selection != null) 3 else 0) }
    val docTexts = remember { HashMap<Int, String>() }
    var setup by remember { mutableStateOf(!vm.assistantConfig.ready) }
    // Servis kullanım şartlarının onayını istiyorsa: metni ve sürümü
    var terms by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0) }

    // Alıştırma ekranı (kaynak: belgede seçili bağlam, not ya da ders)
    var exerciseOpen by remember { mutableStateOf(false) }
    if (exerciseOpen) {
        when (source) {
            is AssistantSource.Document -> {
                val sc = if (docScope >= 2) 0 else docScope
                ExerciseScreen(
                    vm,
                    sourceTitle = source.doc.title + if (docScope == 3) " (seçim)" else if (sc == 0) " (sayfa ${source.page + 1})" else "",
                    loadSource = {
                        val text = (if (docScope == 3) selection else null) ?: docTexts[sc] ?: run {
                            val (from, to) = if (sc == 0) source.page to source.page else 0 to source.pageCount - 1
                            vm.documentText(source.doc, from, to).also { docTexts[sc] = it }
                        }
                        if (text.isBlank()) throw java.io.IOException("Bu sayfada okunabilir metin yok (taranmış görüntü olabilir)")
                        text
                    },
                ) { exerciseOpen = false }
            }
            is AssistantSource.Note -> ExerciseScreen(vm, sourceTitle = source.title, loadSource = { source.text.take(14_000) }) { exerciseOpen = false }
            is AssistantSource.Course -> ExerciseScreen(vm, course = vm.catalog.courses.find { it.name.equals(source.name, ignoreCase = true) }, sourceTitle = source.name) {
                exerciseOpen = false
            }
            AssistantSource.General -> ExerciseScreen(vm) { exerciseOpen = false }
        }
    }

    // Kart üretme: önizlenen soru-cevaplar (null = pencere kapalı)
    var cardDraft by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var makingCards by remember { mutableStateOf(false) }

    fun makeCards() {
        val doc = source as? AssistantSource.Document ?: return
        if (makingCards || busy) return
        makingCards = true
        error = null
        // "Belgesiz" ya da "Seçim" seçiliyken: seçim varsa ondan, yoksa bu sayfadan üret.
        val sc = if (docScope >= 2) 0 else docScope
        scope.launch {
            val r = runCatching {
                val text = (if (docScope == 3) selection else null) ?: docTexts[sc] ?: run {
                    val (from, to) = if (sc == 0) doc.page to doc.page else 0 to doc.pageCount - 1
                    vm.documentText(doc.doc, from, to).also { docTexts[sc] = it }
                }
                if (text.isBlank()) throw java.io.IOException("Bu sayfada okunabilir metin yok (taranmış görüntü olabilir)")
                withContext(Dispatchers.IO) { AssistantClient.generateCards(vm.assistantConfig, text) }
            }
            makingCards = false
            r.onSuccess { cardDraft = it }.onFailure {
                ErrorLog.record("Asistan kart", it)
                error = it.message ?: "Kartlar üretilemedi"
            }
        }
    }

    fun ask() {
        busy = true
        error = null
        scope.launch {
            val result = runCatching {
                val contextText = when (source) {
                    is AssistantSource.Document -> if (docScope == 3 && selection != null) {
                        "Kullanıcı \"${source.doc.title}\" belgesinin ${source.page + 1}. sayfasında bir bölge seçti. Seçili metin:\n\"\"\"\n" +
                            selection.take(8_000) + "\n\"\"\""
                    } else if (docScope == 2) {
                        null
                    } else {
                        docTexts[docScope] ?: run {
                            val (from, to) = if (docScope == 0) source.page to source.page else 0 to source.pageCount - 1
                            vm.documentText(source.doc, from, to).also { docTexts[docScope] = it }
                        }
                    }?.let { t ->
                        val where = if (docScope == 0) "sayfa ${source.page + 1}" else "tüm belge"
                        "Kullanıcının açık belgesi: \"${source.doc.title}\" ($where). Belgenin metni:\n\"\"\"\n" +
                            t.ifBlank { "(Bu sayfada okunabilir metin yok; taranmış görüntü olabilir.)" } + "\n\"\"\""
                    }
                    is AssistantSource.Note -> "Kullanıcının açık yazılı notu: \"${source.title}\". Notun metni:\n\"\"\"\n${source.text.take(12_000)}\n\"\"\""
                    is AssistantSource.Course -> docTexts[COURSE_TEXT] ?: run {
                        // Her belgeden eşit pay: toplam ~16 bin karakter
                        val per = (16_000 / source.docs.size.coerceAtLeast(1)).coerceAtLeast(1_500)
                        val parts = ArrayList<String>()
                        for (d in source.docs.take(12)) {
                            val t = try { vm.documentText(d, 0, 9_999) } catch (e: Exception) { "" }
                            parts += "### ${d.title}\n" + t.take(per)
                        }
                        parts.joinToString("\n\n").let { all ->
                            "Kullanıcının \"${source.name}\" dersinin kütüphanedeki belgeleri (${source.docs.size} belge). Metinleri:\n\"\"\"\n$all\n\"\"\""
                        }.also { docTexts[COURSE_TEXT] = it }
                    }
                    AssistantSource.General -> null
                }
                val system = AssistantClient.systemPrompt(vm.catalog, vm.userName) + (contextText?.let { "\n\n$it" } ?: "")
                val history = messages.takeLast(12)
                withContext(Dispatchers.IO) {
                    AssistantClient.chat(vm.assistantConfig, listOf(ChatMessage("system", system)) + history)
                }
            }
            busy = false
            result.onSuccess {
                messages += ChatMessage("assistant", it.ifBlank { "(boş cevap)" })
                histories[source.key()] = messages.toList()
            }.onFailure {
                if (it is TermsNotAcceptedException) {
                    runCatching { withContext(Dispatchers.IO) { AssistantClient.termsText(vm.assistantConfig) } }
                        .onSuccess { t -> terms = t }
                        .onFailure { e -> error = "Servisin kullanım şartlarını onaylaman gerekiyor ama metin alınamadı: ${e.message}" }
                    return@onFailure
                }
                ErrorLog.record("Asistan", it)
                error = it.message ?: "Bağlantı hatası"
            }
        }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        input = ""
        error = null
        messages += ChatMessage("user", q)
        histories[source.key()] = messages.toList()
        ask()
    }

    cardDraft?.let { draft ->
        val doc = (source as AssistantSource.Document)
        val picked = remember(draft) { mutableStateListOf<Boolean>().apply { repeat(draft.size) { add(true) } } }
        AlertDialog(
            onDismissRequest = { cardDraft = null },
            title = { Text("${draft.size} bilgi kartı") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "İstemediklerinin işaretini kaldır. Kartlar bu belgenin bilgi kartlarına eklenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    draft.forEachIndexed { i, (q, a) ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { picked[i] = !picked[i] }
                                .background(if (picked[i]) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                .padding(8.dp),
                        ) {
                            Checkbox(checked = picked[i], onCheckedChange = { picked[i] = it })
                            Column(Modifier.padding(start = 4.dp, top = 2.dp)) {
                                Text(q, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val n = picked.count { it }
                Button(enabled = n > 0, onClick = {
                    vm.addGeneratedCards(doc.doc, draft.filterIndexed { i, _ -> picked[i] }, if (docScope == 1) -1 else doc.page)
                    cardDraft = null
                }) { Text("Ekle ($n)") }
            },
            dismissButton = { TextButton(onClick = { cardDraft = null }) { Text("Vazgeç") } },
        )
    }

    terms?.let { (text, version) ->
        AlertDialog(
            onDismissRequest = { terms = null },
            title = { Text("Kullanım şartları") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Asistan servisi ilk kullanımdan önce şartlarını onaylamanı istiyor. Okuyup kabul edersen bir daha sorulmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    terms = null
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { AssistantClient.acceptTerms(vm.assistantConfig, version) } }
                            .onSuccess { if (messages.lastOrNull()?.role == "user") ask() else busy = false }
                            .onFailure {
                                busy = false
                                ErrorLog.record("Asistan şartlar", it)
                                error = it.message
                            }
                    }
                }) { Text("Kabul ediyorum") }
            },
            dismissButton = { TextButton(onClick = { terms = null }) { Text("Vazgeç") } },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().souBackground(), color = Color.Transparent) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Üst çubuk: geri, asistan kimliği, yeni sohbet, ayarlar
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(SouIcons.ArrowBack, contentDescription = "Kapat") }
                    BotAvatar(40.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("SouNote Asistan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            when {
                                busy -> "yazıyor…"
                                !vm.assistantConfig.ready -> "Kurulum gerekli"
                                source is AssistantSource.Document -> source.doc.title
                                source is AssistantSource.Note -> source.title.ifBlank { "Yazılı not" }
                                source is AssistantSource.Course -> "${source.name} · ${source.docs.size} belge"
                                else -> "İngilizce, L4 dersleri ve uygulama"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = {
                            messages.clear()
                            histories.remove(source.key())
                            error = null
                        }) { Icon(SouIcons.Refresh, contentDescription = "Yeni sohbet") }
                    }
                    IconButton(onClick = { setup = !setup }) { Icon(SouIcons.Settings, contentDescription = "Asistan ayarları") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (setup) {
                    AssistantSetup(vm.assistantConfig, onSave = {
                        vm.updateAssistantConfig(it)
                        setup = false
                    })
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
                    ) {
                        if (messages.isEmpty()) {
                            item { Welcome(source, vm.userName, vm.assistantConfig.ready, onSetup = { setup = true }) { send(it) } }
                        }
                        items(messages) { m -> Box(Modifier.appear()) { Bubble(m, vm) } }
                        if (busy) item { Box(Modifier.appear()) { TypingBubble() } }
                        error?.let { e ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                ) {
                                    Text(
                                        e,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                    )
                                    if (messages.lastOrNull()?.role == "user") {
                                        TextButton(onClick = { ask() }) { Text("Tekrar dene") }
                                    }
                                }
                            }
                        }
                    }
                }
                // Alt kısım: belge kapsamı + yazma alanı
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(cardBackground())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
                        if (source is AssistantSource.Document) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                            ) {
                                Text("Bağlam:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selection != null) {
                                    FilterChip(selected = docScope == 3, onClick = { docScope = 3 }, label = { Text("Seçili bölge") })
                                }
                                FilterChip(selected = docScope == 0, onClick = { docScope = 0 }, label = { Text("Bu sayfa (${source.page + 1})") })
                                FilterChip(selected = docScope == 1, onClick = { docScope = 1 }, label = { Text("Tüm belge") })
                                FilterChip(selected = docScope == 2, onClick = { docScope = 2 }, label = { Text("Belgesiz") })
                                AssistChip(
                                    onClick = { makeCards() },
                                    enabled = vm.assistantConfig.ready && !makingCards && !busy,
                                    leadingIcon = { Icon(SouIcons.Style, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    label = { Text(if (makingCards) "Kartlar hazırlanıyor…" else "Kart üret") },
                                )
                                AssistChip(
                                    onClick = { exerciseOpen = true },
                                    enabled = vm.assistantConfig.ready && !busy,
                                    leadingIcon = { Icon(SouIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    label = { Text("Alıştırma") },
                                )
                            }
                        } else if (source is AssistantSource.Note || source is AssistantSource.Course) {
                            Row(Modifier.padding(bottom = 6.dp)) {
                                AssistChip(
                                    onClick = { exerciseOpen = true },
                                    enabled = vm.assistantConfig.ready && !busy,
                                    leadingIcon = { Icon(SouIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    label = { Text("Alıştırma hazırla") },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = { Text(if (vm.assistantConfig.ready) "Mesaj yaz…" else "Önce asistanı kur") },
                                maxLines = 6,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            val canSend = !busy && input.isNotBlank() && vm.assistantConfig.ready
                            FilledIconButton(
                                onClick = { send(input) },
                                enabled = canSend,
                                modifier = Modifier.size(52.dp),
                            ) { Icon(SouIcons.Send, contentDescription = "Gönder") }
                        }
                    }
                }
            }
        }
    }
}

/** Asistanın simgesi: tema renginde daire içinde yıldız. */
@Composable
private fun BotAvatar(size: Dp) {
    val hero = listOf(MaterialTheme.colorScheme.primary, LocalSouColors.current.hero.last())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(hero)),
    ) {
        Icon(SouIcons.StarShine, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun Welcome(source: AssistantSource, userName: String, ready: Boolean, onSetup: () -> Unit, onAsk: (String) -> Unit) {
    val prompts = when (source) {
        is AssistantSource.Document -> if (source.selection.isNullOrBlank()) listOf(
            SouIcons.EditNote to "Bu sayfayı Türkçe özetle",
            SouIcons.LibraryBooks to "Buradaki önemli kelimeleri anlamlarıyla listele",
            SouIcons.Quiz to "Bu sayfadan bana 5 soru sor",
            SouIcons.Extension to "Buradaki grammar konusunu örneklerle anlat",
        ) else listOf(
            SouIcons.Lightbulb to "Seçili metni basitçe açıkla",
            SouIcons.Translate to "Seçili metni Türkçeye çevir",
            SouIcons.LibraryBooks to "Seçili metindeki zor kelimeleri anlamlarıyla listele",
            SouIcons.Quiz to "Seçili metinden bana 3 soru sor",
        )
        is AssistantSource.Course -> listOf(
            SouIcons.Article to "Bu dersin konularını tek sayfada başlıklar ve maddelerle özetle",
            SouIcons.LibraryBooks to "Bu derste geçen önemli kelimeleri anlamlarıyla listele",
            SouIcons.Quiz to "Bu dersten sınav için 10 soru sor (cevaplarıyla)",
            SouIcons.Flag to "Sınava 3 gün kala bu dersi nasıl çalışmalıyım?",
        )
        is AssistantSource.Note -> listOf(
            SouIcons.Quiz to "Bu nottan 5 çalışma sorusu üret (cevaplarıyla)",
            SouIcons.StarShine to "Bu notu toparla ve özetle",
            SouIcons.TaskAlt to "Yazımdaki hataları düzelt",
            SouIcons.Flag to "Bu essay'i L4 kriterlerine göre değerlendir",
        )
        AssistantSource.General -> listOf(
            SouIcons.Event to "Sıradaki sınavıma ne çalışmalıyım?",
            SouIcons.CalendarMonth to "Yaklaşan sınavlarıma göre bana bu hafta için çalışma planı öner",
            SouIcons.Extension to "Present perfect ile past simple farkı ne?",
            SouIcons.Draw to "Bana opinion essay için bir plan yap",
            SouIcons.Mic to "Uygulamada ses kaydı nasıl kullanılır?",
        )
    }
    val name = userName.trim().split(" ").firstOrNull().orEmpty()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    ) {
        BotAvatar(72.dp)
        Text(
            if (name.isNotEmpty()) "Merhaba $name" else "Merhaba",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            when (source) {
                is AssistantSource.Document -> if (source.selection.isNullOrBlank()) {
                    "Bu belgeyi okuyorum. Özet, kelime listesi, soru ya da konu anlatımı isteyebilirsin."
                } else {
                    "Seçtiğin bölgeyi okuyorum. Açıklama, çeviri ya da soru isteyebilirsin."
                }
                is AssistantSource.Note -> "Notunu okuyorum. Düzeltme, özet ya da değerlendirme isteyebilirsin."
                is AssistantSource.Course -> "${source.name} dersinin belgelerini okuyorum. Özet, kelime listesi ya da sınav soruları isteyebilirsin."
                AssistantSource.General -> "İngilizce, L4 dersleri, sınavların ve uygulama hakkında her şeyi sorabilirsin."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (!ready) {
            Button(onClick = onSetup, modifier = Modifier.padding(top = 4.dp)) { Text("Asistanı kur") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            prompts.forEach { (emoji, p) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .card()
                        .clickable(enabled = ready) { onAsk(p) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(emoji, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(p, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(
                        SouIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "Yapay zekâ hata yapabilir; önemli bilgileri ders materyalinden kontrol et.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dots")
    Row(verticalAlignment = Alignment.Top) {
        BotAvatar(28.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(cardBackground())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            repeat(3) { i ->
                val a = if (phase.toInt() == i) 1f else 0.3f
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a)))
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, vm: LibraryViewModel) {
    val context = LocalContext.current
    if (m.role == "user") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            SelectionContainer {
                Text(
                    m.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(start = 48.dp)
                        .widthIn(max = 560.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        BotAvatar(28.dp)
        Column(Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .background(cardBackground())
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer { MarkdownText(m.content, MaterialTheme.colorScheme.onSurface) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                SmallAction("Kopyala") {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("SouNote Asistan", m.content))
                    vm.showMessage("Kopyalandı")
                }
                SmallAction("Notlara kaydet") {
                    vm.saveTextNote(java.util.UUID.randomUUID().toString(), "Asistan notu", m.content)
                    vm.showMessage("Yazılı notlara kaydedildi")
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
