package com.sounote.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import java.time.LocalDate

private const val EVREN_KEYS_PAGE = "https://evren.ssyz.org.tr/api-keys"

/** Evren'in ücretsiz dönemi (kullanım şartlarına göre 1 Kasım 2026'ya kadar). */
private val EVREN_FREE_UNTIL: LocalDate = LocalDate.of(2026, 11, 1)

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
        trimmed.startsWith("evren_llm_") -> "✓ Evren anahtarı" to true
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
                onSuccess = { TestState.Done(true, "Asistan hazır 🎉 Artık soru sorabilirsin.") },
                onFailure = { e ->
                    when (e) {
                        is TermsNotAcceptedException -> TestState.Done(
                            true,
                            "Anahtar doğru ✓ Son adım: asistana ilk soruyu sorunca Evren'in kullanım şartları çıkacak, okuyup onayla.",
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
                    }) { Text("📋 Panodan yapıştır") }
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
            Row {
                TextButton(onClick = { url = AssistantConfig.EVREN_URL; model = AssistantConfig.EVREN_MODEL }) { Text("Evren") }
                TextButton(onClick = { url = AssistantConfig.DEEPSEEK_URL; model = AssistantConfig.DEEPSEEK_MODEL }) { Text("DeepSeek") }
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
            val free = LocalDate.now().isBefore(EVREN_FREE_UNTIL)
            InfoLine(
                "💸",
                if (free) "Evren 1 Kasım 2026'ya kadar ücretsiz. Sonrası için Evren'in duyurularına bak."
                else "Ücret ve kota bilgisi için Evren sitesine bak; ücretsiz dönem 1 Kasım 2026'da bitti.",
            )
            InfoLine("📊", "Günlük kullanım sınırı var; dolarsa ertesi gün yenilenir.")
            InfoLine("🔒", "Anahtarın sadece bu cihazda saklanır ve sadece Evren'e gönderilir. Kimseyle paylaşma.")
            InfoLine("🙂", "e-Devlet şifren yoksa sorun değil: asistan dışında uygulamanın her şeyi çalışır.")
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
private fun InfoLine(emoji: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(emoji, fontSize = 14.sp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
