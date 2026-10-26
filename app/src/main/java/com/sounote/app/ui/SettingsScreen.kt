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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
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
private enum class Category(val label: String, val icon: ImageVector) {
    PROFILE("Profil", SouIcons.AccountCircle),
    APPEARANCE("Görünüm", SouIcons.Palette),
    INTERFACE("Arayüz", SouIcons.DashboardCustomize),
    NOTES("Not alma", SouIcons.Stylus),
    PDF("PDF", SouIcons.PictureAsPdf),
    AI("Asistan", SouIcons.StarShine),
    SECURITY("Güvenlik", SouIcons.Lock),
    DATA("Veriler", SouIcons.Backup),
    HELP("Yardım", SouIcons.Help),
    ABOUT("Hakkında", SouIcons.Info),
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
            SettingRow(SouIcons.Person, "Adın", subtitle = vm.userName.ifBlank { null }, onClick = { nameDialog = true })
        },
        Entry(Category.APPEARANCE, "Açık / koyu", "tema açık koyu karanlık sistem gece") {
            Column {
                SettingRow(SouIcons.Contrast, "Açık / koyu")
                ChipRow {
                    LibraryViewModel.ThemeMode.entries.forEach { m ->
                        FilterChip(selected = vm.themeMode == m, onClick = { vm.updateTheme(m) }, label = { Text(m.label) })
                    }
                }
            }
        },
        Entry(Category.APPEARANCE, "Tema", "tema logo elektrik safir gümüş siyah beyaz zümrüt gül renk palet koyu") {
            Column {
                SettingRow(SouIcons.Palette, "Tema", subtitle = vm.appTheme.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 14.dp),
                ) {
                    AppTheme.entries.forEach { t ->
                        val sel = vm.appTheme == t
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .width(112.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(t.heroB), Color(t.bg))))
                                .border(
                                    if (sel) 2.5.dp else 1.dp,
                                    if (sel) Color(t.glowB) else Color(t.glowA).copy(alpha = 0.4f),
                                    RoundedCornerShape(18.dp),
                                )
                                .clickable { vm.updateAppTheme(t) }
                                .padding(10.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.res.painterResource(t.logo),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Text(t.label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            if (sel) Icon(SouIcons.CheckCircle, contentDescription = "Seçili", tint = Color(t.darkPrimary), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        Entry(Category.APPEARANCE, "Vurgu rengi", "renk vurgu accent özel renk mavi mor yeşil") {
            Column {
                SettingRow(SouIcons.Palette, "Vurgu rengi", subtitle = vm.accent.label)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 56.dp, end = 16.dp, bottom = 14.dp),
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
                            if (sel) Icon(SouIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    val custom = vm.accent as? Accent.Custom
                    FilterChip(
                        selected = custom != null,
                        onClick = { accentPicker = true },
                        label = { Text("Özel") },
                        leadingIcon = if (custom != null) ({ Box(Modifier.size(16.dp).clip(CircleShape).background(Color(custom.argb))) }) else null,
                    )
                }
            }
        },
        Entry(Category.INTERFACE, "Yoğunluk", "yoğunluk kompakt rahat boşluk sıkı") {
            Column {
                SettingRow(SouIcons.ViewAgenda, "Yoğunluk")
                ChipRow {
                    UiDensity.entries.forEach { d ->
                        FilterChip(selected = vm.uiDensity == d, onClick = { vm.updateDensity(d) }, label = { Text(d.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Animasyonlar", "animasyon hareket azalt kapalı yavaş performans") {
            Column {
                SettingRow(SouIcons.Bolt, "Animasyonlar")
                ChipRow {
                    MotionLevel.entries.forEach { m ->
                        FilterChip(selected = vm.motionLevel == m, onClick = { vm.updateMotion(m) }, label = { Text(m.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Gezinme menüsü", "menü navigasyon ray çekmece sol menü tablet") {
            Column {
                SettingRow(SouIcons.Widgets, "Menü (tablet)")
                ChipRow {
                    LibraryViewModel.NavStyle.entries.forEach { n ->
                        FilterChip(selected = vm.navStyle == n, onClick = { vm.updateNavStyle(n) }, label = { Text(n.label) })
                    }
                }
            }
        },
        Entry(Category.INTERFACE, "Ana sayfa düzeni", "ana sayfa modül sırala gizle düzen varsayılan") {
            SettingRow(SouIcons.Home, "Ana sayfa düzeni", onClick = { layoutDialog = true })
        },
        Entry(Category.INTERFACE, "Hızlı erişim", "favori sabitle hızlı erişim sırala") {
            SettingRow(SouIcons.Star, "Favoriler", subtitle = "${vm.quickItems.size} öğe", onClick = { quickDialog = true })
        },
        Entry(Category.INTERFACE, "Tanıtımı tekrar göster", "tanıtım onboarding başlangıç") {
            SettingRow(SouIcons.Slideshow, "Tanıtımı tekrar göster", onClick = { vm.replayOnboarding() })
        },
        Entry(Category.NOTES, "Parmakla çiz", "parmak kalem çizim dokunma") {
            SwitchRow(SouIcons.TouchApp, "Parmakla çiz", "Tek parmak çizer, iki parmak kaydırır", tools.fingerDraw) {
                vm.updateTools(tools.copy(fingerDraw = it))
            }
        },
        Entry(Category.NOTES, "Yazma desteği", "yazma desteği titreme yumuşat düzelt çizgi bağla stabilizatör") {
            Column {
                SettingRow(SouIcons.Draw, "Yazma desteği", subtitle = if (tools.smoothing == 0f) "Kapalı" else "%${(tools.smoothing * 100).toInt()}")
                Slider(
                    value = tools.smoothing,
                    onValueChange = { vm.updateTools(tools.copy(smoothing = (it * 20).toInt() / 20f)) },
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        },
        Entry(Category.NOTES, "Düşük gecikme", "gecikme hız kalem deneysel") {
            SwitchRow(SouIcons.Bolt, "Düşük gecikme", "Deneysel", tools.lowLatency) {
                vm.updateTools(tools.copy(lowLatency = it))
            }
        },
        Entry(Category.NOTES, "Otomatik kayıt", "kayıt otomatik kaydet") {
            SettingRow(SouIcons.CheckCircle, "Otomatik kayıt", subtitle = "Her zaman açık")
        },
        Entry(Category.NOTES, "Yazı boyutu", "yazı boyutu font büyük küçük yazılı not") {
            SettingRow(SouIcons.FormatSize, "Yazılı not yazı boyutu", subtitle = "${vm.textFontSize.toInt()} pt") {
                Row {
                    TextButton(onClick = { vm.updateTextFontSize(vm.textFontSize - 2f) }) { Text("A−") }
                    TextButton(onClick = { vm.updateTextFontSize(vm.textFontSize + 2f) }) { Text("A+") }
                }
            }
        },
        Entry(Category.PDF, "Gece okuma", "gece karanlık ters renk okuma") {
            SwitchRow(SouIcons.DarkMode, "Gece okuma", "Sayfa renklerini ters çevirir", vm.nightReading) { vm.toggleNightReading() }
        },
        Entry(Category.PDF, "Yatayda çift sayfa", "çift sayfa yatay iki sayfa") {
            SwitchRow(SouIcons.AutoStories, "Yatayda çift sayfa", checked = vm.twoPage) { vm.updateTwoPage(it) }
        },
        Entry(Category.PDF, "Okuma modu", "okuma modu araç çubuğu gizle kalem") {
            SwitchRow(SouIcons.MenuBook, "Belgeleri okuma modunda aç", checked = vm.readingMode) { vm.updateReadingMode(it) }
        },
        Entry(Category.AI, "Asistan ayarları", "asistan yapay zeka ai anahtar model servis evren") {
            SettingRow(
                SouIcons.StarShine, "Asistan ayarları",
                subtitle = if (vm.assistantConfig.ready) "Hazır · ${vm.assistantConfig.model}" else "API anahtarı girilmedi",
                onClick = { assistantSetup = true },
            )
        },
        Entry(Category.AI, "Gizlilik", "gizlilik veri gönder yapay zeka") {
            SettingRow(SouIcons.Lock, "Sadece sorduğun metin gönderilir", subtitle = "Anahtar bu cihazda kalır")
        },
        Entry(Category.SECURITY, "Uygulama kilidi", "kilit parmak izi yüz şifre güvenlik") {
            SwitchRow(SouIcons.Lock, "Uygulama kilidi", "Parmak izi, yüz ya da şifre", vm.appLock) { vm.updateAppLock(it) }
        },
        Entry(Category.DATA, "Şimdi yedekle", "yedek yedekle zip") {
            SettingRow(SouIcons.Download, "Şimdi yedekle", onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                backupLauncher.launch("SouNote-yedek-$date.zip")
            })
        },
        Entry(Category.DATA, "Drive'a yedekle", "drive google bulut yedek") {
            SettingRow(SouIcons.CloudUpload, "Drive'a yedekle", onClick = {
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
                SouIcons.Backup, "Otomatik yedek",
                subtitle = if (vm.autoBackupFolder != null) "Açık · günde bir" else "Kapalı",
                onClick = { autoBackupDialog = true },
            )
        },
        Entry(Category.DATA, "Yedekten geri yükle", "geri yükle restore yedek") {
            SettingRow(SouIcons.SettingsBackupRestore, "Yedekten geri yükle", onClick = {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            })
        },
        Entry(Category.DATA, "Çöp kutusu", "çöp sil geri al kalıcı") {
            SettingRow(
                SouIcons.Delete, "Çöp kutusu",
                subtitle = if (vm.trash.isEmpty()) "Boş" else "${vm.trash.size} belge",
                onClick = { trashDialog = true },
            )
        },
        Entry(Category.DATA, "Çalışma istatistiği", "istatistik çalışma süre seri") {
            SettingRow(SouIcons.BarChart, "Çalışma istatistiği", onClick = { statsDialog = true })
        },
        Entry(Category.HELP, "Güncellemeleri denetle", "güncelleme sürüm yeni yönetici test") {
            // Sürüm satırına art arda 7 dokunuş: yönetici modu (test sürümleri) aç/kapa
            var taps by remember { mutableStateOf(0) }
            var lastTap by remember { mutableStateOf(0L) }
            Column {
                SettingRow(
                    SouIcons.Sync, "Güncellemeleri denetle",
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
                    SwitchRow(SouIcons.BugReport, "Yönetici modu", "Test sürümlerini de alırsın", true) { vm.switchTestChannel(it) }
                }
            }
        },
        Entry(Category.HELP, "Nasıl kullanılır?", "yardım hareket kısayol kullanım") {
            SettingRow(SouIcons.Help, "Nasıl kullanılır?", onClick = { helpDialog = true })
        },
        Entry(Category.HELP, "Hata raporu gönder", "hata rapor sorun çökme") {
            SettingRow(SouIcons.BugReport, "Hata raporu gönder", onClick = { shareErrorReport(context) })
        },
        Entry(Category.HELP, "İndirme bağlantısını paylaş", "paylaş arkadaş bağlantı indir") {
            SettingRow(SouIcons.Link, "İndirme bağlantısını paylaş", onClick = { shareLink() })
        },
        Entry(Category.HELP, "Uygulamayı dosya olarak gönder", "apk gönder bluetooth quick share") {
            SettingRow(
                SouIcons.Share, "Uygulamayı dosya olarak gönder",
                subtitle = if (preparingApk) "Hazırlanıyor…" else null,
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
                    subtitle = null,
                    navigationIcon = {
                        if (!wide && category != null && q.isEmpty()) {
                            IconButton(onClick = { selected = null }) { Icon(SouIcons.ArrowBack, contentDescription = "Geri") }
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
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
                                        Icon(c.icon, contentDescription = null, tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                        if (!wide) {
                                            Icon(
                                                SouIcons.ChevronRight,
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
                                SettingsGroup(c.label, Modifier.appear()) {
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
            Accent.Auto -> vm.appTheme.lightPrimary.toInt()
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
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
    ) { content() }
}

/** Ayarlarda arama sonuçları: kategori adıyla birlikte. */
@Composable
private fun SearchResults(results: List<Entry>, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (results.isEmpty()) {
            EmptyState(SouIcons.Search, "Ayar bulunamadı", art = com.sounote.app.R.drawable.empty_search)
        }
        results.groupBy { it.category }.forEach { (c, list) ->
            SettingsGroup(c.label) {
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
            "Ücretsiz ve açık kaynak. Notların sadece bu cihazda saklanır.",
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
        SouIcons.Stylus to "Kalem yazar, tek parmak kaydırır, iki parmak yakınlaştırır.",
        SouIcons.Undo to "İki parmakla dokun: geri al. Üç parmakla: ileri al.",
        SouIcons.InkEraser to "Kalemin yan tuşuyla çizersen silgi olur.",
        SouIcons.Shapes to "Çizginin sonunda kalemi beklet: düz çizgi, daire ya da dikdörtgen olur.",
        SouIcons.GestureSelect to "Lasso: seç, taşı, büyüt, döndür, kopyala, karta çevir, asistana sor.",
        SouIcons.MenuBook to "Okuma modunda kalem de sayfayı kaydırır.",
        SouIcons.Star to "Yıldızladıkların ana sayfada favorilerde görünür.",
        SouIcons.Mic to "Ses kaydı açıkken yazdığın nota dokun: kayıt o andan çalar.",
        SouIcons.VisibilityOff to "Ezber bandının üstüne dokununca açılır, kapanır.",
        SouIcons.Keyboard to "Ctrl+Z / Ctrl+Y, PageUp / PageDown, Ctrl+F ara, Ctrl+P yazdır.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nasıl kullanılır?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { (icon, text) ->
                    Row {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(20.dp))
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
                Text("Kütüphane günde bir seçtiğin klasöre yedeklenir. Drive klasörü seçersen tablet bozulsa da notların kurtulur.")
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
