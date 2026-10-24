@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val empty = vm.loaded && vm.imported.isEmpty() && vm.rootFolders.isEmpty()
    val quick = resolveQuick(vm)

    @Composable
    fun module(m: HomeModule, index: Int, modifier: Modifier) {
        when (m) {
            HomeModule.HERO -> Appear(index, modifier) {
                Greeting(greeting(vm.userName), days.first().second, vm.streak, vm.activeToday)
            }
            HomeModule.QUICK_ACCESS -> if (quick.isNotEmpty()) {
                Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                    SectionHeader("Favoriler", action = "Düzenle", onAction = { quickEdit = true })
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
                val next = vm.allExams.filter { it.time > now && !vm.isDone(it) }.take(3)
                if (next.isNotEmpty()) {
                    Column(modifier.appear(index), verticalArrangement = Arrangement.spacedBy(space.md)) {
                        SectionHeader("Yaklaşanlar", action = "Planlayıcı") { vm.tab = LibraryViewModel.Tab.EXAMS }
                        Column(Modifier.fillMaxWidth().card()) {
                            next.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 72.dp))
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
                QuickActions(
                    listOf(
                        QuickAction(SouIcons.Add, "Yeni defter") { newNotebook = true },
                        QuickAction(SouIcons.EditNote, "Hızlı not") { vm.quickNote() },
                        QuickAction(SouIcons.PictureAsPdf, "PDF ekle") { importPdf() },
                        QuickAction(SouIcons.StarShine, "Asistan") { vm.openAssistant() },
                        // Kalemi olmayanlar için: tek dokunuşla parmakla yazma
                        QuickAction(SouIcons.TouchApp, "Parmakla yaz", toggled = vm.tools.fingerDraw) {
                            vm.updateTools(vm.tools.copy(fingerDraw = !vm.tools.fingerDraw))
                            vm.showMessage(if (vm.tools.fingerDraw) "Parmakla yazma açık" else "Parmakla yazma kapalı")
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
                            Box(Modifier.width(120.dp).animateItem().pressScale()) {
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
                verticalArrangement = Arrangement.spacedBy(if (narrow) 24.dp else 28.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            ) {
                // Üst satır: logo, arama ve ayarlar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(32.dp, elevated = false)
                    Text("SouNote", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    IconButton(onClick = { vm.searchOpen = true }, modifier = Modifier.semantics { contentDescription = "Ara" }) {
                        Icon(SouIcons.Search, contentDescription = null)
                    }
                    IconButton(onClick = { vm.tab = LibraryViewModel.Tab.SETTINGS }) {
                        Icon(SouIcons.Settings, contentDescription = "Ayarlar")
                    }
                }
                val modules = vm.homeLayout.visible
                // Geniş ekranda küçük kartlar yan yana: art arda gelen iki "yarım" modül aynı satıra.
                val half = setOf(HomeModule.STATS, HomeModule.TIP, HomeModule.WEEK_TASKS, HomeModule.UPCOMING, HomeModule.CONTINUE, HomeModule.REVIEW)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
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
            onCreate = { name, template, size, landscape, paper ->
                newNotebook = false
                vm.createNotebook(name, template, size, landscape, paper)
            },
            onDismiss = { newNotebook = false },
        )
    }
}

/** Karşılama: tarih, selam ve tek satır özet (seri, bugünkü süre). */
@Composable
private fun Greeting(greeting: String, todayMs: Long, streak: Int, activeToday: Boolean) {
    val date = remember { SimpleDateFormat("d MMMM EEEE", TR).format(Date()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            val sou = LocalSouColors.current
            InfoPill(
                SouIcons.LocalFireDepartment,
                if (streak > 0) "$streak gün seri" else "Seri yok",
                if (streak > 0 && activeToday) sou.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoPill(SouIcons.Timer, "Bugün ${formatStudy(todayMs)}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().card().combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }).padding(14.dp),
    ) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
        Box(
            Modifier
                .width(60.dp)
                .aspectRatio(0.75f)
                .clip(thumbShape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
        ) { Thumbnail(doc) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Sayfa ${page + 1} · $ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Yaklaşan sınav/ödev satırı: tarih kutusu, başlık, kalan süre. */
@Composable
private fun ExamRow(e: com.sounote.app.data.Exam, vm: LibraryViewModel, onClick: () -> Unit) {
    val day = remember(e.time) { SimpleDateFormat("d", TR).format(Date(e.time)) }
    val month = remember(e.time) { SimpleDateFormat("MMM", TR).format(Date(e.time)) }
    val c = countdownColor(e.time)
    val color = Color(vm.courseColor(e.course))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(vertical = 6.dp),
        ) {
            Text(day, style = MaterialTheme.typography.titleMedium, color = color)
            Text(month, style = MaterialTheme.typography.labelSmall, color = color)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(e.title.ifBlank { e.course }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(e.kindIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    e.course,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Text(countdownText(e.time), color = c, style = MaterialTheme.typography.labelLarge)
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
        Image(painterResource(com.sounote.app.R.drawable.pen_fountain), contentDescription = null, modifier = Modifier.height(96.dp))
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

private class QuickAction(
    val icon: ImageVector,
    val title: String,
    /** Aç/kapa için: null değilse açıkken dolu renkli görünür. */
    val toggled: Boolean? = null,
    val onClick: () -> Unit,
)

/** Hızlı başla: yuvarlak simge + tek kelime, tek satır. */
@Composable
private fun QuickActions(actions: List<QuickAction>) {
    Row(Modifier.fillMaxWidth()) {
        actions.forEach { a ->
            val on = a.toggled == true
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .pressScale()
                    .clickable(onClick = a.onClick)
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        a.icon,
                        contentDescription = null,
                        tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    a.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
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
    Column(modifier = modifier.card().clickable(onClick = onOpen).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bu hafta", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatStudy(days.sumOf { it.second }), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            days.reversed().forEachIndexed { i, (t, ms) ->
                val today = i == days.size - 1
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .width(14.dp)
                            .height((64f * ms / max * grow.value).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                when {
                                    today -> MaterialTheme.colorScheme.primary
                                    vm.isActiveDay(t) -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                },
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card().clickable(onClick = onStart).padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        IconBadge(SouIcons.Style)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Bugünkü tekrar", style = MaterialTheme.typography.titleMedium)
            Text("$count kart", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onStart) { Text("Başla") }
    }
}
