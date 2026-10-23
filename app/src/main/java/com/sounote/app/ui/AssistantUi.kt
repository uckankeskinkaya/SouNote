package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
    data class Document(val doc: DocRef, val page: Int, val pageCount: Int) : AssistantSource
    data class Note(val title: String, val text: String) : AssistantSource
}

/** Konuşmalar uygulama açık kaldıkça (kaynağa göre) saklanır. */
private val histories = HashMap<String, List<ChatMessage>>()

private fun AssistantSource.key() = when (this) {
    AssistantSource.General -> "general"
    is AssistantSource.Document -> "doc:" + doc.id
    is AssistantSource.Note -> "note:" + title
}

/** Yardımcı asistan: soru sor, belgeyi/notu özetlet, alıştırma iste, yazını kontrol ettir. */
@Composable
fun AssistantDialog(vm: LibraryViewModel, source: AssistantSource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(histories[source.key()].orEmpty()) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Belgeden açıldıysa: 0 = bu sayfa, 1 = tüm belge, 2 = belgesiz
    var docScope by remember { mutableStateOf(0) }
    val docTexts = remember { HashMap<Int, String>() }
    var setup by remember { mutableStateOf(!vm.assistantConfig.ready) }
    // Servis kullanım şartlarının onayını istiyorsa: metni ve sürümü
    var terms by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0) }

    fun ask() {
        busy = true
        error = null
        scope.launch {
            val result = runCatching {
                val contextText = when (source) {
                    is AssistantSource.Document -> if (docScope == 2) {
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                    Column(Modifier.weight(1f)) {
                        Text("🤖 SouNote Asistan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (source) {
                                is AssistantSource.Document -> source.doc.title
                                is AssistantSource.Note -> source.title.ifBlank { "Yazılı not" }
                                AssistantSource.General -> "İngilizce, dersler ve uygulama hakkında sor"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (messages.isNotEmpty()) {
                        TextButton(onClick = {
                            messages.clear()
                            histories.remove(source.key())
                        }) { Text("Yeni sohbet") }
                    }
                    TextButton(onClick = { setup = !setup }) { Text("⚙️") }
                }
                if (setup) {
                    AssistantSetup(vm.assistantConfig, onSave = {
                        vm.updateAssistantConfig(it)
                        setup = false
                    })
                }
                if (source is AssistantSource.Document) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    ) {
                        FilterChip(selected = docScope == 0, onClick = { docScope = 0 }, label = { Text("Bu sayfa (${source.page + 1})") })
                        FilterChip(selected = docScope == 1, onClick = { docScope = 1 }, label = { Text("Tüm belge") })
                        FilterChip(selected = docScope == 2, onClick = { docScope = 2 }, label = { Text("Belgesiz") })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                    ) {
                        if (messages.isEmpty()) {
                            item { Welcome(source) { send(it) } }
                        }
                        items(messages) { m -> Bubble(m, vm) }
                        if (busy) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Asistan yazıyor…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        error?.let { e ->
                            item {
                                Text(
                                    "⚠️ $e",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Bir şey sor…") },
                        maxLines = 5,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { send(input) }, enabled = !busy && input.isNotBlank() && vm.assistantConfig.ready) { Text("Gönder") }
                }
            }
        }
    }
}

@Composable
private fun Welcome(source: AssistantSource, onAsk: (String) -> Unit) {
    val prompts = when (source) {
        is AssistantSource.Document -> listOf(
            "Bu sayfayı Türkçe özetle",
            "Buradaki önemli kelimeleri anlamlarıyla listele",
            "Bu sayfadan bana 5 soru sor",
            "Buradaki grammar konusunu örneklerle anlat",
        )
        is AssistantSource.Note -> listOf(
            "Bu notu toparla ve özetle",
            "Yazımdaki hataları düzelt",
            "Bu essay'i L4 kriterlerine göre değerlendir",
        )
        AssistantSource.General -> listOf(
            "Sıradaki sınavıma ne çalışmalıyım?",
            "Present perfect ile past simple farkı ne?",
            "Bana opinion essay için bir plan yap",
            "Uygulamada ses kaydı nasıl kullanılır?",
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        Text("Merhaba! 👋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "İngilizce dersleri, L4 sınavları ve uygulama hakkında sorabilirsin. Belgeden açtıysan belgenin metnini de okuyorum.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        prompts.forEach { p -> AssistChip(onClick = { onAsk(p) }, label = { Text(p) }) }
        Text(
            "Not: Yapay zekâ hata yapabilir; önemli bilgileri ders materyalinden kontrol et.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage, vm: LibraryViewModel) {
    val context = LocalContext.current
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else cardBackground())
                .padding(12.dp),
        ) {
            SelectionContainer { Text(m.content, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp) }
            if (!mine) {
                Row {
                    TextButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("SouNote Asistan", m.content))
                        vm.showMessage("Kopyalandı")
                    }) { Text("Kopyala", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = {
                        vm.saveTextNote(java.util.UUID.randomUUID().toString(), "Asistan notu", m.content)
                        vm.showMessage("Yazılı notlara kaydedildi")
                    }) { Text("Notlara kaydet", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/** Servis ayarları: OpenAI uyumlu adres, model ve anahtar (sadece bu cihazda saklanır). */
@Composable
internal fun AssistantSetup(config: AssistantConfig, onSave: (AssistantConfig) -> Unit) {
    var url by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var key by remember { mutableStateOf(config.apiKey) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .card()
            .padding(16.dp),
    ) {
        Text("Asistan ayarları", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "OpenAI uyumlu bir sohbet servisi gerekir. Evren anahtarı (evren_llm_…) için \"Evren\" düğmesine bas, anahtarı yapıştır, kaydet. " +
                "Anahtar sadece bu cihazda saklanır, uygulamayla birlikte paylaşılmaz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("API adresi") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = model, onValueChange = { model = it }, singleLine = true, label = { Text("Model adı") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            label = { Text("API anahtarı") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                url = AssistantConfig.EVREN_URL
                model = AssistantConfig.EVREN_MODEL
            }) { Text("Evren") }
            TextButton(onClick = {
                url = AssistantConfig.DEEPSEEK_URL
                model = AssistantConfig.DEEPSEEK_MODEL
            }) { Text("DeepSeek") }
            Button(onClick = { onSave(AssistantConfig(url, model, key).fixed()) }, enabled = url.isNotBlank() && model.isNotBlank() && key.isNotBlank()) {
                Text("Kaydet")
            }
        }
    }
}
