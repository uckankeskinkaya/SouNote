@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.DocRef
import com.sounote.app.data.HomeModule
import com.sounote.app.data.QuickItem
import com.sounote.app.data.TextNote
import com.sounote.app.data.UserFolder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

private val TIPS = listOf(
    "Kalemi çizginin sonunda bir an beklet: düz çizgi, daire ya da dikdörtgen olur.",
    "İki parmakla dokun: geri al. Üç parmakla: ileri al.",
    "Kalemin yan tuşuyla çizersen silgi olur.",
    "Ezber bandıyla bir yeri kapat; dokununca açılır.",
    "Ses kaydı açıkken aldığın nota dokun: kayıt o andan çalar.",
    "Lasso ile seç, \"Karta çevir\" de: bilgi kartı olur.",
    "Lasso ile seçtiğin bölgeyi asistana açıklatabilirsin.",
    "Sadece okuyacaksan okuma moduna geç.",
    "Bağlı klasöre attığın PDF'ler kütüphanede kendiliğinden görünür.",
    "Otomatik yedeği Drive'daki bir klasöre bağla.",
    "Klavyede Ctrl+Z geri alır, PageDown sonraki sayfaya geçer.",
    "Yıldızla favorilere eklediklerin ana sayfada görünür.",
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

/** Ana sayfa: sade karşılama ve kullanıcının sıraladığı modüller (Ana sayfayı düzenle). */
@Composable
fun HomeScreen(vm: LibraryViewModel) {
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importPdfs(uris, openAfter = uris.size == 1)
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.enableAutoBackup(uri)
    }
    var newNotebook by remember { mutableStateOf(false) }
    var newMenuOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var layoutOpen by remember { mutableStateOf(false) }
    var quickEdit by remember { mutableStateOf(false) }
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
        DropdownMenuItem(text = { Text("Notlu PDF'i paylaş") }, onClick = { dismiss(); vm.export(doc) { sharePdfFile(context, it) } })
        DropdownMenuItem(text = { Text("SouNote'lu arkadaşa gönder") }, onClick = { dismiss(); vm.sharePackage(doc) { sharePackageFile(context, it) } })
        DropdownMenuItem(text = { Text("Sil", color = MaterialTheme.colorScheme.error) }, onClick = { dismiss(); deleting = doc })
    }
    val importPdf = { pickPdfs.launch(arrayOf("application/pdf")) }
    val space = LocalSpacing.current

    val recentDocs = remember(vm.imported, vm.recentsClearedAt, vm.recentHidden) {
        vm.imported.filter { vm.recentVisible("d" + it.id, vm.lastOpened(it)) }.sortedByDescending { vm.lastOpened(it) }.take(12)
    }
    val recentAll = remember(vm.imported, vm.textNotes, vm.catalog, vm.recentsClearedAt, vm.recentHidden) {
        val docs = recentDocs.map { RecentEntry.Doc(it, vm.lastOpened(it)) }
        val notes = vm.textNotes.filter { vm.recentVisible("n" + it.id, it.updated) }.take(8).map { RecentEntry.Note(it) }
        val courses = vm.catalog.courses.mapNotNull { c ->
            vm.lastCourseOpen(c.id).takeIf { vm.recentVisible("c" + c.id, it) }?.let { RecentEntry.Lesson(c, it) }
        }
        (docs + notes + courses).sortedByDescending { it.time }.take(14)
    }
    var clearRecents by remember { mutableStateOf(false) }
    if (clearRecents) {
        AlertDialog(
            onDismissRequest = { clearRecents = false },
            title = { Text("Son kullanılanlar temizlensin mi?") },
            text = { Text("Liste boşalır; belgelerin, notların ve derslerin silinmez.") },
            confirmButton = { TextButton(onClick = { vm.clearRecents(); clearRecents = false }) { Text("Temizle") } },
            dismissButton = { TextButton(onClick = { clearRecents = false }) { Text("Vazgeç") } },
        )
    }
    val days = remember(vm.imported) { vm.studyDays(7) }
    val empty = vm.loaded && vm.imported.isEmpty() && vm.rootFolders.isEmpty()
    val quick = resolveQuick(vm)

    @Composable
    fun module(m: HomeModule, index: Int, modifier: Modifier) {
        when (m) {
            HomeModule.HERO -> {
                // Sınavdan önceki gün ve sınav günü: konular, PDF'ler, deneme sınavı
                examDayTarget(vm)?.let { e ->
                    Appear(index, modifier) {
                        ExamDayCard(vm, e, onReview = { scope.launch { review = vm.dueCards().ifEmpty { null } } })
                    }
                }
            }
            HomeModule.QUICK_ACCESS -> if (quick.isNotEmpty()) {
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Favoriler", action = "Düzenle", onAction = { quickEdit = true })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(quick, key = { it.item.key }) { e ->
                            QuickTile(e, vm, Modifier.animateItem().pressScale(), onOpen = { openQuick(vm, e) })
                        }
                    }
                }
            }
            HomeModule.CONTINUE -> {
                if (empty) {
                    Appear(index, modifier) { StartCard(onNotebook = { newNotebook = true }, onImport = importPdf) }
                } else if (recentDocs.isNotEmpty()) {
                    Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Devam et", action = "Kütüphane") { vm.tab = LibraryViewModel.Tab.LIBRARY }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            recentDocs.take(4).forEach { doc ->
                                Box(Modifier.weight(1f)) {
                                    DocCard(doc, onClick = { vm.open(doc) }, menu = { d -> docMenu(doc, d) }, favorite = vm.isFavorite(QuickItem.DOC, doc.id))
                                }
                            }
                            repeat(4 - recentDocs.take(4).size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            HomeModule.UPCOMING -> {
                val now = System.currentTimeMillis()
                val next = vm.allExams.filter { it.time > now && !vm.isDone(it) }.take(4)
                if (next.isNotEmpty()) {
                    Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Yaklaşanlar", action = "Takvim") { vm.tab = LibraryViewModel.Tab.EXAMS }
                        Column(Modifier.fillMaxWidth().card()) {
                            next.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ExamRow(e, vm) { vm.tab = LibraryViewModel.Tab.EXAMS }
                            }
                        }
                    }
                }
            }
            HomeModule.REVIEW -> if (vm.dueCount > 0) {
                Appear(index, modifier) {
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
            HomeModule.ACTIONS -> Appear(index, modifier) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Hızlı başla")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PracticeTile(SouIcons.NoteAdd, "Yeni defter", Color(0xFF3346D3), Modifier.weight(1f)) { newNotebook = true }
                        PracticeTile(SouIcons.PictureAsPdf, "PDF aç", Color(0xFFC2410C), Modifier.weight(1f)) { importPdf() }
                        PracticeTile(SouIcons.Bolt, "Hızlı not", Color(0xFFB45309), Modifier.weight(1f)) { vm.quickNote() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PracticeTile(SouIcons.StarShine, "Asistan", Color(0xFF7C3AED), Modifier.weight(1f)) { vm.openAssistant() }
                        PracticeTile(SouIcons.TouchApp, if (vm.tools.fingerDraw) "Parmakla yaz: açık" else "Parmakla yaz", Color(0xFF0F766E), Modifier.weight(1f)) {
                            vm.updateTools(vm.tools.copy(fingerDraw = !vm.tools.fingerDraw))
                            vm.showMessage(if (vm.tools.fingerDraw) "Parmakla yazma açık" else "Parmakla yazma kapalı")
                        }
                        PracticeTile(SouIcons.EditNote, "Yazı stüdyosu", Color(0xFFBE185D), Modifier.weight(1f)) { vm.tab = LibraryViewModel.Tab.WRITING }
                    }
                }
            }
            HomeModule.RECENT -> if (recentAll.size > 1) {
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Son kullanılanlar", action = "Temizle") { clearRecents = true }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.lg), contentPadding = PaddingValues(end = 8.dp)) {
                        items(recentAll, key = {
                            when (it) {
                                is RecentEntry.Doc -> "d" + it.doc.id
                                is RecentEntry.Note -> "n" + it.note.id
                                is RecentEntry.Lesson -> "c" + it.course.id
                            }
                        }) { r ->
                            Box(Modifier.width(120.dp).animateItem().pressScale()) {
                                when (r) {
                                    is RecentEntry.Doc -> DocCard(
                                        r.doc,
                                        onClick = { vm.open(r.doc) },
                                        menu = { d ->
                                            DropdownMenuItem(text = { Text("Listeden kaldır") }, onClick = { d(); vm.hideRecent("d" + r.doc.id) })
                                            docMenu(r.doc, d)
                                        },
                                        favorite = vm.isFavorite(QuickItem.DOC, r.doc.id),
                                    )
                                    is RecentEntry.Note -> TextNoteCard(
                                        r.note,
                                        onClick = { vm.openTextNote(r.note) },
                                        menu = { d ->
                                            DropdownMenuItem(text = { Text("Listeden kaldır") }, onClick = { d(); vm.hideRecent("n" + r.note.id) })
                                            TextNoteMenuItems(r.note, vm, d) { deletingText = r.note }
                                        },
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
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Yazılı notlar", action = "Tümü") { vm.tab = LibraryViewModel.Tab.WRITING }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.lg), contentPadding = PaddingValues(end = 8.dp)) {
                        items(vm.textNotes.take(12), key = { "t" + it.id }) { n ->
                            Box(Modifier.width(120.dp).animateItem().pressScale()) {
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
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 20.dp else 32.dp, vertical = if (narrow) 8.dp else 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(vm.userName), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val now = System.currentTimeMillis()
                        val week = vm.catalog.weekOf(now)
                        val date = SimpleDateFormat("d MMMM EEEE", TR).format(Date(now))
                        Text(date + if (week > 0) " · Hafta $week" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.searchOpen = true }) {
                        Icon(SouIcons.Search, contentDescription = "Ara")
                    }
                    if (narrow) {
                        IconButton(onClick = { vm.tab = LibraryViewModel.Tab.SETTINGS }) {
                            Icon(SouIcons.Settings, contentDescription = "Ayarlar")
                        }
                    }
                    Box {
                        Button(onClick = { newMenuOpen = true }) {
                            Icon(SouIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Yeni", modifier = Modifier.padding(start = 8.dp))
                        }
                        DropdownMenu(expanded = newMenuOpen, onDismissRequest = { newMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Yeni defter") }, onClick = { newMenuOpen = false; newNotebook = true })
                            DropdownMenuItem(text = { Text("PDF ekle") }, onClick = { newMenuOpen = false; importPdf() })
                            DropdownMenuItem(text = { Text("Hızlı not") }, onClick = { newMenuOpen = false; vm.quickNote() })
                        }
                    }
                }
                val modules = vm.homeLayout.visible
                if (wide) {
                    // Tablet yatay: iki sütun. Solda içerik (devam et, hızlı başla, son kullanılanlar),
                    // sağda takvim ve ilerleme (yaklaşanlar, tekrar, haftalık görevler, istatistik).
                    val right = setOf(HomeModule.UPCOMING, HomeModule.REVIEW, HomeModule.WEEK_TASKS, HomeModule.STATS, HomeModule.TIP)
                    val hero = modules.firstOrNull()?.takeIf { it == HomeModule.HERO }
                    if (hero != null) key(hero) { module(hero, 0, Modifier.fillMaxWidth()) }
                    if (vm.showBackupNudge) BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            modules.filter { it != hero && it !in right }.forEachIndexed { i, m -> key(m) { module(m, i + 1, Modifier.fillMaxWidth()) } }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            modules.filter { it != hero && it in right }.forEachIndexed { i, m -> key(m) { module(m, i + 1, Modifier.fillMaxWidth()) } }
                        }
                    }
                } else {
                    var i = 0
                    while (i < modules.size) {
                        val m = modules[i]
                        key(m) { module(m, i, Modifier.fillMaxWidth()) }
                        if (i == 0 && vm.showBackupNudge) Appear(i + 1) { BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge) }
                        i++
                    }
                }
                if (modules.isEmpty() && vm.showBackupNudge) {
                    BackupNudge(onPick = { pickBackupFolder.launch(null) }, onLater = vm::snoozeBackupNudge)
                }
                TextButton(onClick = { layoutOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(SouIcons.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Ana sayfayı düzenle", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

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
            text = { Text("\"${doc.title}\" çöp kutusuna taşınır; ${LibraryViewModel.TRASH_DAYS} gün içinde geri alabilirsin.") },
            confirmButton = { TextButton(onClick = { vm.delete(doc); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
    deletingText?.let { n ->
        AlertDialog(
            onDismissRequest = { deletingText = null },
            title = { Text("Not silinsin mi?") },
            text = { Text("\"${n.displayTitle}\" kalıcı olarak silinir.") },
            confirmButton = { TextButton(onClick = { vm.deleteTextNote(n); deletingText = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deletingText = null }) { Text("Vazgeç") } },
        )
    }
    if (newNotebook) {
        NotebookDialog(
            onCreate = { name, template, size, landscape, paper, cover ->
                newNotebook = false
                vm.createNotebook(name, template, size, landscape, paper, cover)
            },
            onDismiss = { newNotebook = false },
        )
    }
}

/** Favori kutucuğu: belge kapağı, klasör, ders çizimi ya da not. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(e: QuickEntry, vm: LibraryViewModel, modifier: Modifier, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(128.dp)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(88.dp).card(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (e) {
                is QuickEntry.Doc -> Box(Modifier.fillMaxSize().background(Color.White)) { Thumbnail(e.doc) }
                is QuickEntry.Folder -> Icon(SouIcons.FolderFilled, contentDescription = null, tint = Color(e.folder.color), modifier = Modifier.size(40.dp))
                is QuickEntry.Lesson -> CourseArt(e.course, 56.dp)
                is QuickEntry.Note -> Icon(SouIcons.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
            if (e.item.pinned) {
                Icon(
                    SouIcons.KeepFilled,
                    contentDescription = "Sabit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
                )
            }
        }
        Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).card(12.dp),
        ) { CourseArt(course, 72.dp) }
        Text(
            course.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Yaklaşan sınav veya ödev. */
@Composable
private fun ExamRow(e: com.sounote.app.data.Exam, vm: LibraryViewModel, onClick: () -> Unit) {
    val type = examType(e)
    val color = type.color()
    val now = System.currentTimeMillis()
    val today = SimpleDateFormat("yyyy-MM-dd", TR).format(Date(e.time)) == SimpleDateFormat("yyyy-MM-dd", TR).format(Date(now))
    val date = if (e.slot.isNotBlank()) e.slot else SimpleDateFormat("d MMM, HH:mm", TR).format(Date(e.time))
    val remaining = ((e.time - now + 86_400_000L - 1L) / 86_400_000L).coerceAtLeast(0L)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        ExamTypeBadge(type, 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(e.title.ifBlank { e.course }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$date · ${type.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (today) {
            Text("Bugün", style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
        } else {
            Text("$remaining gün", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val shown = remember(tasks) { tasks.filter { it.first !in done }.take(3).ifEmpty { tasks.takeLast(2) } }
    Column(Modifier.fillMaxWidth().card().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Hafta $week", style = MaterialTheme.typography.titleMedium)
                Text("$doneCount / ${tasks.size} tamam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAll) { Text("Tümü") }
        }
        ProgressBar(doneCount / tasks.size.toFloat(), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), height = 4.dp)
        shown.forEach { (key, title, _) ->
            val checked = key in done
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onToggle(key) }.padding(horizontal = 4.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(key) })
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BackupNudge(onPick: () -> Unit, onLater: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        IconBadge(SouIcons.Backup, MaterialTheme.colorScheme.tertiary)
        Text(
            "Otomatik yedeği aç",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        TextButton(onClick = onLater) { Text("Sonra") }
        FilledTonalButton(onClick = onPick) { Text("Aç") }
    }
}

@Composable
private fun StartCard(onNotebook: () -> Unit, onImport: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().card().padding(24.dp),
    ) {
        Icon(SouIcons.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("İlk defterini oluştur", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Boş defter aç ya da PDF ekle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = onNotebook) { Text("Yeni defter") }
            OutlinedButton(onClick = onImport) { Text("PDF ekle") }
        }
    }
}

@Composable
private fun WeekCard(days: List<Pair<Long, Long>>, vm: LibraryViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val todayMinutes = (days.firstOrNull()?.second ?: 0L) / 60_000L
    val warning = LocalSouColors.current.warning
    Row(
        modifier.card().clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(warning.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(SouIcons.LocalFireDepartment, contentDescription = null, tint = warning, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${vm.streak} gün", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Çalışma serisi · bugün $todayMinutes dk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.height(32.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.reversed().forEachIndexed { i, (_, ms) ->
                val fraction = (ms.toFloat() / max).coerceIn(0f, 1f)
                val alpha = if (i == days.lastIndex) 1f else 0.35f + 0.65f * fraction
                Box(Modifier.width(8.dp).height((32f * fraction).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun TipCard(modifier: Modifier) {
    var index by rememberSaveable { mutableStateOf(dayOfYear() % TIPS.size) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.card().clickable { index = (index + 1) % TIPS.size }.padding(14.dp),
    ) {
        IconBadge(SouIcons.Lightbulb, LocalSouColors.current.warning)
        Text(TIPS[index], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
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
                itemsIndexed(layout.order, key = { _, m -> m.name }) { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().animateItem()) {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.updateHomeLayout(layout.move(m, -1)) }, enabled = i > 0) {
                            Icon(SouIcons.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                        }
                        IconButton(onClick = { vm.updateHomeLayout(layout.move(m, 1)) }, enabled = i < layout.order.size - 1) {
                            Icon(SouIcons.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                        }
                        Switch(checked = m !in layout.hidden, onCheckedChange = { vm.updateHomeLayout(layout.setVisible(m, it)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = { vm.resetHomeLayout() }) { Text("Varsayılan") } },
    )
}

/** Favoriler: sırala, sabitle, çıkar. */
@Composable
internal fun QuickAccessDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val entries = vm.quickItems.mapNotNull { q -> resolveQuick(vm).find { it.item.key == q.key } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Favoriler") },
        text = {
            if (entries.isEmpty()) {
                Text("Henüz favori yok.")
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(entries, key = { _, e -> e.item.key }) { i, e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().animateItem()) {
                            IconButton(onClick = { vm.togglePinned(e.item) }) {
                                Icon(
                                    if (e.item.pinned) SouIcons.KeepFilled else SouIcons.Keep,
                                    contentDescription = if (e.item.pinned) "Sabitlemeyi kaldır" else "Sabitle",
                                    tint = if (e.item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.moveQuick(e.item, -1) }, enabled = i > 0) {
                                Icon(SouIcons.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                            }
                            IconButton(onClick = { vm.moveQuick(e.item, 1) }, enabled = i < entries.size - 1) {
                                Icon(SouIcons.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                            }
                            IconButton(onClick = { vm.removeQuick(e.item) }) {
                                Icon(SouIcons.Close, contentDescription = "Çıkar", tint = MaterialTheme.colorScheme.error)
                            }
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
    val success = LocalSouColors.current.success
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card().clickable(onClick = onStart).padding(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(success.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(SouIcons.Style, contentDescription = null, tint = success, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("$count kart tekrar bekliyor", style = MaterialTheme.typography.titleSmall)
            Text("Yaklaşık ${(count * 20 + 59) / 60} dk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onStart) { Text("Başla") }
    }
}
