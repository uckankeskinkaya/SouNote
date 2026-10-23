@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sounote.app.BuildConfig
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

/** Arkadaşlara gönderilen indirme bağlantısı: her zaman en yeni sürüm. */
private const val DOWNLOAD_URL = "https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk"

/** Hata raporunu paylaşma penceresi (ayarlar ve çökme sonrası). */
internal fun shareErrorReport(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SouNote hata raporu")
        putExtra(Intent.EXTRA_TEXT, ErrorLog.fullReport(context))
    }
    context.startActivity(Intent.createChooser(send, "Hata raporunu paylaş"))
}

@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::backup)
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val autoBackupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::enableAutoBackup)
    }
    var autoBackupDialog by remember { mutableStateOf(false) }
    var trashDialog by remember { mutableStateOf(false) }
    var statsDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var preparingApk by remember { mutableStateOf(false) }
    var assistantSetup by remember { mutableStateOf(false) }
    val tools = vm.tools

    fun shareLink() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "SouNote: PDF'lerin üstüne kalemle not alma uygulaması.\n" +
                    "İndir (Android): $DOWNLOAD_URL\n" +
                    "Kurarken \"bilinmeyen kaynaklardan yüklemeye izin ver\" demen gerekebilir.",
            )
        }
        context.startActivity(Intent.createChooser(send, "SouNote'u paylaş"))
    }

    fun shareApk() {
        if (preparingApk) return
        preparingApk = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val out = File(dir, "SouNote.apk")
                    File(context.applicationInfo.sourceDir).copyTo(out, overwrite = true)
                }.onFailure { ErrorLog.record("APK hazırlanamadı", it) }.getOrNull()
            }
            preparingApk = false
            if (file == null) {
                vm.showMessage("APK hazırlanamadı")
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "SouNote'u gönder"))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
            ) {
                item {
                    Group("Profil") {
                        SettingRow(
                            "🙂", "Adın",
                            subtitle = vm.userName.ifBlank { "Ana sayfada seni adınla karşılayalım" },
                            onClick = { nameDialog = true },
                        )
                    }
                }
                item {
                    Group("Görünüm") {
                        SettingRow("🎨", "Tema", subtitle = "Açık, koyu ya da sistemle aynı")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
                        ) {
                            LibraryViewModel.ThemeMode.entries.forEach { m ->
                                FilterChip(selected = vm.themeMode == m, onClick = { vm.updateTheme(m) }, label = { Text(m.label) })
                            }
                        }
                        RowDivider()
                        SettingRow("🌈", "Renk", subtitle = vm.colorTheme.label)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 68.dp, end = 16.dp, bottom = 14.dp),
                        ) {
                            ColorTheme.entries.forEach { c ->
                                val selected = vm.colorTheme == c
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(c.swatch)
                                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        .clickable { vm.updateColorTheme(c) },
                                ) {
                                    if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        RowDivider()
                        SwitchRow(
                            "🌙", "Gece okuma",
                            "Belgelerde renkleri ters çevirir, karanlıkta gözü yormaz",
                            vm.nightReading,
                        ) { vm.toggleNightReading() }
                        RowDivider()
                        SwitchRow("📖", "Yatayda çift sayfa", "Tablet yan dururken iki sayfa yan yana", vm.twoPage) {
                            vm.updateTwoPage(it)
                        }
                    }
                }
                item {
                    Group("Kalem") {
                        SwitchRow(
                            "👆", "Parmakla çiz",
                            "Kalemin yoksa aç: tek parmak çizer, iki parmak kaydırır",
                            tools.fingerDraw,
                        ) { vm.updateTools(tools.copy(fingerDraw = it)) }
                        RowDivider()
                        SwitchRow(
                            "⚡", "Düşük gecikme (deneysel)",
                            "Kalem çizgisi ekrana daha hızlı düşer",
                            tools.lowLatency,
                        ) { vm.updateTools(tools.copy(lowLatency = it)) }
                    }
                }
                item {
                    Group("Yapay zekâ asistanı") {
                        SettingRow(
                            "🤖", "Asistan ayarları",
                            subtitle = if (vm.assistantConfig.ready) "Hazır · ${vm.assistantConfig.model}" else "API anahtarı girilmedi",
                            onClick = { assistantSetup = true },
                        )
                    }
                }
                item {
                    Group("Güvenlik") {
                        SwitchRow(
                            "🔒", "Uygulama kilidi",
                            "Açılışta parmak izi, yüz ya da ekran kilidi şifresi ister",
                            vm.appLock,
                        ) { vm.updateAppLock(it) }
                    }
                }
                item {
                    Group("Yedekleme") {
                        SettingRow("💾", "Şimdi yedekle", subtitle = "Bütün belgeler ve notlar tek bir zip dosyasına", onClick = {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                            backupLauncher.launch("SouNote-yedek-$date.zip")
                        })
                        RowDivider()
                        SettingRow("📤", "Drive'a yedekle", subtitle = "Yedeği Google Drive'a (ya da başka bir yere) gönder", onClick = {
                            vm.backupToShare { file ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val drive = Intent(send).setPackage("com.google.android.apps.docs")
                                val target = if (drive.resolveActivity(context.packageManager) != null) drive else Intent.createChooser(send, "Yedeği gönder")
                                runCatching { context.startActivity(target) }.onFailure { context.startActivity(Intent.createChooser(send, "Yedeği gönder")) }
                            }
                        })
                        RowDivider()
                        SettingRow(
                            "☁️", "Otomatik yedek",
                            subtitle = if (vm.autoBackupFolder != null) "Açık · günde bir" else "Kapalı",
                            onClick = { autoBackupDialog = true },
                        )
                        RowDivider()
                        SettingRow("♻️", "Yedekten geri yükle", subtitle = "Başka tabletten ya da eski yedekten", onClick = {
                            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        })
                    }
                }
                item {
                    Group("Veriler") {
                        SettingRow(
                            "🗑️", "Çöp kutusu",
                            subtitle = if (vm.trash.isEmpty()) "Boş" else "${vm.trash.size} belge · ${LibraryViewModel.TRASH_DAYS} gün sonra silinir",
                            onClick = { trashDialog = true },
                        )
                        RowDivider()
                        SettingRow("📊", "Çalışma istatistiği", subtitle = "Bugün, bu hafta, en çok çalışılanlar", onClick = { statsDialog = true })
                    }
                }
                item {
                    Group("Yardım") {
                        SettingRow(
                            "🔄", "Güncellemeleri denetle",
                            subtitle = "Sürüm ${vm.installedVersion}" + (vm.update?.let { " · yeni sürüm var: ${it.name}" } ?: ""),
                            onClick = { vm.checkUpdate(manual = true) },
                        )
                        RowDivider()
                        SettingRow("❓", "Nasıl kullanılır?", subtitle = "Hareketler ve kısayollar", onClick = { helpDialog = true })
                        RowDivider()
                        SettingRow("✨", "Tanıtımı tekrar göster", onClick = { vm.replayOnboarding() })
                        RowDivider()
                        SettingRow("🐞", "Hata raporu gönder", subtitle = "Bir sorun olursa geliştiriciye yolla", onClick = {
                            shareErrorReport(context)
                        })
                    }
                }
                item {
                    Group("Arkadaşınla paylaş") {
                        SettingRow("🔗", "İndirme bağlantısını paylaş", subtitle = "Bağlantıya tıklayan herkes en yeni sürümü indirir", onClick = { shareLink() })
                        RowDivider()
                        SettingRow(
                            "📲", "Uygulamayı dosya olarak gönder",
                            subtitle = if (preparingApk) "Hazırlanıyor…" else "Quick Share ya da Bluetooth ile, internetsiz",
                            onClick = { shareApk() },
                        )
                    }
                }
                item { About() }
            }
        }
    }

    if (nameDialog) {
        var text by remember { mutableStateOf(vm.userName) }
        AlertDialog(
            onDismissRequest = { nameDialog = false },
            title = { Text("Adın") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    singleLine = true,
                    placeholder = { Text("ör. Ayşe") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateUserName(text)
                    nameDialog = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { nameDialog = false }) { Text("Vazgeç") } },
        )
    }
    if (autoBackupDialog) {
        AutoBackupDialog(vm, onPickFolder = { autoBackupPicker.launch(null) }) { autoBackupDialog = false }
    }
    if (trashDialog) TrashDialog(vm) { trashDialog = false }
    if (statsDialog) StatsDialog(vm) { statsDialog = false }
    if (helpDialog) HelpDialog { helpDialog = false }
    if (assistantSetup) {
        AlertDialog(
            onDismissRequest = { assistantSetup = false },
            confirmButton = { TextButton(onClick = { assistantSetup = false }) { Text("Kapat") } },
            text = {
                AssistantSetup(vm.assistantConfig) {
                    vm.updateAssistantConfig(it)
                    assistantSetup = false
                    vm.showMessage("Asistan ayarları kaydedildi")
                }
            },
        )
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { Text("Şu anki kütüphane (belgeler, notlar, klasörler) yedektekiyle değiştirilecek. Emin değilsen önce şimdikini yedekle.") },
            confirmButton = { TextButton(onClick = { vm.restore(uri); restoreUri = null }) { Text("Geri yükle") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().appear()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().card(), content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 68.dp),
    )
}

@Composable
private fun SettingRow(
    emoji: String,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(emoji: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(emoji, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun About() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        AppLogo(72.dp)
        Text("SouNote", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Sürüm ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "PDF'lerin ve defterlerin üstüne kalemle not alma uygulaması.\n" +
                "Notların yalnızca bu cihazda saklanır; uygulama internete bağlanmaz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val items = listOf(
        "✍️" to "Kalem yazar, tek parmak kaydırır, iki parmak yakınlaştırır.",
        "↩️" to "İki parmakla dokun: geri al. Üç parmakla dokun: ileri al.",
        "🧽" to "Kalemin yan tuşuna basarak çizersen silgi olur.",
        "📐" to "Çizginin sonunda kalemi beklet: düz çizgi, daire ya da dikdörtgen olur.",
        "🎯" to "Lasso: seç, taşı, büyüt, döndür, rengini değiştir, kopyala ya da karta çevir.",
        "🎙️" to "Ders kaydı açıkken yazdığın nota sonra dokun: kayıt o andan çalar.",
        "🙈" to "Ezber bandı: üstünü kapattığın yere parmakla dokununca açılır/kapanır.",
        "📑" to "Sayfalar düğmesi: sayfa ekle, sil, taşı, çoğalt; PDF içindekiler.",
        "📤" to "Paylaş düğmesi: notlu PDF'i paylaş, kaydet ya da yazdır.",
        "⌨️" to "Klavye: Ctrl+Z / Ctrl+Y, PageUp / PageDown, Ctrl+F ara, Ctrl+P yazdır.",
        "📄" to "Word dosyalarını önce PDF'e çevir, sonra içe aktar.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nasıl kullanılır?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { (emoji, text) ->
                    Row {
                        Text(emoji, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}

@Composable
private fun AutoBackupDialog(vm: LibraryViewModel, onPickFolder: () -> Unit, onDismiss: () -> Unit) {
    val enabled = vm.autoBackupFolder != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Otomatik yedek") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Açıkken bütün kütüphane günde bir (değişiklik olduysa) seçtiğin klasöre yedeklenir. " +
                        "İki dosya dönüşümlü yazılır, biri her zaman sağlam kalır. Drive gibi bir bulut klasörü seçersen tablet bozulsa da notların kurtulur.",
                )
                if (enabled) {
                    val last = vm.lastAutoBackup()
                    Text(
                        if (last > 0) "Son yedek: " + SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(Date(last)) else "Henüz yedek alınmadı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (enabled) {
                TextButton(onClick = { vm.runAutoBackup(force = true); onDismiss() }) { Text("Şimdi yedekle") }
            } else {
                TextButton(onClick = { onPickFolder(); onDismiss() }) { Text("Klasör seç") }
            }
        },
        dismissButton = {
            Row {
                if (enabled) {
                    TextButton(onClick = { vm.disableAutoBackup(); onDismiss() }) { Text("Kapat") }
                    TextButton(onClick = { onPickFolder(); onDismiss() }) { Text("Klasörü değiştir") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Vazgeç") }
                }
            }
        },
    )
}

@Composable
private fun TrashDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    var confirmEmpty by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çöp kutusu") },
        text = {
            if (vm.trash.isEmpty()) {
                Text("Çöp kutusu boş.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(vm.trash, key = { it.id }) { doc ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${vm.trashDaysLeft(doc)} gün sonra kalıcı silinecek",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.restoreFromTrash(doc) }) { Text("Geri al") }
                            TextButton(onClick = { vm.purge(doc) }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        dismissButton = {
            if (vm.trash.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Çöpü boşalt", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Çöp kutusu boşaltılsın mı?") },
            text = { Text("${vm.trash.size} belge notları ve kayıtlarıyla birlikte kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.emptyTrash()
                    confirmEmpty = false
                }) { Text("Boşalt", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Vazgeç") } },
        )
    }
}
