@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import com.sounote.app.data.DocRef
import com.sounote.app.data.TextNote
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

private val TIPS = listOf(
    "Kalemi bir çizginin sonunda bir an beklet: düz çizgiye, daireye ya da dikdörtgene dönüşür.",
    "İki parmakla dokun: geri al. Üç parmakla dokun: ileri al.",
    "Kalemin yan tuşuna basarak çizersen silgi olur.",
    "Ezber bandıyla önemli yerlerin üstünü kapat; dokununca açılır, kendini sınarsın.",
    "Ders kaydı açıkken aldığın notlara sonra dokun: kayıt tam o andan çalar.",
    "Lasso ile bir bölgeyi seç, \"Karta çevir\" de: bilgi kartıyla tekrar edersin.",
    "Bağlı klasöre attığın yeni PDF'ler kütüphanede kendiliğinden görünür.",
    "Otomatik yedeği Drive'daki bir klasöre bağlarsan tablet bozulsa da notların güvende.",
    "Sayfa panelinden sayfa ekleyip silebilir, sürükleyip yerini değiştirebilirsin.",
    "Klavye bağlıysa Ctrl+Z geri alır, PageDown sonraki sayfaya geçer.",
    "Pomodoro ile 25 dakika çalış, 5 dakika mola ver; süreni istatistikte gör.",
    "Gece okuma modu sayfaları koyulaştırır, akşam gözünü yormaz.",
    "Kalemi olmayan arkadaşın Ayarlar'dan \"Parmakla çiz\"i açabilir.",
    "Şekil aracıyla ok, dikdörtgen ve elipsi sürükleyerek çiz.",
)

private val MOTTOS = listOf(
    "Bugün ne çalışıyoruz?",
    "Küçük adımlar, düzenli notlar.",
    "Bir sayfa bile ilerlemektir.",
    "Odaklan, yaz, tekrar et.",
    "Kalemin hazır, sen de hazırsın.",
    "Bugünün notu, yarının özeti.",
)

private fun dayOfYear() = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val g = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..17 -> "İyi günler"
        in 18..22 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
    return if (name.isBlank()) g else "$g, $name"
}

/** Kartların zemini: açık temada beyaz, koyu temada biraz açık gri. */
@Composable
internal fun cardBackground(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

@Composable
internal fun Modifier.card(): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this
        .clip(shape)
        .background(cardBackground())
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
}

/** Ana sayfa: karşılama, kaldığın yer, hızlı başlangıç, son açılanlar, haftalık çalışma, ipucu. */
@Composable
fun HomeScreen(vm: LibraryViewModel) {
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importPdfs(uris, openAfter = uris.size == 1)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.linkFolder(uri)
            vm.tab = LibraryViewModel.Tab.LIBRARY
        }
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.enableAutoBackup(uri)
    }
    var newNotebook by remember { mutableStateOf(false) }
    var assistant by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var gradesOpen by remember { mutableStateOf(false) }
    var writingOpen by remember { mutableStateOf(false) }
    // Bugünkü tekrar: açılınca kartlar yüklenir
    var review by remember { mutableStateOf<List<Pair<DocRef, com.sounote.app.data.Card>>?>(null) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<DocRef?>(null) }
    var deleting by remember { mutableStateOf<DocRef?>(null) }
    var deletingText by remember { mutableStateOf<TextNote?>(null) }
    val context = LocalContext.current
    val docMenu: @Composable (DocRef, () -> Unit) -> Unit = { doc, dismiss ->
        DropdownMenuItem(text = { Text("Aç") }, onClick = { dismiss(); vm.open(doc) })
        DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { dismiss(); renaming = doc })
        DropdownMenuItem(
            text = { Text("Notlu PDF'i paylaş") },
            onClick = { dismiss(); vm.export(doc) { sharePdfFile(context, it) } },
        )
        DropdownMenuItem(
            text = { Text("SouNote'lu arkadaşa gönder") },
            onClick = { dismiss(); vm.sharePackage(doc) { sharePackageFile(context, it) } },
        )
        DropdownMenuItem(text = { Text("Sil", color = MaterialTheme.colorScheme.error) }, onClick = { dismiss(); deleting = doc })
    }
    val importPdf = { pickPdfs.launch(arrayOf("application/pdf")) }

    val recent = remember(vm.imported) {
        vm.imported.filter { vm.lastOpened(it) > 0 }.sortedByDescending { vm.lastOpened(it) }.take(12)
    }
    val days = remember(vm.imported) { vm.studyDays(7) }
    val empty = vm.loaded && vm.imported.isEmpty() && vm.rootFolders.isEmpty()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val narrow = LocalConfiguration.current.screenWidthDp < 600
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 16.dp else 24.dp, vertical = if (narrow) 12.dp else 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (narrow) 22.dp else 28.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            ) {
                Appear(0) { SearchBarButton { searchOpen = true } }
                Appear(0) {
                    Hero(
                        greeting = greeting(vm.userName),
                        todayMs = days.first().second,
                        docCount = vm.imported.size,
                    )
                }

                Appear(1) { Section("Hızlı başla") }
                QuickActions(
                    listOf(
                        // Kalemi olmayanlar için: tek dokunuşla parmakla yazma
                        QuickAction(
                            painterResource(R.drawable.ic_touch), "Parmakla yaz",
                            "Kalemin yoksa aç: tek parmak çizer, iki parmak kaydırır", Color(0xFF00796B),
                            toggled = vm.tools.fingerDraw,
                        ) {
                            vm.updateTools(vm.tools.copy(fingerDraw = !vm.tools.fingerDraw))
                            vm.showMessage(if (vm.tools.fingerDraw) "Parmakla yazma açık: tek parmak çizer, iki parmak kaydırır" else "Parmakla yazma kapalı: sadece kalem çizer")
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Add), "Yeni defter", "Şablonlu boş sayfalar", Color(0xFF2F5BD3)) {
                            newNotebook = true
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Edit), "Hızlı not", "Hemen yazmaya başla", Color(0xFFE0893A)) {
                            vm.quickNote()
                        },
                        QuickAction(painterResource(R.drawable.ic_text), "Yazılı not", "Klavyeyle not, yapılacaklar", Color(0xFF8E63CE)) {
                            vm.newTextNote()
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Face), "Asistan", "İngilizce ve ders soruları sor", Color(0xFF00796B)) {
                            assistant = true
                        },
                        QuickAction(painterResource(R.drawable.ic_text), "Writing", "Essay yaz, asistan puanlasın", Color(0xFFC2357A)) {
                            writingOpen = true
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Star), "Ortalama", "Notlarını gir, geçmek için gerekeni gör", Color(0xFFC2570C)) {
                            gradesOpen = true
                        },
                        QuickAction(painterResource(R.drawable.ic_pdf), "PDF içe aktar", "Ders notlarını getir", Color(0xFFD64545)) {
                            importPdf()
                        },
                        QuickAction(painterResource(R.drawable.ic_folder), "Klasör bağla", "Yeni PDF'ler kendiliğinden gelsin", Color(0xFF3FA36B)) {
                            pickFolder.launch(null)
                        },
                    ),
                )

                if (vm.showBackupNudge) {
                    Appear(4) { BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge) }
                }

                if (vm.dueCount > 0) {
                    Appear(4, Modifier.pressScale(0.98f)) {
                        ReviewCard(vm.dueCount) { scope.launch { review = vm.dueCards().ifEmpty { null } } }
                    }
                }

                val last = recent.firstOrNull()
                if (empty) {
                    Appear(5) { StartCard(onNotebook = { newNotebook = true }, onImport = importPdf) }
                } else if (last != null) {
                    Appear(5, Modifier.pressScale(0.98f)) {
                        ContinueCard(last, vm.lastPage(last), vm.lastOpened(last), menu = { d -> docMenu(last, d) }) { vm.open(last) }
                    }
                }

                val now = System.currentTimeMillis()
                val nextExams = vm.allExams.filter { it.time > now }.take(if (narrow) 5 else 3)
                if (nextExams.isNotEmpty()) {
                    Appear(5) { Section("Yaklaşan sınav ve ödevler", action = "Takvim") { vm.tab = LibraryViewModel.Tab.EXAMS } }
                    if (narrow) {
                        // Telefonda yan yana kaydırılan küçük kartlar (alt alta üç büyük kart çok yer kaplıyordu)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                            itemsIndexed(nextExams, key = { _, e -> e.id }) { i, e ->
                                ExamMini(e, Modifier.appear(6 + i).pressScale()) { vm.tab = LibraryViewModel.Tab.EXAMS }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            nextExams.forEachIndexed { i, e ->
                                Appear(6 + i, Modifier.pressScale(0.98f)) {
                                    ExamCard(e, vm, onClick = { vm.tab = LibraryViewModel.Tab.EXAMS }, onLongClick = {})
                                }
                            }
                        }
                    }
                }

                // Bu haftanın kaynakları henüz girilmediyse (ör. hafta sonu) en son dolu hafta gösterilir.
                val (week, weekTasks) = remember(vm.catalog) {
                    val now = vm.catalog.weekOf(System.currentTimeMillis())
                    (now downTo 1).asSequence().map { it to vm.weekTasks(it) }.firstOrNull { it.second.isNotEmpty() } ?: (0 to emptyList())
                }
                if (weekTasks.isNotEmpty()) {
                    Appear(4) {
                        WeekTasksCard(week, weekTasks, vm.doneItems, onToggle = vm::toggleDone) {
                            vm.coursesByWeek = true
                            vm.tab = LibraryViewModel.Tab.COURSES
                        }
                    }
                }

                if (recent.size > 1) {
                    Appear(9) { Section("Son açılanlar", action = "Kütüphane") { vm.tab = LibraryViewModel.Tab.LIBRARY } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        itemsIndexed(recent, key = { _, it -> it.id }) { i, doc ->
                            Box(Modifier.width(132.dp).appear(9 + i).pressScale()) { DocCard(doc, onClick = { vm.open(doc) }, menu = { d -> docMenu(doc, d) }) }
                        }
                    }
                }

                if (vm.textNotes.isNotEmpty()) {
                    Appear(10) { Section("Yazılı notlar", action = "Yeni not") { vm.newTextNote() } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        itemsIndexed(vm.textNotes.take(12), key = { _, it -> "t" + it.id }) { i, n ->
                            Box(Modifier.width(132.dp).appear(10 + i).pressScale()) {
                                TextNoteCard(n, onClick = { vm.openTextNote(n) }, menu = { d -> TextNoteMenuItems(n, vm, d) { deletingText = n } })
                            }
                        }
                    }
                }

                BoxWithConstraints(Modifier.appear(11)) {
                    if (maxWidth > 640.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            WeekCard(days, Modifier.weight(1f))
                            TipCard(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            WeekCard(days, Modifier.fillMaxWidth())
                            TipCard(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (assistant) AssistantDialog(vm, AssistantSource.General) { assistant = false }
    if (searchOpen) SearchDialog(vm) { searchOpen = false }
    review?.let { list ->
        val byId = remember(list) { list.associate { it.second.id to it.first } }
        StudyDialog(
            cards = list.map { it.second },
            image = { c -> byId[c.id]?.let { vm.cardImage(it, c) } },
            onReview = { c, known -> byId[c.id]?.let { vm.reviewAny(it, c, known) } },
            onGoToPage = { c ->
                review = null
                byId[c.id]?.let { vm.openAt(it, c.page) }
            },
            onDismiss = {
                review = null
                vm.refreshDue()
            },
        )
    }
    if (gradesOpen) GradesDialog(vm) { gradesOpen = false }
    if (writingOpen) WritingScreen(vm) { writingOpen = false }
    renaming?.let { doc ->
        var text by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Yeniden adlandır") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(doc, text); renaming = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Silinsin mi?") },
            text = { Text("\"${doc.title}\" çöp kutusuna taşınacak. ${LibraryViewModel.TRASH_DAYS} gün içinde geri alabilirsin.") },
            confirmButton = { TextButton(onClick = { vm.delete(doc); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
    deletingText?.let { n ->
        AlertDialog(
            onDismissRequest = { deletingText = null },
            title = { Text("Not silinsin mi?") },
            text = { Text("\"${n.displayTitle}\" kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { vm.deleteTextNote(n); deletingText = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deletingText = null }) { Text("Vazgeç") } },
        )
    }
    if (newNotebook) {
        NotebookDialog(
            onCreate = { name, template, size, landscape, paper ->
                newNotebook = false
                vm.createNotebook(name, template, size, landscape, paper)
            },
            onDismiss = { newNotebook = false },
        )
    }
}

@Composable
private fun Hero(greeting: String, todayMs: Long, docCount: Int) {
    val date = remember { SimpleDateFormat("d MMMM EEEE", TR).format(Date()) }
    val motto = remember { MOTTOS[dayOfYear() % MOTTOS.size] }
    val hero = LocalColorTheme.current.heroColors
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(hero))) {
        val compact = maxWidth < 420.dp
        // Arka plandaki daireler yavaşça süzülür.
        val drift = rememberInfiniteTransition(label = "hero")
        val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                Color.White.copy(alpha = 0.08f),
                radius = size.height * (0.85f + 0.1f * t),
                center = Offset(size.width * (0.92f + 0.05f * t), size.height * (-0.05f + 0.1f * t)),
            )
            drawCircle(
                Color.White.copy(alpha = 0.06f),
                radius = size.height * (0.42f + 0.06f * (1 - t)),
                center = Offset(size.width * (0.68f + 0.08f * t), size.height * 1.05f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.05f),
                radius = size.height * 0.18f,
                center = Offset(size.width * (0.45f - 0.08f * t), size.height * (0.15f + 0.1f * t)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(if (compact) 20.dp else 28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(date, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Text(
                    greeting,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(motto, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    HeroChip("⏱ Bugün ${formatStudy(todayMs)}")
                    HeroChip("📚 $docCount belge")
                }
            }
            if (!compact) AppLogo(84.dp, Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text("$action →") }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ContinueCard(
    doc: DocRef,
    page: Int,
    openedAt: Long,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
    onOpen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val ago = remember(openedAt) {
        DateUtils.getRelativeTimeSpanString(openedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val thumbShape = RoundedCornerShape(8.dp)
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 20.dp),
        modifier = Modifier.fillMaxWidth().card().combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }).padding(if (narrow) 12.dp else 16.dp),
    ) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
        Box(
            Modifier
                .width(if (narrow) 52.dp else 84.dp)
                .aspectRatio(0.75f)
                .clip(thumbShape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
        ) {
            Thumbnail(doc)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text("Kaldığın yerden devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(
                doc.title,
                style = if (narrow) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (narrow) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Sayfa ${page + 1} · $ago",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (narrow) {
            androidx.compose.material3.FilledIconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Devam et")
            }
        } else {
            Button(onClick = onOpen) { Text("Devam et") }
        }
    }
}

/** Bütün belgelerde tekrar zamanı gelmiş bilgi kartları. */
@Composable
private fun ReviewCard(count: Int, onStart: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7048C8), Color(0xFF2F5BD3))))
            .clickable(onClick = onStart)
            .padding(18.dp),
    ) {
        Text("🃏", fontSize = 34.sp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("Bugünkü tekrar", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$count bilgi kartının tekrar zamanı geldi. Birkaç dakikada bitir, unutmadan pekiştir.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2F5BD3)),
        ) { Text("Başla") }
    }
}

/** Bu haftanın kaynakları yapılacaklar listesi olarak: ilerleme + ilk birkaç yapılmamış. */
@Composable
private fun WeekTasksCard(
    week: Int,
    tasks: List<Triple<String, String, String>>,
    done: Set<String>,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
) {
    val doneCount = tasks.count { it.first in done }
    val progress by animateFloatAsState(doneCount / tasks.size.toFloat(), tween(600), label = "weekTasks")
    // Yeni işaretlenen satır hemen kaybolmasın: kart açıkken görünen satırlar sabit kalır.
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val shown = remember(tasks) { tasks.filter { it.first !in done }.take(if (narrow) 3 else 4).ifEmpty { tasks.takeLast(2) } }
    Column(Modifier.fillMaxWidth().card().padding(if (narrow) 14.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (narrow) 2.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hafta $week listesi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (doneCount == tasks.size) "Hepsi tamam, harikasın 🎉" else "$doneCount / ${tasks.size} tamamlandı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAll) { Text("Tümü →") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        shown.forEach { (key, title, _) ->
            val checked = key in done
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggle(key) },
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(key) })
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Ana sayfanın üstünde "her şeyde ara" çubuğu (dokununca arama ekranı açılır). */
@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.98f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Ara", onClick = onClick)
            .semantics { contentDescription = "Ara" }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Belgelerde, notlarda, kartlarda ara…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun BackupNudge(onPick: () -> Unit, onLater: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp),
    ) {
        Text("☁️", fontSize = 30.sp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                "Notların güvende mi?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "Otomatik yedeği aç: notların her gün seçtiğin klasöre yedeklenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onPick) { Text("Klasör seç") }
                TextButton(onClick = onLater) { Text("Sonra") }
            }
        }
    }
}

@Composable
private fun StartCard(onNotebook: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().card().padding(24.dp)) {
        Text("Başlayalım 🎉", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Kütüphanen henüz boş. Boş bir defter aç ya da ders PDF'lerini içe aktar; " +
                "Word dosyalarını önce PDF'e çevirmen yeterli.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = onNotebook) { Text("Yeni defter") }
            OutlinedButton(onClick = onImport) { Text("PDF içe aktar") }
        }
    }
}

private class QuickAction(
    val icon: Painter,
    val title: String,
    val text: String,
    val color: Color,
    /** Aç/kapa kutusu için: null değilse kutuda "Açık/Kapalı" görünür ve açıkken renkli olur. */
    val toggled: Boolean? = null,
    val onClick: () -> Unit,
)

@Composable
private fun QuickActions(actions: List<QuickAction>) {
    BoxWithConstraints {
        // Telefonda 3 sütun kompakt kutu (açıklamasız); tablette açıklamalı geniş kutular.
        val compact = maxWidth < 600.dp
        val columns = if (maxWidth > 900.dp) 5 else if (compact) 3 else 4
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
            actions.chunked(columns).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    row.forEachIndexed { c, a ->
                        val m = Modifier.weight(1f).appear(2 + r * columns + c).pressScale()
                        if (compact) CompactTile(a, m) else ActionTile(a, m)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CompactTile(a: QuickAction, modifier: Modifier) {
    val on = a.toggled == true
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) a.color.copy(alpha = 0.16f) else cardBackground())
            .border(
                if (on) 2.dp else 1.dp,
                if (on) a.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = a.onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (on) a.color else a.color.copy(alpha = 0.15f)),
        ) {
            Icon(a.icon, contentDescription = null, tint = if (on) Color.White else a.color, modifier = Modifier.size(22.dp))
        }
        Text(
            a.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (a.toggled != null) {
            Text(
                if (on) "Açık" else "Kapalı",
                style = MaterialTheme.typography.labelSmall,
                color = if (on) a.color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ActionTile(a: QuickAction, modifier: Modifier) {
    val on = a.toggled == true
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .card()
            .then(if (on) Modifier.background(a.color.copy(alpha = 0.12f)) else Modifier)
            .clickable(onClick = a.onClick)
            .padding(18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (on) a.color else a.color.copy(alpha = 0.15f)),
        ) {
            Icon(a.icon, contentDescription = null, tint = if (on) Color.White else a.color, modifier = Modifier.size(24.dp))
        }
        Text(
            a.title + if (a.toggled != null) (if (on) " · Açık" else " · Kapalı") else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            a.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Telefonda yaklaşan sınav: yan yana kaydırılan küçük kart. */
@Composable
private fun ExamMini(e: com.sounote.app.data.Exam, modifier: Modifier, onClick: () -> Unit) {
    val day = remember(e.time) { SimpleDateFormat("d MMM", TR).format(Date(e.time)) }
    val c = countdownColor(e.time)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(210.dp)
            .card()
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.kindEmoji, fontSize = 18.sp)
            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text(
                countdownText(e.time),
                color = c,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(
            e.title.ifBlank { e.course },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp),
        )
    }
}

@Composable
private fun WeekCard(days: List<Pair<Long, Long>>, modifier: Modifier) {
    val dayName = remember { SimpleDateFormat("EEE", TR) }
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val compactChart = LocalConfiguration.current.screenWidthDp < 600
    // Çubuklar açılışta aşağıdan büyür.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, delayMillis = 250, easing = FastOutSlowInEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.card().padding(20.dp)) {
        Text("Bu hafta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Toplam ${formatStudy(days.sumOf { it.second })} çalıştın",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            days.reversed().forEachIndexed { i, (t, ms) ->
                val today = i == days.size - 1
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(((if (compactChart) 56f else 84f) * ms / max * grow.value).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                    )
                    Text(
                        dayName.format(Date(t)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TipCard(modifier: Modifier) {
    var index by rememberSaveable { mutableStateOf(dayOfYear() % TIPS.size) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.card().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 22.sp)
            Text(
                "İpucu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (fadeIn(tween(Motion.MEDIUM)) + slideInHorizontally { it / 6 }).togetherWith(fadeOut(tween(Motion.SHORT)) + slideOutHorizontally { -it / 6 })
            },
            label = "tip",
        ) { i ->
            Text(TIPS[i], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 72.dp))
        }
        TextButton(onClick = { index = (index + 1) % TIPS.size }, modifier = Modifier.align(Alignment.End)) {
            Text("Sonraki ipucu")
        }
    }
}
