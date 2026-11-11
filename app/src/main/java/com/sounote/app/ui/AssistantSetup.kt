package com.sounote.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.data.AssistantClient
import com.sounote.app.data.AssistantConfig
import com.sounote.app.data.ChatMessage
import com.sounote.app.data.TermsNotAcceptedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EVREN_KEYS_PAGE = "https://evren.ssyz.org.tr/api-keys"

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Done(val ok: Boolean, val text: String) : TestState
}

/**
 * Asistan kurulumu: Evren anahtarını adım adım aldıran rehber + "Kaydet ve dene".
 * Başka bir OpenAI uyumlu servis "Gelişmiş" altında. Anahtar sadece bu cihazda saklanır.
 */
@Composable
internal fun AssistantSetup(config: AssistantConfig, onSave: (AssistantConfig) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var key by remember { mutableStateOf(config.apiKey) }
    var advanced by remember { mutableStateOf(!config.baseUrl.contains("ssyz.org.tr")) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    val trimmed = key.trim()
    val isEvren = url.contains("ssyz.org.tr")
    val keyHint = when {
        trimmed.isEmpty() -> null
        trimmed.startsWith("evren_llm_") -> "Evren anahtarı" to true
        isEvren -> "Evren anahtarı \"evren_llm_\" ile başlar. Tamamını kopyaladığından emin ol." to false
        else -> null
    }

    fun current() = AssistantConfig(url, model, key).fixed()

    fun saveAndTest() {
        val c = current()
        onSaveSilently(context, c)
        test = TestState.Running
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AssistantClient.chat(c, listOf(ChatMessage("user", "Reply with only: OK"))) }
            }
            test = result.fold(
                onSuccess = { TestState.Done(true, "Asistan hazır. Artık soru sorabilirsin.") },
                onFailure = { e ->
                    when (e) {
                        is TermsNotAcceptedException -> TestState.Done(
                            true,
                            "Anahtar doğru. Son adım: asistana ilk soruyu sorunca Evren'in kullanım şartları çıkacak, okuyup onayla.",
                        )
                        else -> TestState.Done(false, e.message ?: "Bağlanılamadı. İnternetini kontrol et.")
                    }
                },
            )
            if ((test as? TestState.Done)?.ok == true) {
                delay(1800) // sonucu okuyabilsin
                onSave(c)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .card()
            .padding(16.dp),
    ) {
        Text("Asistanı kur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Asistan, devletin ücretsiz yapay zekâ platformu Evren ile çalışır. e-Devlet şifren yeterli, yaklaşık 2 dakika sürer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!advanced) {
            SetupStep(1, "Evren'e e-Devlet ile gir", "Aşağıdaki düğme Evren'in API Anahtarları sayfasını açar. \"e-Devlet ile Giriş\"e bas, şifrenle gir.") {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EVREN_KEYS_PAGE))) }
                }) { Text("Evren'i aç ↗") }
            }
            SetupStep(2, "Anahtar oluştur ve kopyala", "API Anahtarları sayfasında yeni anahtar oluştur. \"evren_llm_\" ile başlayan anahtarın tamamını kopyala.")
            SetupStep(3, "Buraya yapıştır", null) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; test = TestState.Idle },
                    singleLine = true,
                    label = { Text("evren_llm_…") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                            ?.let { key = it; test = TestState.Idle }
                    }) { Text("Panodan yapıştır") }
                    keyHint?.let { (text, ok) ->
                        Text(
                            text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ok) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            SetupStep(4, "Kaydet ve dene", "Anahtarın çalışıp çalışmadığını hemen denerim.")
        } else {
            OutlinedTextField(value = url, onValueChange = { url = it; test = TestState.Idle }, singleLine = true, label = { Text("API adresi") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it; test = TestState.Idle }, singleLine = true, label = { Text("Model adı") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; test = TestState.Idle },
                singleLine = true,
                label = { Text("API anahtarı") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Servis:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { url = AssistantConfig.EVREN_URL; model = AssistantConfig.EVREN_MODEL }) { Text("Evren") }
                TextButton(onClick = { url = AssistantConfig.DEEPSEEK_URL; model = AssistantConfig.DEEPSEEK_MODEL }) { Text("DeepSeek API") }
            }
            // Model seçimi: anahtar girilince servisin modelleri gelir, aileye basınca o ailenin en uygun modeli seçilir
            if (trimmed.isNotBlank() && url.isNotBlank()) {
                ModelPicker(current = model, url = url, key = trimmed, config = { current() }, onPick = { model = it; test = TestState.Idle })
            }
        }

        when (val t = test) {
            TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Deneniyor…", style = MaterialTheme.typography.bodySmall)
            }
            is TestState.Done -> Text(
                t.text,
                style = MaterialTheme.typography.bodySmall,
                color = if (t.ok) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (t.ok) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.error).copy(alpha = 0.1f))
                    .padding(10.dp),
            )
            TestState.Idle -> Unit
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                advanced = !advanced
                if (!advanced) { url = AssistantConfig.EVREN_URL; model = AssistantConfig.EVREN_MODEL }
            }) { Text(if (advanced) "Evren rehberi" else "Gelişmiş", maxLines = 1, softWrap = false) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { saveAndTest() },
                enabled = test != TestState.Running && url.isNotBlank() && model.isNotBlank() && trimmed.isNotBlank(),
            ) { Text("Kaydet ve dene", maxLines = 1, softWrap = false) }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp),
        ) {
            // Evren ücretsiz: kullanım kredisi e-Devlet hesabına kendiliğinden tanımlanır, satın alınmaz
            InfoLine(SouIcons.Info, "Evren ücretsiz. Kullanım kredin e-Devlet hesabına kendiliğinden tanımlanır; bir şey satın alman gerekmez.")
            InfoLine(SouIcons.BarChart, "Kredin bitince yenilenmesini beklemen gerekir.")
            InfoLine(SouIcons.Lock, "Anahtarın sadece bu cihazda saklanır. Kimseyle paylaşma.")
            InfoLine(SouIcons.Person, "e-Devlet şifren yoksa asistan dışında her şey çalışır.")
        }
    }
}

/** Test başarısız olsa da yazılanlar kaybolmasın: ayarları sessizce kaydeder. */
private fun onSaveSilently(context: Context, c: AssistantConfig) = AssistantConfig.save(context, c)

@Composable
private fun SetupStep(n: Int, title: String, body: String?, content: (@Composable () -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        ) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content?.invoke()
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Model aileleri: düğmeye basınca listede o aileden en uygun model seçilir. */
private val MODEL_FAMILIES = listOf(
    "DeepSeek" to "deepseek", "GLM" to "glm", "Qwen" to "qwen", "Llama" to "llama", "Gemma" to "gemma",
    "GPT-OSS" to "gpt-oss", "Mistral" to "mistral", "Kimi" to "kimi", "Phi" to "phi",
)

/**
 * Model seçimi (Gelişmiş): anahtarla servisten model listesini çeker. Ailelerin (DeepSeek, GLM, Qwen…) düğmesine basınca
 * o ailenin en uygun modeli seçilir; "Otomatik" servisin kendi seçimi ("auto"). Tek tek modeller "Tüm modeller" altında.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(current: String, url: String, key: String, config: () -> AssistantConfig, onPick: (String) -> Unit) {
    var models by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    // Anahtar ya da adres değişince listeyi kendiliğinden getir (yazarken her harfte istek atmasın diye kısa bekleme)
    LaunchedEffect(url, key, reload) {
        delay(600)
        loading = true
        error = null
        val r = withContext(Dispatchers.IO) { runCatching { AssistantClient.models(config()) } }
        loading = false
        r.onSuccess { list ->
            models = list
            if (list.isEmpty()) error = "Servis model listesi vermedi; model adını yukarıya elle yazabilirsin"
        }.onFailure {
            models = null
            error = it.message ?: "Model listesi alınamadı"
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Model seç", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (current == AssistantConfig.EVREN_MODEL) "Otomatik (servis seçer)" else current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else TextButton(onClick = { reload++ }) { Text("Yenile") }
        }
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        val list = models.orEmpty()
        if (list.isNotEmpty()) {
            val best = AssistantClient.recommendedModel(list)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = current == AssistantConfig.EVREN_MODEL,
                    onClick = { onPick(AssistantConfig.EVREN_MODEL) },
                    label = { Text("Otomatik") },
                )
                if (best != null) {
                    FilterChip(selected = current == best, onClick = { onPick(best) }, label = { Text("Önerilen") })
                }
                MODEL_FAMILIES.forEach { (label, tag) ->
                    val family = list.filter { tag in it.lowercase() }
                    if (family.isNotEmpty()) {
                        FilterChip(
                            selected = tag in current.lowercase(),
                            onClick = { onPick(family.maxBy { AssistantClient.modelScore(it) }) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Listeyi gizle" else "Tüm modeller (${list.size})") }
            if (showAll) {
                list.sortedByDescending { AssistantClient.modelScore(it) }.forEach { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(id) },
                    ) {
                        RadioButton(selected = id == current, onClick = { onPick(id) })
                        Text(id, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                        if (id == best) Text("Önerilen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "Seçince \"Kaydet ve dene\" de. Bir model kötü cevap verirse başka aileyi dene.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
