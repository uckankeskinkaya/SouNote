@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
private const val REPO_URL = "https://github.com/uckankeskinkaya/SouNote"
private const val SITE_URL = "https://uckankeskinkaya.github.io/SouNote/"

/** Hata raporunu paylaşma penceresi (ayarlar ve çökme sonrası). */
internal fun shareErrorReport(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SouNote hata raporu")
        putExtra(Intent.EXTRA_TEXT, ErrorLog.fullReport(context))
    }
    context.startActivity(Intent.createChooser(send, "Hata raporunu paylaş"))
}

/** Ayar kategorileri. */
private enum class Category(val label: String, val emoji: String, val description: String) {
    PROFILE("Profil", "🙂", "Adın"),
    APPEARANCE("Görünüm", "🎨", "Tema, koyu palet, vurgu rengi"),
    INTERFACE("Arayüz", "🧭", "Yoğunluk, animasyonlar, menü, ana sayfa"),
    NOTES("Not alma", "✍️", "Kalem, parmakla çizim, kayıt, yazı"),
    PDF("PDF", "📄", "Gece okuma, çift sayfa, okuma modu"),
    AI("Yapay zekâ", "🤖", "Asistan servisi"),
    SECURITY("Güvenlik", "🔒", "Uygulama kilidi"),
    DATA("Veriler", "💾", "Yedekleme, geri yükleme, çöp kutusu"),
    HELP("Yardım ve paylaşım", "❓", "Güncelleme, kullanım, hata raporu, arkadaşa gönder"),
    ABOUT("Hakkında", "ℹ️", "Sürüm, geliştirici, açık kaynak"),
}

/** Aranabilir tek bir ayar satırı. */
private class Entry(val category: Category, val title: String, val keywords: String, val content: @Composable () -> Unit)

/** Kişiselleştirme merkezi: kategoriler, ayarlarda arama; tablette iki bölmeli. */
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
    var accentPicker by remember { mutableStateOf(false) }
    var layoutDialog by remember { mutableStateOf(false) }
    var quickDialog by remember { mutableStateOf(false) }
    var licensesDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val tools = vm.tools

    fun shareLink() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "SouNote: L4 hazırlık için ders notu, haftalık kaynak ve sınav uygulaması.\n" +
                    "Kurulum ve tanıtım: $SITE_URL\n" +
                    "Doğrudan indir (Android): $DOWNLOAD_URL\n\n" +
                    "Play Store'da olmadığı için telefon \"bilinmeyen uygulama\" ya da Play Protect uyarısı verebilir; " +
                    "\"Yine de yükle\" demen yeterli. İçin rahat etsin istersen APK'yı virustotal.com'a yükleyip taratabilirsin.",
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

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.showMessage("Bağlantı açılamadı") }
    }

    val entries = listOf(
        Entry(Category.PROFILE, "Adın", "ad isim profil kullanıcı") {
            SettingRow("🙂", "Adın", subtitle = vm.userName.ifBlank { "Ana sayfada seni adınla karşılayalım" }, onClick = { nameDialog = true })
        },
        Entry(Category.APPEARANCE, "Tema", "tema açık koyu karanlık sistem gece") {
            Column {
                SettingRow("🌗", "Tema", subtitle = "Açık (SouNote Light), koyu ya da sistemle aynı")
                ChipRow {
                    LibraryViewModel.ThemeMode.entries.forEach { m ->
                        FilterChip(selected = vm.themeMode == m, onClick = { vm.updateTheme(m) }, label = { Text(m.label) })
                    }
                }
            }
        },
        Entry(Category.APPEARANCE, "Koyu tema paleti", "palet obsidian midnight grafit mor mavi koyu") {
            Column {
                SettingRow("🌌", "Koyu tema paleti", subtitle = vm.darkPalette.description)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 14.dp),
                ) {
                    DarkPalette.entries.forEach { p ->
                        val sel = vm.darkPalette == p
                        val preview = colorSchemeFor(true, p, Accent.Auto)
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(p.preview))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clickable { vm.updateDarkPalette(p) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(preview.primary, preview.tertiary, preview.surfaceContainerHigh).forEach {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(it))
                                }
                            }
                            Text(p.label, color = preview.onSurface, style = MaterialTheme.typography.labelLarge)
                            if (sel) Text("Seçili ✓", color = preview.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        Entry(Category.APPEARANCE, "Vurgu rengi", "renk vurgu accent özel renk mavi mor yeşil") {
            Column {
                SettingRow("🎨", "Vurgu rengi", subtitle = vm.accent.label)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 68.dp, end = 16.dp, bottom = 14.dp),
                ) {
                    FilterChip(selected = vm.accent == Accent.Auto, onClick = { vm.updateAccent(Accent.Auto) }, label = { Text("Tema rengi") })
                    ColorTheme.entries.forEach { c ->
                        val sel = (vm.accent as? Accent.Preset)?.theme == c
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.swatch)
                                .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable(onClickLabel = c.label) { vm.updateAccent(Accent.Preset(c)) },
                        ) {
                            if (sel) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    val custom = vm.accent as? Accent.Custom
                    FilterChip(
                        selected = custom != null,
                        onClick = { accentPicker = true },
                        label = { Text("Özel renk…") },
                        leadingIcon = if (custom != null) ({ Box(Modifier.size(16.dp).clip(CircleShape).background(Color(custom.argb))) }) else null,
                    )
                }
            }
        },
        Entry(Category.INTERFACE, "Yoğunluk", "yoğunluk kompakt rahat boşluk sıkı") {
            Column {
                SettingRow("📏", "Yoğunluk", subtitle = "Kompakt düzende kartlar arası boşluklar küçülür")
                ChipRow {
                    UiDensity.entries.forEach { d ->
                        FilterChip(selected = vm.uiDensity == d, onClick = { vm.updateDensity(d) }, label = { Text(d.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Animasyonlar", "animasyon hareket azalt kapalı yavaş performans") {
            Column {
                SettingRow(
                    "✨", "Animasyonlar",
                    subtitle = "Belge ve çizim ekranında animasyon yok. Android'de animasyonlar kapalıysa burada ne seçersen seç kapalı kalır.",
                )
                ChipRow {
                    MotionLevel.entries.forEach { m ->
                        FilterChip(selected = vm.motionLevel == m, onClick = { vm.updateMotion(m) }, label = { Text(m.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Gezinme menüsü", "menü navigasyon ray çekmece sol menü tablet") {
            Column {
                SettingRow("🧭", "Gezinme menüsü (tablet)", subtitle = "Telefonda her zaman alt çubuk kullanılır")
                ChipRow {
                    LibraryViewModel.NavStyle.entries.forEach { n ->
                        FilterChip(selected = vm.navStyle == n, onClick = { vm.updateNavStyle(n) }, label = { Text(n.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Ana sayfa düzeni", "ana sayfa modül sırala gizle düzen varsayılan") {
            SettingRow("🏠", "Ana sayfa düzeni", subtitle = "Modülleri sırala, gizle ya da varsayılana dön", onClick = { layoutDialog = true })
        },
        Entry(Category.INTERFACE, "Hızlı erişim", "favori sabitle hızlı erişim sırala") {
            SettingRow("⭐", "Hızlı erişim (favoriler)", subtitle = "${vm.quickItems.size} öğe · sırala, sabitle, çıkar", onClick = { quickDialog = true })
        },
        Entry(Category.INTERFACE, "Tanıtımı tekrar göster", "tanıtım onboarding başlangıç") {
            SettingRow("🎬", "Tanıtımı tekrar göster", onClick = { vm.replayOnboarding() })
        },
        Entry(Category.NOTES, "Parmakla çiz", "parmak kalem çizim dokunma") {
            SwitchRow("👆", "Parmakla çiz", "Kalemin yoksa aç: tek parmak çizer, iki parmak kaydırır", tools.fingerDraw) {
                vm.updateTools(tools.copy(fingerDraw = it))
            }
        },
        Entry(Category.NOTES, "Düşük gecikme", "gecikme hız kalem deneysel") {
            SwitchRow("⚡", "Düşük gecikme (deneysel)", "Kalem çizgisi ekrana daha hızlı düşer", tools.lowLatency) {
                vm.updateTools(tools.copy(lowLatency = it))
            }
        },
        Entry(Category.NOTES, "Otomatik kayıt", "kayıt otomatik kaydet") {
            SettingRow(
                "💾", "Otomatik kayıt",
                subtitle = "Her zaman açık: notlar çizdikten 1 saniye içinde, yazılı notlar yazdıkça kaydedilir; uygulamadan çıkarken de kaydedilir.",
            )
        },
        Entry(Category.NOTES, "Yazı boyutu", "yazı boyutu font büyük küçük yazılı not") {
            SettingRow("🔠", "Yazılı not yazı boyutu", subtitle = "${vm.textFontSize.toInt()} pt") {
                Row {
                    TextButton(onClick = { vm.updateTextFontSize(vm.textFontSize - 2f) }) { Text("A−") }
                    TextButton(onClick = { vm.updateTextFontSize(vm.textFontSize + 2f) }) { Text("A+") }
                }
            }
        },
        Entry(Category.PDF, "Gece okuma", "gece karanlık ters renk okuma") {
            SwitchRow("🌙", "Gece okuma", "Belgelerde renkleri ters çevirir, karanlıkta gözü yormaz", vm.nightReading) { vm.toggleNightReading() }
        },
        Entry(Category.PDF, "Yatayda çift sayfa", "çift sayfa yatay iki sayfa") {
            SwitchRow("📖", "Yatayda çift sayfa", "Tablet yan dururken iki sayfa yan yana", vm.twoPage) { vm.updateTwoPage(it) }
        },
        Entry(Category.PDF, "Okuma modu", "okuma modu araç çubuğu gizle kalem") {
            SwitchRow(
                "👓", "Belgeleri okuma modunda aç",
                "Araç çubuğu gizli, kalem sayfayı kaydırır; not almak için ✏️ ile geç",
                vm.readingMode,
            ) { vm.updateReadingMode(it) }
        },
        Entry(Category.AI, "Asistan ayarları", "asistan yapay zeka ai anahtar model servis evren") {
            SettingRow(
                "🤖", "Asistan ayarları",
                subtitle = if (vm.assistantConfig.ready) "Hazır · ${vm.assistantConfig.model}" else "API anahtarı girilmedi",
                onClick = { assistantSetup = true },
            )
        },
        Entry(Category.AI, "Gizlilik", "gizlilik veri gönder yapay zeka") {
            SettingRow(
                "🛡️", "Ne gönderilir?",
                subtitle = "Asistanı ya da yazma koçunu kullandığında sadece sorduğun metin (ve seçtiğin sayfa/not) ayarladığın servise gider. Anahtar yalnızca bu cihazda saklanır.",
            )
        },
        Entry(Category.SECURITY, "Uygulama kilidi", "kilit parmak izi yüz şifre güvenlik") {
            SwitchRow("🔒", "Uygulama kilidi", "Açılışta parmak izi, yüz ya da ekran kilidi şifresi ister", vm.appLock) { vm.updateAppLock(it) }
        },
        Entry(Category.DATA, "Şimdi yedekle", "yedek yedekle zip") {
            SettingRow("💾", "Şimdi yedekle", subtitle = "Bütün belgeler ve notlar tek bir zip dosyasına", onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                backupLauncher.launch("SouNote-yedek-$date.zip")
            })
        },
        Entry(Category.DATA, "Drive'a yedekle", "drive google bulut yedek") {
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
        },
        Entry(Category.DATA, "Otomatik yedek", "otomatik yedek günlük klasör") {
            SettingRow(
                "☁️", "Otomatik yedek",
                subtitle = if (vm.autoBackupFolder != null) "Açık · günde bir" else "Kapalı",
                onClick = { autoBackupDialog = true },
            )
        },
        Entry(Category.DATA, "Yedekten geri yükle", "geri yükle restore yedek") {
            SettingRow("♻️", "Yedekten geri yükle", subtitle = "Başka tabletten ya da eski yedekten", onClick = {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            })
        },
        Entry(Category.DATA, "Çöp kutusu", "çöp sil geri al kalıcı") {
            SettingRow(
                "🗑️", "Çöp kutusu",
                subtitle = if (vm.trash.isEmpty()) "Boş" else "${vm.trash.size} belge · ${LibraryViewModel.TRASH_DAYS} gün sonra silinir",
                onClick = { trashDialog = true },
            )
        },
        Entry(Category.DATA, "Çalışma istatistiği", "istatistik çalışma süre seri") {
            SettingRow("📊", "Çalışma istatistiği", subtitle = "Bugün, bu hafta, seri, en çok çalışılanlar", onClick = { statsDialog = true })
        },
        Entry(Category.HELP, "Güncellemeleri denetle", "güncelleme sürüm yeni yönetici test") {
            // Sürüm satırına art arda 7 dokunuş: yönetici modu (test sürümleri) aç/kapa
            var taps by remember { mutableStateOf(0) }
            var lastTap by remember { mutableStateOf(0L) }
            Column {
                SettingRow(
                    "🔄", "Güncellemeleri denetle",
                    subtitle = "Sürüm ${vm.installedVersion}" + (if (vm.testChannel) " · yönetici" else "") +
                        (vm.update?.let { " · yeni sürüm var: ${it.name}" } ?: ""),
                    onClick = {
                        val t = System.currentTimeMillis()
                        taps = if (t - lastTap < 600) taps + 1 else 1
                        lastTap = t
                        when {
                            taps >= 7 -> {
                                taps = 0
                                vm.switchTestChannel(!vm.testChannel)
                            }
                            taps == 1 -> vm.checkUpdate(manual = true)
                        }
                    },
                )
                if (vm.testChannel) {
                    RowDivider()
                    SwitchRow(
                        "🧪", "Yönetici modu",
                        "Yayınlanmamış test sürümlerini de alırsın; arkadaşların sadece yayınlananları alır",
                        true,
                    ) { vm.switchTestChannel(it) }
                }
            }
        },
        Entry(Category.HELP, "Nasıl kullanılır?", "yardım hareket kısayol kullanım") {
            SettingRow("❓", "Nasıl kullanılır?", subtitle = "Hareketler ve kısayollar", onClick = { helpDialog = true })
        },
        Entry(Category.HELP, "Hata raporu gönder", "hata rapor sorun çökme") {
            SettingRow("🐞", "Hata raporu gönder", subtitle = "Bir sorun olursa geliştiriciye yolla", onClick = { shareErrorReport(context) })
        },
        Entry(Category.HELP, "İndirme bağlantısını paylaş", "paylaş arkadaş bağlantı indir") {
            SettingRow("🔗", "İndirme bağlantısını paylaş", subtitle = "Bağlantıya tıklayan herkes en yeni sürümü indirir", onClick = { shareLink() })
        },
        Entry(Category.HELP, "Uygulamayı dosya olarak gönder", "apk gönder bluetooth quick share") {
            SettingRow(
                "📲", "Uygulamayı dosya olarak gönder",
                subtitle = if (preparingApk) "Hazırlanıyor…" else "Quick Share ya da Bluetooth ile, internetsiz",
                onClick = { shareApk() },
            )
        },
        Entry(Category.ABOUT, "Hakkında", "hakkında sürüm geliştirici github açık kaynak lisans") {
            About(onGitHub = { openUrl(REPO_URL) }, onLicenses = { licensesDialog = true })
        },
    )

    val q = query.trim().lowercase(TR)
    val results = if (q.isEmpty()) emptyList() else entries.filter {
        it.title.lowercase(TR).contains(q) || it.keywords.contains(q) || it.category.label.lowercase(TR).contains(q)
    }
    val category = Category.entries.find { it.name == selected }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        // Telefonda kategori sayfasından geri: kategori listesine.
        BackHandler(enabled = !wide && category != null && q.isEmpty()) { selected = null }
        Scaffold(
            topBar = {
                SouTopBar(
                    if (!wide && category != null && q.isEmpty()) category.label else "Ayarlar",
                    subtitle = if (!wide && category != null && q.isEmpty()) category.description else "Kişiselleştirme merkezi",
                    navigationIcon = {
                        if (!wide && category != null && q.isEmpty()) {
                            IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                val showList = wide || (category == null && q.isEmpty())
                if (showList || q.isNotEmpty()) {
                    Column(
                        Modifier
                            .then(if (wide) Modifier.width(320.dp) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                    ) {
                        SouSearchField(query, { query = it }, "Ayarlarda ara", modifier = Modifier.padding(bottom = 12.dp))
                        if (!wide && q.isNotEmpty()) {
                            SearchResults(results, Modifier.weight(1f))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(Category.entries) { c ->
                                    val sel = wide && (category ?: Category.APPEARANCE) == c && q.isEmpty()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .clickable { selected = c.name; query = "" }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                    ) {
                                        EmojiTile(c.emoji, MaterialTheme.colorScheme.primary, 38.dp)
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(c.label, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                c.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (!wide) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide || (category != null && q.isEmpty())) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        if (wide && q.isNotEmpty()) {
                            SearchResults(results, Modifier.widthIn(max = 760.dp).padding(horizontal = 16.dp))
                        } else {
                            val c = category ?: Category.APPEARANCE
                            Column(
                                Modifier
                                    .widthIn(max = 760.dp)
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                            ) {
                                SettingsGroup("${c.emoji}  ${c.label}", Modifier.appear()) {
                                    entries.filter { it.category == c }.forEachIndexed { i, e ->
                                        if (i > 0) RowDivider()
                                        e.content()
                                    }
                                }
                            }
                        }
                    }
                }
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
    if (accentPicker) {
        val initial = when (val a = vm.accent) {
            is Accent.Custom -> a.argb
            is Accent.Preset -> a.theme.lightPrimary.toInt()
            Accent.Auto -> 0xFF4F46E5.toInt()
        }
        ColorPickerDialog(
            initial = initial,
            onPick = {
                vm.updateAccent(Accent.Custom(it))
                accentPicker = false
            },
            onDismiss = { accentPicker = false },
        )
    }
    if (layoutDialog) HomeLayoutDialog(vm) { layoutDialog = false }
    if (quickDialog) QuickAccessDialog(vm) { quickDialog = false }
    if (licensesDialog) LicensesDialog { licensesDialog = false }
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

/** Ayar satırının altındaki seçim çipleri. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 68.dp, end = 16.dp, bottom = 12.dp),
    ) { content() }
}

/** Ayarlarda arama sonuçları: kategori adıyla birlikte. */
@Composable
private fun SearchResults(results: List<Entry>, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (results.isEmpty()) {
            EmptyState("🔍", "Ayar bulunamadı", "Başka bir kelimeyle dene: ör. tema, yedek, kalem, kilit.")
        }
        results.groupBy { it.category }.forEach { (c, list) ->
            SettingsGroup("${c.emoji}  ${c.label}") {
                list.forEachIndexed { i, e ->
                    if (i > 0) RowDivider()
                    e.content()
                }
            }
        }
    }
}

@Composable
private fun About(onGitHub: () -> Unit, onLicenses: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) {
        AppLogo(72.dp)
        Text("SouNote", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sürüm ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "PDF'lerin ve defterlerin üstüne kalemle not alma uygulaması. Ücretsiz, açık kaynak, hesap gerektirmez.\n" +
                "Notların yalnızca bu cihazda saklanır. İnternet sadece ders kataloğu, güncelleme denetimi, " +
                "ders kaynağı indirme ve (kurarsan) asistan için kullanılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text("Geliştirici: uckankeskinkaya (GitHub)", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onGitHub) { Text("GitHub'da aç") }
            TextButton(onClick = onLicenses) { Text("Açık kaynak lisansları") }
        }
    }
}

/** Kullanılan açık kaynak bileşenler. */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val items = listOf(
        "AndroidX, Jetpack Compose, Material 3" to "Apache License 2.0 · Google",
        "PDFBox-Android (com.tom-roush)" to "Apache License 2.0 · Apache PDFBox uyarlaması",
        "ML Kit Digital Ink Recognition" to "Google ML Kit hizmet şartları",
        "Manrope yazı tipi" to "SIL Open Font License 1.1 · The Manrope Project Authors",
        "Kotlin, kotlinx.coroutines" to "Apache License 2.0 · JetBrains",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Açık kaynak lisansları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (name, license) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val items = listOf(
        "✍️" to "Kalem yazar, tek parmak kaydırır, iki parmak yakınlaştırır.",
        "↩️" to "İki parmakla dokun: geri al. Üç parmakla dokun: ileri al.",
        "🧽" to "Kalemin yan tuşuna basarak çizersen silgi olur.",
        "📐" to "Çizginin sonunda kalemi beklet: düz çizgi, daire ya da dikdörtgen olur.",
        "🎯" to "Lasso: seç, taşı, büyüt, döndür, rengini değiştir, kopyala, karta çevir ya da asistana sor.",
        "👓" to "Okuma modu (📖): araç çubuğu gizlenir, kalem de sayfayı kaydırır. Not almak için ✏️.",
        "⭐" to "Belge, klasör, ders ve notları favorilere ekle; ana sayfada hızlı erişimde görünür.",
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
