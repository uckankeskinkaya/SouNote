@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import com.sounote.app.data.Course
import com.sounote.app.data.DocRef
import com.sounote.app.data.HomeModule
import com.sounote.app.data.QuickItem
import com.sounote.app.data.TextNote
import com.sounote.app.data.UserFolder
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
    "Lasso ile seçtiğin bölgeyi \"Asistana sor\" ile açıklatabilir ya da çevirtebilirsin.",
    "Belgeyi sadece okuyacaksan 📖 okuma moduna geç: araç çubuğu gizlenir, kalem kaydırır.",
    "Bağlı klasöre attığın yeni PDF'ler kütüphanede kendiliğinden görünür.",
    "Otomatik yedeği Drive'daki bir klasöre bağlarsan tablet bozulsa da notların güvende.",
    "Sayfa panelinden sayfa ekleyip silebilir, sürükleyip yerini değiştirebilirsin.",
    "Klavye bağlıysa Ctrl+Z geri alır, PageDown sonraki sayfaya geçer.",
    "Yazı stüdyosunda essay şablonunu kullan; yazma koçu dil bilgisi ve yapı önerir.",
    "⭐ ile belgeleri, klasörleri ve dersleri favorilere ekle; ana sayfada hızlı erişimde görünür.",
    "Ana sayfayı düzenleyebilirsin: en alttaki \"Ana sayfayı düzenle\" ile modülleri sırala ya da gizle.",
    "Gece okuma modu sayfaları koyulaştırır, akşam gözünü yormaz.",
    "Kalemi olmayan arkadaşın Ayarlar'dan \"Parmakla çiz\"i açabilir.",
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

/** Hızlı erişimde görünen, çözümlenmiş öğe. */
internal sealed interface QuickEntry {
    val item: QuickItem
    val title: String

    data class Doc(override val item: QuickItem, val doc: DocRef) : QuickEntry {
        override val title get() = doc.title
    }
    data class Folder(override val item: QuickItem, val folder: UserFolder) : QuickEntry {
        override val title get() = folder.name
    }
    data class Lesson(override val item: QuickItem, val course: Course) : QuickEntry {
        override val title get() = course.name
    }
    data class Note(override val item: QuickItem, val note: TextNote) : QuickEntry {
        override val title get() = note.displayTitle
    }
}

/** Favorileri gerçek içerikle eşleştirir; silinmiş/çöpteki içerik görünmez (favori kaydı durur). */
internal fun resolveQuick(vm: LibraryViewModel): List<QuickEntry> {
    val list = vm.quickItems.mapNotNull { q ->
        when (q.type) {
            QuickItem.DOC -> vm.imported.find { it.id == q.id }?.let { QuickEntry.Doc(q, it) }
            QuickItem.FOLDER -> vm.userFolders.find { it.id == q.id }?.let { QuickEntry.Folder(q, it) }
            QuickItem.COURSE -> vm.catalog.courses.find { it.id == q.id }?.let { QuickEntry.Lesson(q, it) }
            QuickItem.NOTE -> vm.textNotes.find { it.id == q.id }?.let { QuickEntry.Note(q, it) }
            else -> null
        }
    }
    return list.filter { it.item.pinned } + list.filter { !it.item.pinned }
}

/** Hızlı erişim öğesini açar. */
internal fun openQuick(vm: LibraryViewModel, e: QuickEntry) {
    when (e) {
        is QuickEntry.Doc -> vm.open(e.doc)
        is QuickEntry.Folder -> {
            vm.openUserFolder(e.folder.id)
            vm.tab = LibraryViewModel.Tab.LIBRARY
        }
        is QuickEntry.Lesson -> {
            vm.focusCourse = e.course.id
            vm.tab = LibraryViewModel.Tab.COURSES
        }
        is QuickEntry.Note -> vm.openTextNote(e.note)
    }
}

/** Son kullanılanlar: belgeler, yazılı notlar ve dersler birlikte. */
private sealed interface RecentEntry {
    val time: Long

    data class Doc(val doc: DocRef, override val time: Long) : RecentEntry
    data class Note(val note: TextNote) : RecentEntry {
        override val time get() = note.updated
    }
    data class Lesson(val course: Course, override val time: Long) : RecentEntry
}

/** Ana sayfa: kullanıcının sıraladığı modüller (Ana sayfayı düzenle). */
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
    var gradesOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var layoutOpen by remember { mutableStateOf(false) }
    var quickEdit by remember { mutableStateOf(false) }
    var graderOpen by remember { mutableStateOf(false) }
    // Bugünkü tekrar: açılınca bütün belgelerin zamanı gelmiş kartları yüklenir.
    var review by remember { mutableStateOf<List<Pair<DocRef, com.sounote.app.data.Card>>?>(null) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<DocRef?>(null) }
    var deleting by remember { mutableStateOf<DocRef?>(null) }
    var deletingText by remember { mutableStateOf<TextNote?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val docMenu: @Composable (DocRef, () -> Unit) -> Unit = { doc, dismiss ->
        DropdownMenuItem(text = { Text("Aç") }, onClick = { dismiss(); vm.open(doc) })
        val fav = vm.isFavorite(QuickItem.DOC, doc.id)
        DropdownMenuItem(
            text = { Text(if (fav) "Favorilerden çıkar" else "Favorilere ekle") },
            onClick = { dismiss(); vm.toggleFavorite(QuickItem.DOC, doc.id) },
        )
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
    val space = LocalSpacing.current

    val recentDocs = remember(vm.imported) {
        vm.imported.filter { vm.lastOpened(it) > 0 }.sortedByDescending { vm.lastOpened(it) }.take(12)
    }
    val recentAll = remember(vm.imported, vm.textNotes, vm.catalog) {
        val docs = recentDocs.map { RecentEntry.Doc(it, vm.lastOpened(it)) }
        val notes = vm.textNotes.take(8).map { RecentEntry.Note(it) }
        val courses = vm.catalog.courses.mapNotNull { c -> vm.lastCourseOpen(c.id).takeIf { it > 0 }?.let { RecentEntry.Lesson(c, it) } }
        (docs + notes + courses).sortedByDescending { it.time }.take(14)
    }
    val days = remember(vm.imported) { vm.studyDays(7) }
    val streak = vm.streak
    val empty = vm.loaded && vm.imported.isEmpty() && vm.rootFolders.isEmpty()
    val quick = resolveQuick(vm)

    @Composable
    fun module(m: HomeModule, index: Int, modifier: Modifier) {
        when (m) {
            HomeModule.HERO -> Appear(index, modifier) {
                Hero(greeting(vm.userName), days.first().second, streak, vm.activeToday, vm.imported.size)
            }
            HomeModule.QUICK_ACCESS -> Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionHeader("Hızlı erişim", action = if (quick.isNotEmpty()) "Düzenle" else null, onAction = { quickEdit = true })
                if (quick.isEmpty()) {
                    Text(
                        "Belgeleri, klasörleri, dersleri ve notları ⭐ ile favorilere ekle; burada tek dokunuşla açılır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().card().padding(space.card),
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.md), contentPadding = PaddingValues(end = 8.dp)) {
                        items(quick, key = { it.item.key }) { e ->
                            QuickTile(e, vm, Modifier.animateItem().pressScale(), onOpen = { openQuick(vm, e) })
                        }
                    }
                }
            }
            HomeModule.CONTINUE -> {
                val last = recentDocs.firstOrNull()
                if (empty) {
                    Appear(index, modifier) { StartCard(onNotebook = { newNotebook = true }, onImport = importPdf) }
                } else if (last != null) {
                    Appear(index, modifier.pressScale(0.98f)) {
                        ContinueCard(last, vm.lastPage(last), vm.lastOpened(last), menu = { d -> docMenu(last, d) }) { vm.open(last) }
                    }
                }
            }
            HomeModule.UPCOMING -> {
                val now = System.currentTimeMillis()
                val narrowHome = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
                val next = vm.allExams.filter { it.time > now && !vm.isDone(it) }.take(if (narrowHome) 5 else 3)
                if (next.isNotEmpty()) {
                    Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                        SectionHeader("Yaklaşan sınav ve ödevler", action = "Planlayıcı") { vm.tab = LibraryViewModel.Tab.EXAMS }
                        if (narrowHome) {
                            // Telefonda yan yana kaydırılan küçük kartlar (alt alta büyük kartlar çok yer kaplıyordu)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                                items(next, key = { it.id }) { e ->
                                    ExamMini(e, vm, Modifier.animateItem().pressScale()) { vm.tab = LibraryViewModel.Tab.EXAMS }
                                }
                            }
                        } else {
                            next.forEach { e ->
                                Box(Modifier.pressScale(0.98f)) {
                                    ExamCard(e, vm, onClick = { vm.tab = LibraryViewModel.Tab.EXAMS }, onLongClick = {})
                                }
                            }
                        }
                    }
                }
            }
            HomeModule.REVIEW -> if (vm.dueCount > 0) {
                Appear(index, modifier.pressScale(0.98f)) {
                    ReviewCard(vm.dueCount) { scope.launch { review = vm.dueCards().ifEmpty { null } } }
                }
            }
            HomeModule.WEEK_TASKS -> {
                // Bu haftanın kaynakları henüz girilmediyse (ör. hafta sonu) en son dolu hafta gösterilir.
                val (week, weekTasks) = remember(vm.catalog) {
                    val now = vm.catalog.weekOf(System.currentTimeMillis())
                    (now downTo 1).asSequence().map { it to vm.weekTasks(it) }.firstOrNull { it.second.isNotEmpty() } ?: (0 to emptyList())
                }
                if (weekTasks.isNotEmpty()) {
                    Appear(index, modifier) {
                        WeekTasksCard(week, weekTasks, vm.doneItems, onToggle = vm::toggleDone) {
                            vm.coursesByWeek = true
                            vm.tab = LibraryViewModel.Tab.COURSES
                        }
                    }
                }
            }
            HomeModule.ACTIONS -> Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionHeader("Hızlı başla")
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
                        QuickAction(rememberVectorPainter(Icons.Default.Add), "Yeni defter", "Şablonlu boş sayfalar", Color(0xFF4F46E5)) {
                            newNotebook = true
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Edit), "Hızlı not", "Hemen yazmaya başla", Color(0xFFE0893A)) {
                            vm.quickNote()
                        },
                        QuickAction(painterResource(R.drawable.ic_text), "Yazı stüdyosu", "Essay ve yazılı notlar", Color(0xFF8E63CE)) {
                            vm.tab = LibraryViewModel.Tab.WRITING
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Face), "Asistan", "İngilizce ve ders soruları sor", Color(0xFF00897B)) {
                            vm.openAssistant()
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Star), "Essay puanla", "Süreyle yaz, asistan L4 ölçütleriyle puanlasın", Color(0xFFC2357A)) {
                            graderOpen = true
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
            }
            HomeModule.RECENT -> if (recentAll.size > 1) {
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                    SectionHeader("Son kullanılanlar", action = "Kütüphane") { vm.tab = LibraryViewModel.Tab.LIBRARY }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.lg), contentPadding = PaddingValues(end = 8.dp)) {
                        items(recentAll, key = {
                            when (it) {
                                is RecentEntry.Doc -> "d" + it.doc.id
                                is RecentEntry.Note -> "n" + it.note.id
                                is RecentEntry.Lesson -> "c" + it.course.id
                            }
                        }) { r ->
                            Box(Modifier.width(132.dp).animateItem().pressScale()) {
                                when (r) {
                                    is RecentEntry.Doc -> DocCard(
                                        r.doc,
                                        onClick = { vm.open(r.doc) },
                                        menu = { d -> docMenu(r.doc, d) },
                                        favorite = vm.isFavorite(QuickItem.DOC, r.doc.id),
                                    )
                                    is RecentEntry.Note -> TextNoteCard(
                                        r.note,
                                        onClick = { vm.openTextNote(r.note) },
                                        menu = { d -> TextNoteMenuItems(r.note, vm, d) { deletingText = r.note } },
                                    )
                                    is RecentEntry.Lesson -> CourseMiniCard(r.course) {
                                        vm.focusCourse = r.course.id
                                        vm.tab = LibraryViewModel.Tab.COURSES
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HomeModule.NOTES -> if (vm.textNotes.isNotEmpty()) {
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                    SectionHeader("Yazılı notlar", action = "Yazı stüdyosu") { vm.tab = LibraryViewModel.Tab.WRITING }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.lg), contentPadding = PaddingValues(end = 8.dp)) {
                        items(vm.textNotes.take(12), key = { "t" + it.id }) { n ->
                            Box(Modifier.width(132.dp).animateItem().pressScale()) {
                                TextNoteCard(n, onClick = { vm.openTextNote(n) }, menu = { d -> TextNoteMenuItems(n, vm, d) { deletingText = n } })
                            }
                        }
                    }
                }
            }
            HomeModule.STATS -> Appear(index, modifier) { WeekCard(days, vm, Modifier.fillMaxWidth()) { statsOpen = true } }
            HomeModule.TIP -> Appear(index, modifier) { TipCard(Modifier.fillMaxWidth()) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val narrow = maxWidth < 600.dp
        val wide = maxWidth >= 900.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(space.section),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 16.dp else 28.dp, vertical = if (narrow) 12.dp else 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (narrow) space.xl else space.section),
                modifier = Modifier.widthIn(max = 1180.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SearchBarButton { vm.searchOpen = true } }
                    IconButton(onClick = { vm.tab = LibraryViewModel.Tab.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
                val modules = vm.homeLayout.visible
                // Geniş ekranda küçük kartlar yan yana: art arda gelen iki "yarım" modül aynı satıra.
                val half = setOf(HomeModule.STATS, HomeModule.TIP, HomeModule.WEEK_TASKS, HomeModule.UPCOMING, HomeModule.CONTINUE)
                var i = 0
                while (i < modules.size) {
                    val m = modules[i]
                    if (i == 0) {
                        key(m) { module(m, i, Modifier.fillMaxWidth()) }
                        if (vm.showBackupNudge) Appear(i + 1) { BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge) }
                        i++
                        continue
                    }
                    val next = modules.getOrNull(i + 1)
                    if (wide && m in half && next != null && next in half) {
                        Row(horizontalArrangement = Arrangement.spacedBy(space.xl), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(1f)) { key(m) { module(m, i, Modifier.fillMaxWidth()) } }
                            Box(Modifier.weight(1f)) { key(next) { module(next, i + 1, Modifier.fillMaxWidth()) } }
                        }
                        i += 2
                    } else {
                        key(m) { module(m, i, Modifier.fillMaxWidth()) }
                        i++
                    }
                }
                if (modules.isEmpty() && vm.showBackupNudge) {
                    BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge)
                }
                TextButton(onClick = { layoutOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("✏️ Ana sayfayı düzenle")
                }
            }
        }
    }

    if (gradesOpen) GradesDialog(vm) { gradesOpen = false }
    if (graderOpen) EssayGraderScreen(vm) { graderOpen = false }
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
    if (statsOpen) StatsDialog(vm) { statsOpen = false }
    if (layoutOpen) HomeLayoutDialog(vm) { layoutOpen = false }
    if (quickEdit) QuickAccessDialog(vm) { quickEdit = false }
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
private fun Hero(greeting: String, todayMs: Long, streak: Int, activeToday: Boolean, docCount: Int) {
    val date = remember { SimpleDateFormat("d MMMM EEEE", TR).format(Date()) }
    val motto = remember { MOTTOS[dayOfYear() % MOTTOS.size] }
    val sou = LocalSouColors.current
    val full = LocalMotionLevel.current == MotionLevel.FULL
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(sou.hero))) {
        val compact = maxWidth < 420.dp
        // Arka plandaki daireler yavaşça süzülür (animasyon azaltılmışsa durur).
        val t = if (full) {
            val drift = rememberInfiniteTransition(label = "hero")
            drift.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift").value
        } else {
            0.5f
        }
        Canvas(Modifier.matchParentSize()) {
            // Koyu temada çok hafif aurora parıltısı
            if (sou.aurora != Color.Transparent) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(sou.aurora.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * (0.85f - 0.1f * t), size.height * 0.1f),
                        radius = size.height * 1.4f,
                    ),
                    radius = size.height * 1.4f,
                    center = Offset(size.width * (0.85f - 0.1f * t), size.height * 0.1f),
                )
            }
            drawCircle(
                Color.White.copy(alpha = 0.07f),
                radius = size.height * (0.85f + 0.1f * t),
                center = Offset(size.width * (0.92f + 0.05f * t), size.height * (-0.05f + 0.1f * t)),
            )
            drawCircle(
                Color.White.copy(alpha = 0.05f),
                radius = size.height * (0.42f + 0.06f * (1 - t)),
                center = Offset(size.width * (0.68f + 0.08f * t), size.height * 1.05f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(if (compact) 20.dp else 28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(date, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Text(
                    greeting,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                )
                Text(motto, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    HeroChip(
                        when {
                            streak > 0 && activeToday -> "🔥 $streak gün seri"
                            streak > 0 -> "🔥 $streak gün · bugün çalış, seri bozulmasın"
                            else -> "🔥 Seriye bugün başla"
                        },
                    )
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

/** Hızlı erişim kutucuğu: belge kapağı, klasör rengi, ders emojisi ya da not. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(e: QuickEntry, vm: LibraryViewModel, modifier: Modifier, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val (typeLabel, color) = when (e) {
        is QuickEntry.Doc -> "Belge" to MaterialTheme.colorScheme.primary
        is QuickEntry.Folder -> "Klasör" to Color(e.folder.color)
        is QuickEntry.Lesson -> "Ders" to Color(e.course.color)
        is QuickEntry.Note -> "Not" to Color(0xFF8E63CE)
    }
    Column(
        modifier
            .width(150.dp)
            .card(18.dp)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            when (e) {
                is QuickEntry.Doc -> Box(Modifier.fillMaxSize().background(Color.White)) { Thumbnail(e.doc) }
                is QuickEntry.Folder -> Text(e.folder.icon.ifEmpty { "📁" }, fontSize = 34.sp)
                is QuickEntry.Lesson -> Text(e.course.emoji.ifEmpty { "📘" }, fontSize = 34.sp)
                is QuickEntry.Note -> Text(if (e.note.hasSketch) "🎨" else "📝", fontSize = 34.sp)
            }
            if (e.item.pinned) {
                Text("📌", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Aç") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(
                text = { Text(if (e.item.pinned) "Sabitlemeyi kaldır" else "Başa sabitle") },
                onClick = { menu = false; vm.togglePinned(e.item) },
            )
            DropdownMenuItem(text = { Text("Sola taşı") }, onClick = { menu = false; vm.moveQuick(e.item, -1) })
            DropdownMenuItem(text = { Text("Sağa taşı") }, onClick = { menu = false; vm.moveQuick(e.item, 1) })
            DropdownMenuItem(text = { Text("Favorilerden çıkar") }, onClick = { menu = false; vm.removeQuick(e.item) })
        }
    }
}

/** Son kullanılanlarda ders kartı. */
@Composable
private fun CourseMiniCard(course: Course, onClick: () -> Unit) {
    val color = Color(course.color)
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)))),
        ) { Text(course.emoji.ifEmpty { "📘" }, fontSize = 44.sp) }
        Text(
            course.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
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
    BoxWithConstraints {
        val compact = maxWidth < 420.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
            modifier = Modifier.fillMaxWidth().card().combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }).padding(16.dp),
        ) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
            Box(
                Modifier
                    .width(if (compact) 64.dp else 84.dp)
                    .aspectRatio(0.75f)
                    .clip(thumbShape)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
            ) {
                Thumbnail(doc)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("Kaldığın yerden devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Sayfa ${page + 1} · $ago",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (compact) Button(onClick = onOpen, modifier = Modifier.padding(top = 4.dp)) { Text("Devam et") }
            }
            if (!compact) Button(onClick = onOpen) { Text("Devam et") }
        }
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
    // Yeni işaretlenen satır hemen kaybolmasın: kart açıkken görünen satırlar sabit kalır.
    val shown = remember(tasks) { tasks.filter { it.first !in done }.take(4).ifEmpty { tasks.takeLast(2) } }
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(doneCount / tasks.size.toFloat(), size = 44.dp, stroke = 5.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Hafta $week görevleri", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (doneCount == tasks.size) "Hepsi tamam, harikasın 🎉" else "$doneCount / ${tasks.size} tamamlandı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAll) { Text("Tümü →") }
        }
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
            "Ders, belge, not, sınav ara…",
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
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "Otomatik yedeği aç: her gün bütün notların seçtiğin klasöre (ör. Drive) yedeklenir. Tablet bozulsa da kaybolmaz.",
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
        Text("Başlayalım 🎉", style = MaterialTheme.typography.titleLarge)
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
    /** Aç/kapa kutusu için: null değilse "Açık/Kapalı" yazar ve açıkken renkli olur. */
    val toggled: Boolean? = null,
    val onClick: () -> Unit,
)

@Composable
private fun QuickActions(actions: List<QuickAction>) {
    val space = LocalSpacing.current
    BoxWithConstraints {
        val columns = if (maxWidth > 900.dp) 4 else if (maxWidth > 560.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            actions.chunked(columns).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                    row.forEachIndexed { c, a -> ActionTile(a, Modifier.weight(1f).appear(r * columns + c).pressScale()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(a: QuickAction, modifier: Modifier) {
    val space = LocalSpacing.current
    val on = a.toggled == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .card(18.dp)
            .then(if (on) Modifier.background(a.color.copy(alpha = 0.14f)).border(2.dp, a.color, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = a.onClick)
            .padding(horizontal = space.lg, vertical = space.md)
            .heightIn(min = 48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (on) a.color else a.color.copy(alpha = 0.15f)),
        ) {
            Icon(a.icon, contentDescription = null, tint = if (on) Color.White else a.color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                a.title + if (a.toggled != null) (if (on) " · Açık" else " · Kapalı") else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
}

@Composable
private fun WeekCard(days: List<Pair<Long, Long>>, vm: LibraryViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val dayName = remember { SimpleDateFormat("EEE", TR) }
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    // Çubuklar açılışta aşağıdan büyür (animasyon kapalıysa hemen tam boy).
    val animate = LocalMotionLevel.current != MotionLevel.OFF
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) grow.animateTo(1f, tween(700, delayMillis = 250, easing = FastOutSlowInEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.card().clickable(onClick = onOpen).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bu hafta", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Toplam ${formatStudy(days.sumOf { it.second })} çalıştın · 🔥 Seri ${vm.streak} gün · en uzun ${vm.bestStreak}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Ayrıntı →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
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
                            .height((84f * ms / max * grow.value).dp.coerceAtLeast(4.dp))
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
                    // Seriye sayılan gün
                    Text(if (vm.isActiveDay(t)) "🔥" else "·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TipCard(modifier: Modifier) {
    var index by rememberSaveable { mutableStateOf(dayOfYear() % TIPS.size) }
    val motion = LocalMotionLevel.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.card().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 22.sp)
            Text("İpucu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                if (motion == MotionLevel.OFF) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.SHORT))
                }
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

/** Ana sayfa düzeni: modülleri sırala, gizle ya da varsayılana dön. */
@Composable
internal fun HomeLayoutDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val layout = vm.homeLayout
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ana sayfayı düzenle") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Text(
                        "Okları kullanarak sırala, anahtarla gizle ya da göster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(layout.order, key = { _, m -> m.name }) { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().animateItem()) {
                        Text(m.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.updateHomeLayout(layout.move(m, -1)) }, enabled = i > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                        }
                        IconButton(onClick = { vm.updateHomeLayout(layout.move(m, 1)) }, enabled = i < layout.order.size - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                        }
                        Switch(checked = m !in layout.hidden, onCheckedChange = { vm.updateHomeLayout(layout.setVisible(m, it)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = { vm.resetHomeLayout() }) { Text("Varsayılana dön") } },
    )
}

/** Hızlı erişim düzeni: sırala, sabitle, çıkar. */
@Composable
internal fun QuickAccessDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val entries = vm.quickItems.mapNotNull { q -> resolveQuick(vm).find { it.item.key == q.key } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hızlı erişim") },
        text = {
            if (entries.isEmpty()) {
                Text("Henüz favori yok. Belge, klasör, ders ya da notları ⭐ ile ekle.")
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(entries, key = { _, e -> e.item.key }) { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().animateItem()) {
                            Text(if (e.item.pinned) "📌" else "⭐", fontSize = 16.sp, modifier = Modifier.width(28.dp))
                            Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.moveQuick(e.item, -1) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                            }
                            IconButton(onClick = { vm.moveQuick(e.item, 1) }, enabled = i < entries.size - 1) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                            }
                            TextButton(onClick = { vm.togglePinned(e.item) }) { Text(if (e.item.pinned) "Çöz" else "Sabitle") }
                            TextButton(onClick = { vm.removeQuick(e.item) }) { Text("Çıkar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}

/** Bütün belgelerde tekrar zamanı gelmiş bilgi kartları. */
@Composable
private fun ReviewCard(count: Int, onStart: () -> Unit) {
    val hero = LocalSouColors.current.hero
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(hero))
            .clickable(onClick = onStart)
            .padding(18.dp),
    ) {
        Text("🃏", fontSize = 34.sp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("Bugünkü tekrar", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                "$count bilgi kartının tekrar zamanı geldi. Birkaç dakikada bitir, unutmadan pekiştir.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = hero.first()),
        ) { Text("Başla") }
    }
}

/** Telefonda yaklaşan sınav: yan yana kaydırılan küçük kart. */
@Composable
private fun ExamMini(e: com.sounote.app.data.Exam, vm: LibraryViewModel, modifier: Modifier, onClick: () -> Unit) {
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
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color(vm.courseColor(e.course))))
            Text(" " + e.kindEmoji, fontSize = 16.sp)
            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text(
                countdownText(e.time),
                color = c,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(
            e.title.ifBlank { e.course },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp),
        )
    }
}
