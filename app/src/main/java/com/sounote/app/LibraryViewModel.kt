package com.sounote.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sounote.app.data.AudioNotes
import com.sounote.app.data.AudioRecorder
import com.sounote.app.data.Backup
import com.sounote.app.data.Card
import com.sounote.app.data.Flashcards
import com.sounote.app.data.Recording
import com.sounote.app.data.DocRef
import com.sounote.app.data.FolderListing
import com.sounote.app.data.FolderRef
import com.sounote.app.data.LibraryRepository
import com.sounote.app.data.NaturalOrder
import com.sounote.app.data.UserFolder
import com.sounote.app.data.CatalogRepo
import com.sounote.app.data.Course
import com.sounote.app.data.Exam
import com.sounote.app.data.ExamStore
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic
import com.sounote.app.ui.Accent
import com.sounote.app.ui.ColorTheme
import com.sounote.app.ui.DarkPalette
import com.sounote.app.ui.MotionLevel
import com.sounote.app.ui.UiDensity
import com.sounote.app.data.HomeLayout
import com.sounote.app.data.QuickAccess
import com.sounote.app.data.QuickItem
import com.sounote.app.data.TextNote
import com.sounote.app.data.TextNotePdf
import com.sounote.app.data.TextNoteStore
import kotlinx.coroutines.channels.Channel
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sounote.app.ink.ContentPainter
import com.sounote.app.ink.Images
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.InkStore
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.ToolState
import com.sounote.app.pdf.PageTemplate
import com.sounote.app.pdf.PaperColor
import com.sounote.app.pdf.PaperSize
import com.sounote.app.pdf.PdfExporter
import com.sounote.app.pdf.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val store = InkStore(app)
    private val prefs = app.getSharedPreferences("viewer", Context.MODE_PRIVATE)
    private val toolPrefs = app.getSharedPreferences("tools", Context.MODE_PRIVATE)

    var imported by mutableStateOf<List<DocRef>>(emptyList())
        private set
    var rootFolders by mutableStateOf<List<FolderRef>>(emptyList())
        private set

    /** Bağlı klasör içinde gezinme yolu; boşsa kütüphane ana sayfası. */
    var path by mutableStateOf<List<FolderRef>>(emptyList())
        private set
    var listing by mutableStateOf(FolderListing(emptyList(), emptyList()))
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var openDoc by mutableStateOf<DocRef?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var tools by mutableStateOf(ToolState.load(toolPrefs))
        private set

    fun updateTools(state: ToolState) {
        tools = state
        state.save(toolPrefs)
    }

    // ---- Kütüphane düzeni: kendi klasörlerim, arama, sıralama, tema ----

    enum class SortMode(val label: String) { RECENT("Son açılan"), ADDED("Son eklenen"), NAME("Ada göre") }
    enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Açık"), DARK("Koyu") }

    var userFolders by mutableStateOf<List<UserFolder>>(emptyList())
        private set

    /** Açık olan kendi klasörüm; null = ana sayfa. */
    var currentFolder by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
    var sortMode by mutableStateOf(enumValueOrNull<SortMode>(prefs.getString("sort", null)) ?: SortMode.RECENT)
        private set
    var themeMode by mutableStateOf(enumValueOrNull<ThemeMode>(prefs.getString("theme", null)) ?: ThemeMode.SYSTEM)
        private set

    fun updateSort(mode: SortMode) {
        sortMode = mode
        prefs.edit().putString("sort", mode.name).apply()
    }

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    // ---- 2.0 görünüm ve arayüz tercihleri (hepsi cihazda, güvenli varsayılanlarla) ----

    /** Koyu temanın yüzeyleri. */
    var darkPalette by mutableStateOf(enumValueOrNull<DarkPalette>(prefs.getString("darkPalette", null)) ?: DarkPalette.OBSIDIAN)
        private set

    fun updateDarkPalette(p: DarkPalette) {
        darkPalette = p
        prefs.edit().putString("darkPalette", p.name).apply()
    }

    /** Vurgu rengi. 1.0'da seçilen renk ("colorTheme") aynen korunur; seçilmemişse temanın kendi rengi. */
    var accent by mutableStateOf(loadAccent())
        private set

    private fun loadAccent(): Accent = when (val n = prefs.getString("colorTheme", null)) {
        null, "AUTO" -> Accent.Auto
        "CUSTOM" -> Accent.Custom(prefs.getInt("accentCustom", 0xFF4F46E5.toInt()))
        else -> enumValueOrNull<ColorTheme>(n)?.let { Accent.Preset(it) } ?: Accent.Auto
    }

    fun updateAccent(a: Accent) {
        accent = a
        prefs.edit().apply {
            when (a) {
                Accent.Auto -> putString("colorTheme", "AUTO")
                is Accent.Preset -> putString("colorTheme", a.theme.name)
                is Accent.Custom -> putString("colorTheme", "CUSTOM").putInt("accentCustom", a.argb)
            }
        }.apply()
    }

    var uiDensity by mutableStateOf(enumValueOrNull<UiDensity>(prefs.getString("uiDensity", null)) ?: UiDensity.COMFORTABLE)
        private set

    fun updateDensity(d: UiDensity) {
        uiDensity = d
        prefs.edit().putString("uiDensity", d.name).apply()
    }

    var motionLevel by mutableStateOf(enumValueOrNull<MotionLevel>(prefs.getString("motionLevel", null)) ?: MotionLevel.FULL)
        private set

    fun updateMotion(m: MotionLevel) {
        motionLevel = m
        prefs.edit().putString("motionLevel", m.name).apply()
    }

    /** Geniş ekranda gezinme menüsü: ekrana göre, hep dar ray ya da hep geniş menü. */
    enum class NavStyle(val label: String) { AUTO("Ekrana göre"), RAIL("Dar ray"), EXPANDED("Geniş menü") }

    var navStyle by mutableStateOf(enumValueOrNull<NavStyle>(prefs.getString("navStyle", null)) ?: NavStyle.AUTO)
        private set

    fun updateNavStyle(n: NavStyle) {
        navStyle = n
        prefs.edit().putString("navStyle", n.name).apply()
    }

    // ---- Ana sayfa düzeni ----

    var homeLayout by mutableStateOf(HomeLayout.parse(prefs.getString("homeOrder", null), prefs.getString("homeHidden", null)))
        private set

    fun updateHomeLayout(l: HomeLayout) {
        homeLayout = l
        prefs.edit()
            .putString("homeOrder", l.order.joinToString(",") { it.name })
            .putString("homeHidden", l.hidden.joinToString(",") { it.name })
            .apply()
    }

    fun resetHomeLayout() = updateHomeLayout(HomeLayout.DEFAULT)

    // ---- Hızlı erişim: favoriler ve sabitlenenler ----

    var quickItems by mutableStateOf(QuickAccess.parse(prefs.getString("quickAccess", null)))
        private set

    private fun saveQuick(items: List<QuickItem>) {
        quickItems = items
        prefs.edit().putString("quickAccess", QuickAccess.toJson(items)).apply()
    }

    fun isFavorite(type: String, id: String) = quickItems.any { it.type == type && it.id == id }

    fun toggleFavorite(type: String, id: String) {
        if (isFavorite(type, id)) {
            saveQuick(quickItems.filterNot { it.type == type && it.id == id })
            message = "Favorilerden çıkarıldı"
        } else {
            saveQuick(quickItems + QuickItem(type, id))
            message = "Favorilere eklendi ⭐"
        }
    }

    fun togglePinned(item: QuickItem) = saveQuick(quickItems.map { if (it.key == item.key) it.copy(pinned = !it.pinned) else it })

    fun moveQuick(item: QuickItem, delta: Int) {
        val i = quickItems.indexOfFirst { it.key == item.key }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, quickItems.size - 1)
        saveQuick(quickItems.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun removeQuick(item: QuickItem) = saveQuick(quickItems.filterNot { it.key == item.key })

    /** Ders açıldığında (son kullanılanlar için). */
    fun courseOpened(id: String) {
        prefs.edit().putLong("courseOpened_$id", System.currentTimeMillis()).apply()
    }

    fun lastCourseOpen(id: String) = prefs.getLong("courseOpened_$id", 0L)

    /** Arama sonucundan ya da hızlı erişimden açılacak ders. */
    var focusCourse by mutableStateOf<String?>(null)

    // ---- Kütüphane ve planlayıcı görünümü ----

    enum class LibraryView { GRID, LIST }

    var libraryView by mutableStateOf(enumValueOrNull<LibraryView>(prefs.getString("libraryView", null)) ?: LibraryView.GRID)
        private set

    fun updateLibraryView(v: LibraryView) {
        libraryView = v
        prefs.edit().putString("libraryView", v.name).apply()
    }

    /** Kütüphanede sadece favoriler. */
    var favoritesOnly by mutableStateOf(false)

    enum class PlannerView(val label: String) { LIST("Liste"), WEEK("Hafta"), MONTH("Ay"), TIMELINE("Zaman çizelgesi") }

    var plannerView by mutableStateOf(enumValueOrNull<PlannerView>(prefs.getString("plannerView", null)) ?: PlannerView.LIST)
        private set

    fun updatePlannerView(v: PlannerView) {
        plannerView = v
        prefs.edit().putString("plannerView", v.name).apply()
    }

    // ---- Belge: okuma / not alma modu ----

    /** Okuma modunda araç çubuğu gizlenir ve kalem çizmez (kaydırır). */
    var readingMode by mutableStateOf(prefs.getBoolean("readingMode", false))
        private set

    fun updateReadingMode(on: Boolean) {
        readingMode = on
        prefs.edit().putBoolean("readingMode", on).apply()
    }

    // ---- Son aramalar ----

    var recentSearches by mutableStateOf(prefs.getString("recentSearches", null).orEmpty().split('\n').filter { it.isNotBlank() })
        private set

    fun rememberSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = (listOf(t) + recentSearches.filter { !it.equals(t, ignoreCase = true) }).take(8)
        prefs.edit().putString("recentSearches", recentSearches.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
        prefs.edit().remove("recentSearches").apply()
    }

    /** Yedeği oluşturup Drive'a (ya da seçilen uygulamaya) gönderir. */
    fun backupToShare(onReady: (File) -> Unit) {
        viewModelScope.launch {
            busy = true
            saveJob?.cancel()
            flushInk()
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles { f -> f.name.startsWith("SouNote-yedek") }?.forEach { it.delete() }
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
                    File(dir, "SouNote-yedek-$date.zip").also { f -> f.outputStream().use { Backup.write(getApplication(), it) } }
                }.onFailure { ErrorLog.record("Yedeklenemedi", it) }.getOrNull()
            }
            busy = false
            if (file != null) onReady(file) else message = "Yedeklenemedi"
        }
    }

    // ---- Ana ekran: sekmeler, tanıtım, kullanıcı adı ----

    enum class Tab { HOME, LIBRARY, COURSES, EXAMS, WRITING, SETTINGS }

    /** Belge kapanınca aynı sekmeye dönülsün diye burada tutulur. */
    var tab by mutableStateOf(Tab.HOME)

    /** Her yerden açılabilen evrensel arama. */
    var searchOpen by mutableStateOf(false)

    /** Genel asistan penceresi; değer, giriş kutusuna önceden yazılacak metin (null = kapalı). */
    var assistantPrefill by mutableStateOf<String?>(null)

    fun openAssistant(prefill: String = "") {
        assistantPrefill = prefill
    }

    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set

    fun finishOnboarding() {
        onboarded = true
        // Yeni kurulumda "Yenilikler" penceresi çıkmasın.
        prefs.edit().putBoolean("onboarded", true).putInt("seenBuild", installedBuild).putInt("seenMajor", MAJOR).apply()
    }

    /** Ayarlar → Tanıtımı tekrar göster. */
    fun replayOnboarding() {
        onboarded = false
    }

    var userName by mutableStateOf(prefs.getString("userName", null) ?: "")
        private set

    fun updateUserName(name: String) {
        userName = name.trim().take(30)
        prefs.edit().putString("userName", userName).apply()
    }

    // ---- Yazılı notlar (klavyeyle) ----

    private val textStore = TextNoteStore(app)
    var textNotes by mutableStateOf<List<TextNote>>(emptyList())
        private set
    var openText by mutableStateOf<TextNote?>(null)
        private set
    var textFontSize by mutableStateOf(prefs.getFloat("textNoteSize", 18f))
        private set

    /** Dosya yazmaları sırayla yapılsın (hızlı yazarken eski içerik yenisinin üstüne yazılmasın). */
    private val textWrites = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (write in textWrites) runCatching { write() }.onFailure { ErrorLog.record("Yazılı not kaydedilemedi", it) }
        }
    }

    private suspend fun loadTextNotes() {
        textNotes = withContext(Dispatchers.IO) { runCatching { textStore.list() }.getOrDefault(emptyList()) }
    }

    /** Belgeye bağlı yan not (PDF ile yan yana yazılan yazılı not). Yazılınca normal yazılı not olarak saklanır. */
    fun sideNoteFor(doc: DocRef): TextNote {
        val id = prefs.getString("sideNote_${doc.id}", null)
        textNotes.find { it.id == id }?.let { return it }
        val now = System.currentTimeMillis()
        val note = TextNote(id ?: java.util.UUID.randomUUID().toString(), "${doc.title} – notlar", "", now, now)
        prefs.edit().putString("sideNote_${doc.id}", note.id).apply()
        return note
    }

    /** Yeni yazılı not; [body] şablon olabilir (ör. essay bölümleri). */
    fun newTextNote(body: String = "") {
        val now = System.currentTimeMillis()
        openText = TextNote(java.util.UUID.randomUUID().toString(), "", body, now, now)
    }

    fun openTextNote(note: TextNote) {
        openText = note
    }

    fun closeTextNote() {
        openText = null
    }

    /** Silinen notlar: ekran kapanırken çalışan son kayıt onları geri getirmesin. */
    private val deletedTextIds = HashSet<String>()

    /** Değiştiyse kaydeder; başlık ve metin boşsa not silinir. */
    fun saveTextNote(id: String, title: String, body: String) {
        if (id in deletedTextIds) return
        val old = textNotes.find { it.id == id } ?: openText?.takeIf { it.id == id }
        val stored = textNotes.any { it.id == id }
        // Boş ya da hiç doldurulmamış essay şablonu: not olarak saklanmaz.
        if (title.isBlank() && (body.isBlank() || body.trim() == com.sounote.app.data.Essay.TEMPLATE.trim())) {
            if (stored) {
                textNotes = textNotes.filter { it.id != id }
                textWrites.trySend { textStore.delete(id) }
            }
            return
        }
        if (stored && old != null && old.title == title && old.body == body) return
        val now = System.currentTimeMillis()
        val note = TextNote(id, title, body, old?.created ?: now, now, old?.pinned ?: false)
        textNotes = (textNotes.filter { it.id != id } + note).sortedWith(TextNoteStore.ORDER)
        if (openText?.id == id) openText = note
        textWrites.trySend { textStore.save(note) }
    }

    fun togglePinText(note: TextNote) {
        if (note.id in deletedTextIds) return
        val n = note.copy(pinned = !note.pinned)
        textNotes = (textNotes.filter { it.id != n.id } + n).sortedWith(TextNoteStore.ORDER)
        if (openText?.id == n.id) openText = n
        textWrites.trySend { textStore.save(n) }
    }

    fun deleteTextNote(note: TextNote) {
        deletedTextIds += note.id
        textNotes = textNotes.filter { it.id != note.id }
        if (openText?.id == note.id) openText = null
        textWrites.trySend { textStore.delete(note.id) }
        message = "\"${note.displayTitle}\" silindi"
    }

    fun updateTextFontSize(size: Float) {
        textFontSize = size.coerceIn(12f, 32f)
        prefs.edit().putFloat("textNoteSize", textFontSize).apply()
    }

    /** Notu PDF'e çevirir (paylaşmak için). */
    fun textNotePdf(note: TextNote, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val name = note.displayTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
                    File(getApplication<Application>().cacheDir, "exports/$name.pdf").also { TextNotePdf.write(getApplication(), note, it) }
                }.onFailure { ErrorLog.record("Not PDF'e çevrilemedi", it) }.getOrNull()
            }
            if (file != null) onReady(file) else message = "PDF oluşturulamadı"
        }
    }

    /** Notu PDF defter olarak kütüphaneye ekler ve açar: üstüne kalemle çalışılabilir. */
    fun textNoteToDocument(note: TextNote) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { repo.createDocument(note.displayTitle, null) { TextNotePdf.write(getApplication(), note, it) } }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            result.onSuccess {
                openText = null
                open(it)
            }.onFailure {
                ErrorLog.record("Not deftere çevrilemedi", it)
                message = "Deftere çevrilemedi"
            }
        }
    }

    // ---- Ortak ders kataloğu ve sınavlar ----

    var catalog by mutableStateOf(CatalogRepo.cached(app))
        private set
    var catalogLoading by mutableStateOf(false)
        private set
    var catalogError by mutableStateOf<String?>(null)
        private set
    private val examStore = ExamStore(app)
    var myExams by mutableStateOf(examStore.load())
        private set
    var remindersOn by mutableStateOf(ExamReminders.enabled(app))
        private set
    // ---- Not ortalaması ----

    private val gradeStore = com.sounote.app.data.GradeStore(app)
    var grades by mutableStateOf(gradeStore.load())
        private set

    fun updateGrades(g: com.sounote.app.data.Grades) {
        grades = g
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gradeStore.save(g) }.onFailure { ErrorLog.record("Notlar kaydedilemedi", it) }
        }
    }

    /** Dersler ekranı haftalara göre mi (ana sayfadaki haftalık listeden de açılır). */
    var coursesByWeek by mutableStateOf(false)

    // ---- Haftalık liste: tamamlanan kaynaklar ----

    var doneItems by mutableStateOf(prefs.getStringSet("weekDone", emptySet())!!.toSet())
        private set

    fun toggleDone(key: String) {
        doneItems = if (key in doneItems) doneItems - key else doneItems + key
        prefs.edit().putStringSet("weekDone", doneItems).apply()
    }

    /** Haftanın yapılacakları: (anahtar, başlık, tür) — kaynaklar ve Drive'da olmayanlar, gün sırasıyla. */
    fun weekTasks(week: Int): List<Triple<String, String, String>> {
        val c = catalog
        val res = c.courses.flatMap { co -> co.topics.flatMap { it.resources } }.filter { it.week == week }
            .map { Triple(it.day, it.order, Triple(doneKey(it), it.title, it.kind)) }
        val missing = c.weekItems.filter { it.week == week }
            .map { Triple(it.day, it.order, Triple(doneKey(it), it.title, it.kind)) }
        // Pazartesi..Cuma (1–5), self-study (0) en sonda
        return (res + missing).sortedWith(compareBy({ if (it.first == 0) 9 else it.first }, { it.second })).map { it.third }.distinctBy { it.first }
    }

    /** Konunun/dersin tamamlanan kaynak sayısı ve toplamı (haftalık listedeki işaretlerden). */
    fun progressOf(resources: List<com.sounote.app.data.Resource>): Pair<Int, Int> =
        resources.count { doneKey(it) in doneItems } to resources.size

    /** Dersin klasörüne indirilmiş belgeler (son açılan başta). */
    fun courseDocs(course: Course): List<DocRef> {
        val folder = userFolders.find { it.parentId == null && it.name.equals(course.name, ignoreCase = true) } ?: return emptyList()
        return imported.filter { it.folderId == folder.id }.sortedByDescending { maxOf(lastOpened(it), it.addedAt) }
    }

    fun doneKey(r: com.sounote.app.data.Resource) = "r:" + r.url.ifBlank { r.week.toString() + ":" + r.title }
    fun doneKey(w: com.sounote.app.data.WeekItem) = "w:${w.week}:${w.title}"

    /** Sınavlar ekranından bir konuya gidilince Dersler ekranında açılacak konu. */
    var focusTopic by mutableStateOf<String?>(null)

    /** İndirilmekte olan kaynağın adresi. */
    var downloading by mutableStateOf<String?>(null)
        private set

    /** Kullanıcının kuru (L1–L4); boşsa bütün kurların sınavları görünür. */
    var userLevel by mutableStateOf(prefs.getString("userLevel", null) ?: "")
        private set

    fun updateUserLevel(level: String) {
        userLevel = level
        prefs.edit().putString("userLevel", level).apply()
        viewModelScope.launch(Dispatchers.IO) {
            ExamReminders.schedule(getApplication(), allExams.filter { !isDone(it) })
            ExamWidget.updateAll(getApplication())
        }
    }

    private fun forMyLevel(e: Exam) = userLevel.isEmpty() || e.levels.isEmpty() || userLevel in e.levels

    /** Kendi eklediklerim + ortak katalogdakiler (kurumun), tarihe göre. */
    val allExams: List<Exam> get() = (myExams + catalog.exams.filter(::forMyLevel)).sortedBy { it.time }

    /** Ortak katalogdaki ödevlerin "tamamlandı" işaretleri (katalog dosyası değiştirilmez). */
    var examDoneGlobal by mutableStateOf(prefs.getStringSet("examDone", emptySet())!!.toSet())
        private set

    fun isDone(e: Exam) = e.done || e.id in examDoneGlobal

    fun toggleExamDone(e: Exam) {
        if (e.global) {
            examDoneGlobal = if (e.id in examDoneGlobal) examDoneGlobal - e.id else examDoneGlobal + e.id
            prefs.edit().putStringSet("examDone", examDoneGlobal).apply()
            viewModelScope.launch(Dispatchers.IO) {
                ExamReminders.schedule(getApplication(), allExams.filter { !isDone(it) })
                ExamWidget.updateAll(getApplication())
            }
        } else {
            saveExam(e.copy(done = !e.done))
        }
    }

    /** Ders adına göre renk: katalogdaki dersin rengi, yoksa addan türetilmiş sabit bir renk. */
    fun courseColor(name: String): Int {
        catalog.courses.find { it.name.equals(name, ignoreCase = true) }?.let { return it.color }
        if (name.isBlank()) return 0xFF7A7F89.toInt()
        return COURSE_COLORS[Math.floorMod(name.lowercase().hashCode(), COURSE_COLORS.size)]
    }

    init {
        refreshCatalog(quiet = true)
    }

    // ---- Güncelleme ve "Yenilikler" ----

    val installedBuild = com.sounote.app.data.Updates.installedBuild(app)
    val installedVersion = com.sounote.app.data.Updates.installedName(app)

    /** GitHub'da bu kurulumdan yeni bir sürüm varsa (ve kullanıcı "Sonra" demediyse). */
    var update by mutableStateOf<com.sounote.app.data.AppRelease?>(null)
        private set

    fun checkUpdate(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("updateChecked", 0L) < 6 * 3_600_000L) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { com.sounote.app.data.Updates.latest() } }
            r.onSuccess { rel ->
                prefs.edit().putLong("updateChecked", now).apply()
                val newer = rel.build > installedBuild
                update = if (newer && (manual || prefs.getInt("skipBuild", 0) != rel.build)) rel else null
                if (manual && !newer) message = "Uygulama güncel ($installedVersion)"
            }.onFailure { if (manual) message = "Güncelleme denetlenemedi: internet bağlantısını kontrol et" }
        }
    }

    /** "Sonra": bu sürüm için bir daha kendiliğinden sorma. */
    fun skipUpdate() {
        update?.let { prefs.edit().putInt("skipBuild", it.build).apply() }
        update = null
    }

    /** Uygulama güncellendikten sonra ilk açılışta "Yenilikler" penceresi. İlk kurulumda gösterilmez. */
    var showWhatsNew by mutableStateOf(
        prefs.getBoolean("onboarded", false) && prefs.getInt("seenBuild", 0) < installedBuild &&
            prefs.getInt("seenMajor", 0) < MAJOR,
    )
        private set

    fun dismissWhatsNew() {
        showWhatsNew = false
        prefs.edit().putInt("seenBuild", installedBuild).putInt("seenMajor", MAJOR).apply()
    }

    init {
        checkUpdate(manual = false)
    }

    fun refreshCatalog(quiet: Boolean = false) {
        if (catalogLoading) return
        viewModelScope.launch {
            catalogLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { CatalogRepo.fetch(getApplication()) } }
            catalogLoading = false
            result.onSuccess {
                catalog = it
                catalogError = null
                withContext(Dispatchers.IO) { ExamReminders.schedule(getApplication(), allExams.filter { !isDone(it) }) }
                ExamWidget.updateAll(getApplication())
            }.onFailure {
                catalogError = "İnternete bağlanılamadı; son indirilen liste gösteriliyor"
                if (!quiet) message = catalogError
            }
        }
    }

    fun saveExam(exam: Exam) {
        myExams = (myExams.filter { it.id != exam.id } + exam).sortedBy { it.time }
        persistExams()
    }

    fun deleteExam(exam: Exam) {
        myExams = myExams.filter { it.id != exam.id }
        persistExams()
    }

    private fun persistExams() {
        val list = myExams
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { examStore.save(list) }.onFailure { ErrorLog.record("Sınavlar kaydedilemedi", it) }
            ExamReminders.schedule(getApplication(), allExams.filter { !isDone(it) })
            ExamWidget.updateAll(getApplication())
        }
    }

    fun updateReminders(on: Boolean) {
        remindersOn = on
        viewModelScope.launch(Dispatchers.IO) { ExamReminders.setEnabled(getApplication(), on) }
    }

    /** Katalogdaki PDF'i indirip dersin klasörüne koyar ve açar. */
    fun downloadResource(course: Course, topic: Topic, res: Resource) {
        if (downloading != null) return
        viewModelScope.launch {
            downloading = res.url
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = repo.userFolders().find { it.parentId == null && it.name.equals(course.name, ignoreCase = true) }
                        ?: repo.createFolder(course.name, null, course.color, course.emoji)
                    val doc = repo.createDocument(res.title.ifBlank { topic.name }, folder.id) { CatalogRepo.download(res, it) }
                    repo.setTags(doc.id, listOf(course.name, topic.name).map { it.lowercase(java.util.Locale.forLanguageTag("tr")) })
                    doc
                }
            }
            downloading = null
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            userFolders = withContext(Dispatchers.IO) { repo.userFolders() }
            result.onSuccess { open(it) }.onFailure {
                ErrorLog.record("Kaynak indirilemedi", it)
                message = "İndirilemedi: ${it.message ?: "bağlantı hatası"}"
            }
        }
    }

    /** Bu kaynak daha önce indirildi mi (aynı adla dersin klasöründe). */
    fun downloadedDoc(course: Course, res: Resource): DocRef? {
        val folder = userFolders.find { it.parentId == null && it.name.equals(course.name, ignoreCase = true) } ?: return null
        return imported.find { it.folderId == folder.id && it.title == res.title }
    }

    // ---- Yardımcı asistan ----

    var assistantConfig by mutableStateOf(com.sounote.app.data.AssistantConfig.load(app))
        private set

    fun updateAssistantConfig(c: com.sounote.app.data.AssistantConfig) {
        com.sounote.app.data.AssistantConfig.save(getApplication(), c)
        assistantConfig = com.sounote.app.data.AssistantConfig.load(getApplication())
    }

    /** Belgenin metni (asistana bağlam): [from]..[to] sayfaları. */
    suspend fun documentText(doc: DocRef, from: Int, to: Int): String = withContext(Dispatchers.IO) {
        com.sounote.app.data.PdfText.extract(getApplication(), repo.resolveForOpen(doc).uri, from, to)
    }

    /** Lasso ile seçilen bölgenin PDF metni (asistana "seçili metni açıkla" için). */
    suspend fun regionText(doc: DocRef, page: Int, rect: RectF): String = withContext(Dispatchers.IO) {
        runCatching { com.sounote.app.data.PdfText.region(getApplication(), repo.resolveForOpen(doc).uri, page, rect) }
            .onFailure { ErrorLog.record("Bölge metni okunamadı", it) }
            .getOrDefault("")
    }

    // ---- Genel arama ----

    /** Arama sonucu: belge adı, PDF sayfası, yazılı not ya da bilgi kartı. */
    sealed interface SearchHit {
        data class DocTitle(val doc: DocRef) : SearchHit
        data class PdfPage(val doc: DocRef, val page: Int, val snippet: String) : SearchHit
        data class Note(val note: com.sounote.app.data.TextNote, val snippet: String) : SearchHit
        data class FlashCard(val doc: DocRef, val card: Card) : SearchHit
        data class CourseHit(val course: Course) : SearchHit
        data class TopicHit(val course: Course, val topic: Topic) : SearchHit
        data class FolderHit(val folder: UserFolder) : SearchHit
        data class ExamHit(val exam: Exam) : SearchHit
    }

    private val searchIndex = com.sounote.app.data.SearchIndex(app)

    /** PDF metinleri çıkarılırken ilerleme (bitti = null). */
    var indexing by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private fun stampOf(doc: DocRef): Pair<Uri, Long> {
        val r = repo.resolveForOpen(doc)
        val f = r.uri.path?.let(::File)
        return r.uri to (if (r.uri.scheme == "file" && f != null) f.lastModified() else r.lastModified)
    }

    /** Henüz metni çıkarılmamış belgeleri tarar (arama ekranı açılınca). */
    fun ensureSearchIndex() {
        if (indexing != null) return
        val docs = imported
        viewModelScope.launch {
            val todo = withContext(Dispatchers.IO) {
                searchIndex.prune(docs.map { it.id }.toSet())
                docs.filter { d -> runCatching { !searchIndex.has(d.id, stampOf(d).second) }.getOrDefault(false) }
            }
            if (todo.isEmpty()) return@launch
            todo.forEachIndexed { i, d ->
                indexing = i to todo.size
                withContext(Dispatchers.IO) {
                    runCatching {
                        val (uri, stamp) = stampOf(d)
                        searchIndex.build(d.id, stamp, uri)
                    }.onFailure { ErrorLog.record("Arama dizini: ${d.title}", it) }
                }
            }
            indexing = null
            searchVersion++
        }
    }

    /** Dizin güncellenince arama ekranı sonuçları yenilesin. */
    var searchVersion by mutableStateOf(0)
        private set

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.Default) {
        val q = com.sounote.app.data.SearchIndex.norm(query.trim())
        if (q.length < 2) return@withContext emptyList()
        val norm = com.sounote.app.data.SearchIndex.Companion::norm
        val out = ArrayList<SearchHit>()
        val docs = imported
        // Dersler, konular, klasörler, sınav/ödevler: cihazdaki listelerde (internete gitmez).
        catalog.courses.forEach { c ->
            if (q in norm(c.name)) out += SearchHit.CourseHit(c)
            c.topics.filter { q in norm(it.name) }.forEach { out += SearchHit.TopicHit(c, it) }
        }
        userFolders.filter { q in norm(it.name) }.forEach { out += SearchHit.FolderHit(it) }
        allExams.filter { q in norm(it.title + " " + it.course + " " + it.notes) }.forEach { out += SearchHit.ExamHit(it) }
        docs.filter { q in norm(it.title) }.forEach { out += SearchHit.DocTitle(it) }
        textNotes.forEach { n ->
            val text = n.title + " " + n.plainBody
            if (q in norm(text)) out += SearchHit.Note(n, com.sounote.app.data.SearchIndex.snippet(n.plainBody, q) ?: n.plainBody.take(100))
        }
        withContext(Dispatchers.IO) {
            for (d in docs) {
                val stamp = runCatching { stampOf(d).second }.getOrNull() ?: continue
                searchIndex.pages(d.id, stamp).forEachIndexed { i, text ->
                    com.sounote.app.data.SearchIndex.snippet(text, q)?.let { out += SearchHit.PdfPage(d, i, it) }
                }
                Flashcards.load(getApplication(), d.id).forEach { c ->
                    if (q in norm(c.frontText) || q in norm(c.back)) out += SearchHit.FlashCard(d, c)
                }
                if (out.size > 300) break
            }
        }
        out
    }

    /** Arama sonucundan belgeyi o sayfada açar. */
    fun openAt(doc: DocRef, page: Int) {
        saveLastPage(doc, page)
        open(doc)
    }

    /** Görüntüleyicinin klavye kısayolları (açık belge yoksa null). */
    var keyHandler: ((android.view.KeyEvent) -> Boolean)? = null

    // ---- Uygulama kilidi: açılışta ve bir dakikadan uzun arka planda kalınca parmak izi / PIN ----

    var appLock by mutableStateOf(prefs.getBoolean("appLock", false))
        private set
    var locked by mutableStateOf(appLock)
        private set
    private var backgroundSince = 0L

    private fun deviceSecure() =
        getApplication<Application>().getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

    fun updateAppLock(on: Boolean) {
        if (on && !deviceSecure()) {
            message = "Önce cihazda ekran kilidi (PIN, desen ya da parmak izi) ayarla"
            return
        }
        appLock = on
        prefs.edit().putBoolean("appLock", on).apply()
        message = if (on) "Uygulama kilidi açık" else "Uygulama kilidi kapalı"
    }

    fun onAppBackground() {
        backgroundSince = System.currentTimeMillis()
    }

    fun onAppForeground() {
        if (appLock && backgroundSince != 0L && System.currentTimeMillis() - backgroundSince > LOCK_AFTER_MS) locked = true
        backgroundSince = 0L
    }

    fun unlocked() {
        locked = false
    }

    /** Ekran kilidi sonradan kaldırıldıysa uygulama içeride kalmasın diye kilit de kapanır. */
    fun lockUnavailable() {
        locked = false
        appLock = false
        prefs.edit().putBoolean("appLock", false).apply()
    }

    fun lastOpened(doc: DocRef) = prefs.getLong("opened_${doc.id}", 0L)

    /** Seçili etiket filtresi; null = hepsi. */
    var tagFilter by mutableStateOf<String?>(null)

    val allTags: List<String> get() = imported.flatMap { it.tags }.distinct().sortedWith(NaturalOrder)

    fun setTags(doc: DocRef, tags: List<String>) = folderOp { repo.setTags(doc.id, tags) }

    // ---- Çalışma süresi ----

    private var studyStart = 0L
    private var studyDoc: String? = null

    /** Belge açıkken ve uygulama öndeyken geçen süre sayılır. */
    fun studyResume() {
        val d = openDoc ?: return
        studyDoc = d.id
        studyStart = System.currentTimeMillis()
    }

    fun studyPause() {
        val id = studyDoc ?: return
        val start = studyStart
        studyDoc = null
        if (start == 0L) return
        val ms = (System.currentTimeMillis() - start).coerceIn(0L, 12L * 60 * 60 * 1000)
        if (ms < 1000) return
        val day = dayKey(System.currentTimeMillis())
        prefs.edit()
            .putLong("study_$id", studyTime(id) + ms)
            .putLong("studyday_$day", prefs.getLong("studyday_$day", 0L) + ms)
            .apply()
        ExamWidget.updateAll(getApplication())
    }

    fun studyTime(docId: String) = prefs.getLong("study_$docId", 0L)

    /** Son [days] günün çalışma süreleri (bugün başta). */
    fun studyDays(days: Int): List<Pair<Long, Long>> {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        return (0 until days).map { i ->
            val t = now - i * dayMs
            t to prefs.getLong("studyday_${dayKey(t)}", 0L)
        }
    }

    /** Çalışma serisi: bugünden (bugün henüz çalışılmadıysa dünden) geriye, en az 1 dk çalışılan art arda gün sayısı. */
    fun studyStreak(): Int {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        fun studied(i: Int) = prefs.getLong("studyday_${dayKey(now - i * dayMs)}", 0L) >= 60_000L
        val start = if (studied(0)) 0 else 1
        var n = 0
        while (n < 366 && studied(start + n)) n++
        return n
    }

    private fun dayKey(t: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(t))

    // ---- Pomodoro ----

    /** Çalışan pomodoro: bitiş zamanı ve mola mı; null = kapalı. */
    var pomodoro by mutableStateOf<Pomodoro?>(null)
        private set

    data class Pomodoro(val endsAt: Long, val onBreak: Boolean, val workMin: Int, val breakMin: Int)

    private var pomodoroJob: Job? = null

    fun startPomodoro(workMin: Int, breakMin: Int) {
        pomodoroJob?.cancel()
        pomodoro = Pomodoro(System.currentTimeMillis() + workMin * 60_000L, false, workMin, breakMin)
        pomodoroJob = viewModelScope.launch {
            while (true) {
                val p = pomodoro ?: break
                val left = p.endsAt - System.currentTimeMillis()
                if (left > 0) {
                    delay(minOf(left, 1000L))
                    continue
                }
                // Süre doldu: çalışma ↔ mola
                val next = if (p.onBreak) p.workMin else p.breakMin
                pomodoro = p.copy(endsAt = System.currentTimeMillis() + next * 60_000L, onBreak = !p.onBreak)
                message = if (p.onBreak) "Mola bitti, çalışmaya devam! (${p.workMin} dk)" else "Mola zamanı! (${p.breakMin} dk)"
                vibrate()
            }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        pomodoro = null
    }

    private fun vibrate() {
        runCatching {
            val app = getApplication<Application>()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                app.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(android.os.Vibrator::class.java)
            } ?: return
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
        }
    }

    fun sorted(docs: List<DocRef>): List<DocRef> = when (sortMode) {
        SortMode.NAME -> docs.sortedWith(compareBy(com.sounote.app.data.NaturalOrder) { it.title })
        SortMode.ADDED -> docs.sortedByDescending { it.addedAt }
        SortMode.RECENT -> docs.sortedByDescending { maxOf(lastOpened(it), it.addedAt) }
    }

    fun openUserFolder(id: String) {
        path = emptyList()
        currentFolder = id
    }

    fun createFolder(name: String, color: Int, icon: String = "") =
        folderOp { repo.createFolder(name.trim().ifEmpty { "Klasör" }, currentFolder, color, icon) }
    fun updateFolder(f: UserFolder, name: String, color: Int, icon: String = f.icon) =
        folderOp { repo.updateFolder(f.id, name.trim().ifEmpty { f.name }, color, icon) }
    fun deleteFolder(f: UserFolder) = folderOp { repo.deleteFolder(f.id) }
    fun moveDoc(doc: DocRef, folderId: String?) = folderOp { repo.moveDoc(doc.id, folderId) }
    fun moveFolder(f: UserFolder, parentId: String?) = folderOp { repo.moveFolder(f.id, parentId) }

    private fun folderOp(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            userFolders = withContext(Dispatchers.IO) { repo.userFolders() }
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    // ---- Ders ses kaydı ----

    private var recorder: AudioRecorder? = null

    /** Kayıt başlangıç zamanı (uptime değil, duvar saati); 0 = kayıt yok. */
    var recordingSince by mutableStateOf(0L)
        private set

    var recordings by mutableStateOf<List<Recording>>(emptyList())
        private set

    private var recordingFile: String? = null

    /** Kayıt sürüyorsa dosya adı ve kaydın şu anki anı (çizgileri kayda bağlamak için). */
    fun currentRecording(): Pair<String, Long>? {
        val f = recordingFile ?: return null
        if (recordingSince == 0L) return null
        return f to (System.currentTimeMillis() - recordingSince)
    }

    fun recordingFileFor(doc: DocRef, name: String) = File(AudioNotes.dir(getApplication(), doc.id), name)

    fun startRecording(doc: DocRef) {
        if (recorder != null) return
        try {
            val r = AudioRecorder(getApplication())
            recordingFile = r.start(AudioNotes.dir(getApplication(), doc.id)).name
            recorder = r
            recordingSince = System.currentTimeMillis()
        } catch (e: Exception) {
            ErrorLog.record("Ses kaydı başlatılamadı", e)
            message = "Ses kaydı başlatılamadı"
        }
    }

    fun stopRecording(doc: DocRef?) {
        val r = recorder ?: return
        recorder = null
        recordingSince = 0L
        recordingFile = null
        val saved = r.stop()
        message = if (saved != null) "Ses kaydı kaydedildi (⋮ → Ses kayıtları)" else "Ses kaydı boş, kaydedilmedi"
        doc?.let(::loadRecordings)
    }

    fun loadRecordings(doc: DocRef) {
        viewModelScope.launch {
            recordings = withContext(Dispatchers.IO) { AudioNotes.list(getApplication(), doc.id) }
        }
    }

    fun deleteRecording(doc: DocRef, rec: Recording) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { rec.file.delete() }
            loadRecordings(doc)
        }
    }

    // ---- İçindekiler (PDF'in kendi bölüm listesi) ----

    var outline by mutableStateOf<List<PdfPages.OutlineEntry>?>(null)
        private set
    private var outlineFor: String? = null

    fun loadOutline(doc: DocRef) {
        val key = "${doc.id}:$docVersion"
        if (outlineFor == key) return
        outlineFor = key
        outline = null
        viewModelScope.launch {
            outline = withContext(Dispatchers.IO) {
                runCatching { PdfPages.outline(getApplication(), repo.resolveForOpen(doc).uri) }
                    .onFailure { ErrorLog.record("İçindekiler okunamadı", it) }
                    .getOrDefault(emptyList())
            }
        }
    }

    // ---- Bilgi kartları ----

    var cards by mutableStateOf<List<Card>>(emptyList())
        private set

    fun loadCards(doc: DocRef) {
        viewModelScope.launch {
            cards = withContext(Dispatchers.IO) { Flashcards.load(getApplication(), doc.id) }
        }
    }

    private fun saveCards(doc: DocRef, next: List<Card>) {
        cards = next
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Flashcards.save(getApplication(), doc.id, next) }
                .onFailure { ErrorLog.record("Kartlar kaydedilemedi", it) }
        }
    }

    fun addTextCard(doc: DocRef, front: String, back: String, page: Int) {
        saveCards(doc, cards + Card(System.currentTimeMillis(), front.trim(), null, back.trim(), page, due = System.currentTimeMillis()))
        message = "Kart eklendi"
    }

    /** Asistanın ürettiği soru-cevap kartlarını belgenin kartlarına ekler. */
    fun addGeneratedCards(doc: DocRef, pairs: List<Pair<String, String>>, page: Int) {
        if (pairs.isEmpty()) return
        val now = System.currentTimeMillis()
        // Görüntüleyicide açık belgenin kartları yüklü; değilse önce dosyadan oku.
        val base = if (openDoc?.id == doc.id) cards else Flashcards.load(getApplication(), doc.id)
        saveCards(doc, base + pairs.mapIndexed { i, (q, a) -> Card(now + i, q.trim(), null, a.trim(), page, due = now) })
        message = "${pairs.size} kart eklendi · sağ üstteki ⋮ → Bilgi kartları"
    }

    /** Sayfanın seçilen bölgesini (notlarıyla) görüntü olarak alıp kartın ön yüzü yapar. */
    fun addRegionCard(doc: DocRef, page: Int, region: RectF, front: String, back: String) {
        val content = openInk?.ink?.content(page) ?: PageContent.EMPTY
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val id = System.currentTimeMillis()
                    val dir = Flashcards.imagesDir(app, doc.id).apply { mkdirs() }
                    val name = "$id.png"
                    LibraryRepository.openFileDescriptor(app, repo.resolveForOpen(doc).uri).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            r.openPage(page).use { p ->
                                val rect = RectF(region).apply {
                                    inset(-6f, -6f)
                                    intersect(0f, 0f, p.width.toFloat(), p.height.toFloat())
                                }
                                val scale = (1200f / maxOf(rect.width(), rect.height())).coerceIn(1f, 4f)
                                val bmp = Bitmap.createBitmap(
                                    (rect.width() * scale).toInt().coerceAtLeast(1),
                                    (rect.height() * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                val m = Matrix().apply {
                                    setScale(scale, scale)
                                    postTranslate(-rect.left * scale, -rect.top * scale)
                                }
                                p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val canvas = Canvas(bmp)
                                canvas.concat(m)
                                ContentPainter(store.imagesDir(doc.id)).draw(canvas, content)
                                File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                    }
                    Card(id, front.trim(), name, back.trim(), page, due = System.currentTimeMillis())
                }
            }
            result.onSuccess {
                saveCards(doc, cards + it)
                message = "Kart eklendi (⋮ → Bilgi kartları)"
            }.onFailure {
                ErrorLog.record("Kart oluşturulamadı", it)
                message = "Kart oluşturulamadı"
            }
        }
    }

    fun reviewCard(doc: DocRef, card: Card, known: Boolean) {
        val updated = Flashcards.review(card, known)
        saveCards(doc, cards.map { if (it.id == card.id) updated else it })
    }

    fun deleteCard(doc: DocRef, card: Card) {
        saveCards(doc, cards.filter { it.id != card.id })
        card.frontImage?.let { name ->
            viewModelScope.launch(Dispatchers.IO) { File(Flashcards.imagesDir(getApplication(), doc.id), name).delete() }
        }
    }

    fun cardImage(doc: DocRef, card: Card): File? =
        card.frontImage?.let { File(Flashcards.imagesDir(getApplication(), doc.id), it) }

    // ---- Kaynağı kaybolan notlu belgeler ----

    /** Bağlı klasördeki kaynağı silinmiş/bağlantısı kaldırılmış ama notları olan belgeler. */
    var orphans by mutableStateOf<List<DocRef>>(emptyList())
        private set

    fun adoptOrphan(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.adoptCopy(doc) }
            refresh()
        }
    }

    fun deleteOrphan(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteCopy(doc.id)
                store.delete(doc.id)
            }
            refresh()
        }
    }

    // ---- Görüntüleyici: tam ekran, sayfayı resim olarak paylaş ----

    var fullScreen by mutableStateOf(false)
        private set

    fun toggleFullScreen() {
        fullScreen = !fullScreen
    }

    /** Sayfayı notlarıyla birlikte PNG olarak hazırlar (WhatsApp vb. ile göndermek için). */
    fun exportPageImage(doc: DocRef, page: Int, onReady: (File) -> Unit) {
        if (exporting) return
        val content = openInk?.ink?.content(page) ?: PageContent.EMPTY
        viewModelScope.launch {
            exporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val source = repo.resolveForOpen(doc).uri
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
                    val target = File(dir, "$name - sayfa ${page + 1}.png")
                    LibraryRepository.openFileDescriptor(app, source).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            r.openPage(page).use { p ->
                                val scale = (2400f / maxOf(p.width, p.height)).coerceIn(1f, 4f)
                                val bmp = Bitmap.createBitmap(
                                    (p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val canvas = Canvas(bmp)
                                canvas.scale(scale, scale)
                                ContentPainter(store.imagesDir(doc.id)).draw(canvas, content)
                                target.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                    }
                    target
                }
            }
            exporting = false
            result.onSuccess(onReady).onFailure {
                ErrorLog.record("Sayfa resmi oluşturulamadı", it)
                message = "Sayfa resmi oluşturulamadı"
            }
        }
    }

    // ---- Otomatik yedek ----

    /** Otomatik yedeğin yazıldığı klasör (SAF ağacı); null = kapalı. */
    var autoBackupFolder by mutableStateOf(prefs.getString("autoBackupTree", null))
        private set

    fun enableAutoBackup(tree: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            ErrorLog.record("Yedek klasörü izni alınamadı", e)
            message = "Bu klasöre yazılamıyor"
            return
        }
        prefs.edit().putString("autoBackupTree", tree.toString()).remove("autoBackupFp").apply()
        autoBackupFolder = tree.toString()
        runAutoBackup(force = true)
    }

    /** Otomatik yedek kapalıyken ve korunacak bir şey varken ana sayfada öneri kartı (Sonra = 7 gün sus). */
    var backupNudgeUntil by mutableStateOf(prefs.getLong("backupNudgeUntil", 0L))
        private set

    val showBackupNudge: Boolean
        get() = autoBackupFolder == null && System.currentTimeMillis() > backupNudgeUntil &&
            (imported.isNotEmpty() || textNotes.isNotEmpty())

    fun snoozeBackupNudge() {
        backupNudgeUntil = System.currentTimeMillis() + 7 * 86_400_000L
        prefs.edit().putLong("backupNudgeUntil", backupNudgeUntil).apply()
    }

    fun disableAutoBackup() {
        autoBackupFolder?.let { t ->
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(t),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove("autoBackupTree").apply()
        autoBackupFolder = null
    }

    fun lastAutoBackup() = prefs.getLong("autoBackupTime", 0L)

    /** Günde bir, sadece kütüphane değiştiyse. */
    private fun maybeAutoBackup() {
        if (autoBackupFolder == null) return
        if (System.currentTimeMillis() - lastAutoBackup() < AUTO_BACKUP_INTERVAL_MS) return
        runAutoBackup(force = false)
    }

    private var autoBackupRunning = false

    /**
     * İki dosya dönüşümlü yazılır (…-1.zip, …-2.zip): yazarken bir şey olursa bile
     * diğeri sağlam bir yedek olarak kalır.
     */
    fun runAutoBackup(force: Boolean) {
        val tree = autoBackupFolder?.let(Uri::parse) ?: return
        if (autoBackupRunning) return
        autoBackupRunning = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fp = Backup.fingerprint(app)
                    if (!force && fp == prefs.getString("autoBackupFp", null)) return@runCatching false
                    val slot = 1 - prefs.getInt("autoBackupSlot", 1)
                    val uri = findOrCreate(tree, "SouNote-otomatik-yedek-${slot + 1}.zip")
                    app.contentResolver.openOutputStream(uri, "wt")?.use { Backup.write(app, it) }
                        ?: error("Yedek dosyası açılamadı")
                    prefs.edit()
                        .putString("autoBackupFp", fp)
                        .putInt("autoBackupSlot", slot)
                        .putLong("autoBackupTime", System.currentTimeMillis())
                        .apply()
                    true
                }
            }
            autoBackupRunning = false
            result.onSuccess { if (it && force) message = "Otomatik yedek alındı" }
                .onFailure {
                    ErrorLog.record("Otomatik yedek alınamadı", it)
                    message = "Otomatik yedek alınamadı"
                }
        }
    }

    private fun findOrCreate(tree: Uri, name: String): Uri {
        val resolver = getApplication<Application>().contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "application/zip", name)
            ?: error("Yedek dosyası oluşturulamadı")
    }

    // ---- Yedekleme ----

    fun backup(target: Uri) {
        viewModelScope.launch {
            busy = true
            saveJob?.cancel()
            flushInk()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use {
                        Backup.write(getApplication(), it)
                    } ?: error("Dosya açılamadı")
                }.onFailure { ErrorLog.record("Yedeklenemedi", it) }.isSuccess
            }
            busy = false
            message = if (ok) "Yedek kaydedildi" else "Yedeklenemedi"
        }
    }

    fun restore(source: Uri) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(source)?.use {
                        Backup.restore(getApplication(), it)
                    } ?: error("Dosya açılamadı")
                    repo.reload()
                }
            }
            busy = false
            currentFolder = null
            path = emptyList()
            refresh()
            openText = null
            myExams = withContext(Dispatchers.IO) { examStore.load() }
            grades = withContext(Dispatchers.IO) { gradeStore.load() }
            message = result.fold(
                onSuccess = { "Yedek geri yüklendi" },
                onFailure = { "Geri yüklenemedi: ${it.message ?: it.javaClass.simpleName}" },
            )
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }

    // ---- Notlar: yükleme ve otomatik kayıt ----

    private class OpenInk(val doc: DocRef, val ink: InkDocument, val listener: (Int) -> Unit)

    private var openInk: OpenInk? = null
    private val dirtyPages = HashSet<Int>()
    private var saveJob: Job? = null

    /** Açık belgenin notları. Belge açılırken diskten yüklenir. */
    var ink by mutableStateOf(InkDocument())
        private set

    private fun attachInk(doc: DocRef, loaded: Map<Int, PageContent>) {
        detachInk()
        val document = InkDocument().apply { load(loaded) }
        val listener: (Int) -> Unit = { page -> onInkChanged(page) }
        document.addListener(listener)
        openInk = OpenInk(doc, document, listener)
        ink = document
    }

    private fun detachInk() {
        openInk?.let { it.ink.removeListener(it.listener) }
        openInk = null
    }

    private fun onInkChanged(page: Int) {
        dirtyPages += page
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            flushInk()
        }
    }

    /** Değişen sayfaları hemen diske yazar. Uygulama arka plana geçerken de çağrılır. */
    fun flush() {
        if (dirtyPages.isEmpty()) return
        saveJob?.cancel()
        viewModelScope.launch { flushInk() }
    }

    private suspend fun flushInk() {
        val open = openInk ?: return
        if (dirtyPages.isEmpty()) return
        val snapshot = dirtyPages.associateWith { open.ink.content(it) }
        dirtyPages.clear()
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                // Bağlı klasördeki belgeye ilk not: PDF'in kopyasını al.
                repo.ensureCopy(open.doc)
            } catch (e: Exception) {
                ErrorLog.record("Kopya alınamadı", e)
            }
            snapshot.forEach { (page, content) ->
                try {
                    store.save(open.doc.id, page, content)
                } catch (e: Exception) {
                    ErrorLog.record("Notlar kaydedilemedi", e)
                    withContext(Dispatchers.Main) { message = "Notlar kaydedilemedi!" }
                }
            }
        }
    }

    /** Belgeye eklenen resimlerin klasörü. */
    fun imagesDir(doc: DocRef): File = store.imagesDir(doc.id)

    /** Galeriden seçilen resmi belgeye kaydeder; [onReady] dosya adı ve piksel boyutuyla çağrılır. */
    fun importImage(doc: DocRef, uri: Uri, onReady: (file: String, width: Int, height: Int) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Images.import(getApplication(), uri, store.imagesDir(doc.id)) }
            }
            result.onSuccess { onReady(it.file, it.width, it.height) }.onFailure {
                ErrorLog.record("Resim eklenemedi", it)
                message = "Resim eklenemedi"
            }
        }
    }

    /** Doldurulan formun yazılacağı dosya. */
    fun formSaveTarget(doc: DocRef): File = repo.docFile(doc.id)

    fun open(doc: DocRef) {
        prefs.edit().putLong("opened_${doc.id}", System.currentTimeMillis()).apply()
        studyPause()
        viewModelScope.launch {
            // Başka belge açıkken (kısayol, hızlı not) önceki belgenin son notları kaybolmasın.
            saveJob?.cancel()
            flushInk()
            withContext(Dispatchers.IO) { repo.rememberLinked(doc) }
            val (resolved, content) = withContext(Dispatchers.IO) { repo.resolveForOpen(doc) to store.load(doc.id) }
            bookmarks = withContext(Dispatchers.IO) { store.loadBookmarks(doc.id) }
            attachInk(resolved, content)
            openDoc = resolved
            studyResume()
            pushShortcut(doc)
        }
    }

    // ---- Sayfalar: ekleme, silme, taşıma, yer imleri ----

    /** Sayfa yapısı değişince artar; görüntüleyici PDF'i yeniden açar. */
    var docVersion by mutableStateOf(0)
        private set

    var pagesBusy by mutableStateOf(false)
        private set

    var bookmarks by mutableStateOf<Set<Int>>(emptySet())
        private set

    var pagePanelOpen by mutableStateOf(false)
        private set

    fun togglePagePanel() {
        pagePanelOpen = !pagePanelOpen
    }

    var twoPage by mutableStateOf(prefs.getBoolean("twoPage", false))
        private set

    /** Gece okuma: görüntüleyicide renkler ters çevrilir. */
    var nightReading by mutableStateOf(prefs.getBoolean("nightReading", false))
        private set

    fun toggleNightReading() {
        nightReading = !nightReading
        prefs.edit().putBoolean("nightReading", nightReading).apply()
    }

    /** Başka bir PDF'in bütün sayfalarını [at] konumuna ekler (notlar kayar). */
    fun insertPdf(doc: DocRef, at: Int, source: Uri) =
        changePages(doc, focusPage = at, "PDF eklenemedi") { file ->
            val n = PdfPages.insertFrom(getApplication(), file, at, source)
            ({ i: Int -> if (i >= at) i + n else i })
        }

    fun updateTwoPage(on: Boolean) {
        twoPage = on
        prefs.edit().putBoolean("twoPage", on).apply()
    }

    fun toggleBookmark(doc: DocRef, page: Int) {
        val next = if (page in bookmarks) bookmarks - page else bookmarks + page
        bookmarks = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.saveBookmarks(doc.id, next) } }
    }

    /** Uygulamada oluşturulan defterin kâğıt rengi: araya eklenen sayfalar da aynı renkte olsun. */
    private fun paperOf(doc: DocRef) =
        enumValueOrNull<PaperColor>(prefs.getString("paper_${doc.id}", null)) ?: PaperColor.WHITE

    fun insertBlankPage(doc: DocRef, at: Int, template: PageTemplate) =
        changePages(doc, focusPage = at, "Sayfa eklenemedi") { file ->
            PdfPages.insertBlank(getApplication(), file, at, template, paperOf(doc))
            ({ i: Int -> if (i >= at) i + 1 else i })
        }

    fun deletePage(doc: DocRef, at: Int) =
        changePages(doc, focusPage = (at - 1).coerceAtLeast(0), "Sayfa silinemedi") { file ->
            PdfPages.delete(getApplication(), file, at)
            ({ i: Int -> if (i == at) null else if (i > at) i - 1 else i })
        }

    fun duplicatePage(doc: DocRef, at: Int) =
        changePages(doc, focusPage = at + 1, "Sayfa çoğaltılamadı", afterRemap = { store.copyPage(doc.id, at, at + 1) }) { file ->
            PdfPages.duplicate(getApplication(), file, at)
            ({ i: Int -> if (i > at) i + 1 else i })
        }

    fun movePage(doc: DocRef, from: Int, to: Int) =
        changePages(doc, focusPage = to, "Sayfa taşınamadı") { file ->
            PdfPages.move(getApplication(), file, from, to)
            ({ i: Int ->
                when {
                    i == from -> to
                    from < to && i in (from + 1)..to -> i - 1
                    from > to && i in to until from -> i + 1
                    else -> i
                }
            })
        }

    /**
     * PDF'in sayfa yapısını değiştirir: önce notlar kaydedilir, belge uygulama içine kopyalanır,
     * PDF düzenlenir, sonra notlar, yer imleri ve son sayfa yeni numaralara taşınır.
     * [edit] PDF'i değiştirip eski sayfa → yeni sayfa eşlemesini döndürür.
     */
    private fun changePages(
        doc: DocRef,
        focusPage: Int,
        error: String,
        afterRemap: (() -> Unit)? = null,
        edit: (File) -> (Int) -> Int?,
    ) {
        if (pagesBusy) return
        viewModelScope.launch {
            pagesBusy = true
            saveJob?.cancel()
            flushInk()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.ensureCopy(doc)
                    val map = edit(repo.docFile(doc.id))
                    store.remapPages(doc.id, map)
                    afterRemap?.invoke()
                    val marks = store.loadBookmarks(doc.id).mapNotNull(map).toSet()
                    store.saveBookmarks(doc.id, marks)
                    Triple(repo.resolveForOpen(doc), store.load(doc.id), marks)
                }
            }
            pagesBusy = false
            result.onSuccess { (resolved, content, marks) ->
                bookmarks = marks
                saveLastPage(doc, focusPage)
                attachInk(resolved, content)
                openDoc = resolved
                docVersion++
            }.onFailure {
                ErrorLog.record(error, it)
                message = "$error: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /** Hızlı not: tarihli, çizgili yeni defter açar (kısayoldan da). */
    fun quickNote() {
        val name = "Hızlı not " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.forLanguageTag("tr")).format(java.util.Date())
        createNotebook(name, PageTemplate.LINED)
    }

    /** Kısayoldan gelen belgeyi aç. */
    fun openById(id: String) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { repo.importedDocs() }.find { it.id == id }
            if (doc != null) open(doc) else message = "Belge bulunamadı"
        }
    }

    /** Son açılan belgeyi uygulama simgesinin kısayollarına ekle. */
    private fun pushShortcut(doc: DocRef) {
        if (!doc.imported) return
        runCatching {
            val app = getApplication<Application>()
            val intent = Intent(app, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DOC)
                .putExtra(MainActivity.EXTRA_DOC_ID, doc.id)
            val info = ShortcutInfoCompat.Builder(app, "doc_${doc.id}")
                .setShortLabel(doc.title.take(24).ifEmpty { "Belge" })
                .setLongLabel(doc.title.take(48).ifEmpty { "Belge" })
                .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_doc))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(app, info)
        }
    }

    /** Boş defter: şablonlu A4 sayfalardan oluşan yeni bir PDF olarak kütüphaneye eklenir ve açılır. */
    fun createNotebook(
        title: String, template: PageTemplate, size: PaperSize = PaperSize.A4, landscape: Boolean = false,
        paper: PaperColor = PaperColor.WHITE,
    ) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.createDocument(title.trim().ifEmpty { "Defter" }, currentFolder) { file ->
                        PdfPages.createNotebook(getApplication(), file, template, pages = 1, size = size, landscape = landscape, paper = paper)
                    }
                }
            }
            result.onSuccess { if (paper != PaperColor.WHITE) prefs.edit().putString("paper_${it.id}", paper.name).apply() }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            result.onSuccess { open(it) }.onFailure {
                ErrorLog.record("Defter oluşturulamadı", it)
                message = "Defter oluşturulamadı"
            }
        }
    }

    fun closeDoc() {
        studyPause()
        fullScreen = false
        stopRecording(null)
        viewModelScope.launch {
            saveJob?.cancel()
            flushInk()
            detachInk()
            openDoc = null
            refresh()
        }
    }

    // ---- Dışa aktarma ----

    var exporting by mutableStateOf(false)
        private set

    /** Notlu PDF'i oluşturur; [onReady] dosyayla çağrılır (paylaşmak ya da kaydetmek için). */
    /** Belgeyi notlarıyla .sounote paketi yapar (arkadaşına göndermek için). */
    fun sharePackage(doc: DocRef, onReady: (File) -> Unit) {
        if (exporting) return
        viewModelScope.launch {
            exporting = true
            saveJob?.cancel()
            flushInk()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val source = repo.resolveForOpen(doc).uri
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                    val target = File(dir, "$name.${com.sounote.app.data.SouNotePackage.EXTENSION}")
                    val pdf = app.contentResolver.openInputStream(source) ?: throw java.io.IOException("PDF açılamadı")
                    com.sounote.app.data.SouNotePackage.write(pdf, File(app.filesDir, "ink/${doc.id}"), doc.title, target)
                    target
                }
            }
            exporting = false
            result.onSuccess(onReady).onFailure {
                ErrorLog.record("Paket hazırlanamadı", it)
                message = "Paket hazırlanamadı: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /** Dışarıdan gelen dosya: SouNote paketiyse notlarıyla, değilse PDF olarak içe aktarılır. */
    fun importShared(uri: Uri) {
        val app = getApplication<Application>()
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        if (!com.sounote.app.data.SouNotePackage.looksLikePackage(app, uri, name)) {
            importPdfs(listOf(uri), openAfter = true)
            return
        }
        viewModelScope.launch {
            busy = true
            val folder = currentFolder
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    com.sounote.app.data.SouNotePackage.read(app, uri, { title, write -> repo.createDocument(title, folder, write) }, { it.id })
                }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            r.onSuccess { doc ->
                message = "\"${doc.title}\" notlarıyla eklendi"
                path = emptyList()
                open(doc)
            }.onFailure {
                ErrorLog.record("Paket açılamadı", it)
                message = it.message ?: "Paket açılamadı"
            }
        }
    }

    fun export(doc: DocRef, pages: Set<Int>? = null, label: String = "", onReady: (File) -> Unit) {
        if (exporting) return
        viewModelScope.launch {
            exporting = true
            saveJob?.cancel()
            flushInk()
            val snapshot = openInk?.ink?.snapshot() ?: emptyMap()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Form doldurulduysa ya da not alındıysa uygulama içindeki kopya güncel olandır.
                    val source = repo.resolveForOpen(doc).uri
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                    val suffix = when {
                        label.isNotEmpty() -> " $label"
                        pages == null -> ""
                        pages.size == 1 -> " s${pages.first() + 1}"
                        else -> " s${pages.min() + 1}-${pages.max() + 1}"
                    }
                    val target = File(dir, "$name (notlu)$suffix.pdf")
                    PdfExporter.export(getApplication(), source, snapshot, store.imagesDir(doc.id), target, pages)
                    target
                }
            }
            exporting = false
            result.onSuccess(onReady).onFailure {
                ErrorLog.record("Dışa aktarılamadı", it)
                message = "PDF oluşturulamadı: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /** Oluşturulan PDF'i kullanıcının seçtiği yere (Dosyalar, Drive…) kopyalar. */
    fun saveExportTo(file: File, target: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Dosya açılamadı")
                }.isSuccess
            }
            message = if (ok) "PDF kaydedildi" else "PDF kaydedilemedi"
        }
    }

    // ---- Kütüphane ----

    /** Uygulamaya her dönüldüğünde çağrılır: bağlı klasöre yeni eklenen PDF'ler böyle görünür. */
    fun refresh() {
        viewModelScope.launch {
            val (imp, roots) = withContext(Dispatchers.IO) { repo.importedDocs() to repo.linkedFolders() }
            imported = imp
            rootFolders = roots
            userFolders = withContext(Dispatchers.IO) { repo.userFolders() }
            loadTextNotes()
            if (currentFolder != null && userFolders.none { it.id == currentFolder }) currentFolder = null
            orphans = withContext(Dispatchers.IO) { runCatching { repo.orphanedCopies() }.getOrDefault(emptyList()) }
            trash = withContext(Dispatchers.IO) {
                repo.expiredTrash(TRASH_DAYS * 24L * 60 * 60 * 1000).forEach { purgeNow(it) }
                repo.trashDocs()
            }
            if (openDoc == null) maybeAutoBackup()
            loaded = true
            path.lastOrNull()?.let { loadListing(it) }
        }
    }

    private suspend fun loadListing(folder: FolderRef) {
        try {
            val result = withContext(Dispatchers.IO) { repo.listFolder(folder) }
            if (path.lastOrNull() == folder) listing = result
        } catch (e: Exception) {
            ErrorLog.record("Klasör okunamadı", e)
            message = "\"${folder.name}\" klasörüne erişilemiyor. Silinmiş ya da izni kaldırılmış olabilir."
            path = emptyList()
        }
    }

    val canNavigateUp get() = path.isNotEmpty() || currentFolder != null

    fun openFolder(folder: FolderRef) {
        path = path + folder
        listing = FolderListing(emptyList(), emptyList())
        viewModelScope.launch { loadListing(folder) }
    }

    fun navigateUp() {
        if (path.isEmpty()) {
            currentFolder = userFolders.find { it.id == currentFolder }?.parentId
            return
        }
        path = path.dropLast(1)
        path.lastOrNull()?.let { f ->
            listing = FolderListing(emptyList(), emptyList())
            viewModelScope.launch { loadListing(f) }
        }
    }

    fun importPdfs(uris: List<Uri>, openAfter: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            var last: DocRef? = null
            var failed = 0
            for (uri in uris) {
                try {
                    val folder = currentFolder
                    last = withContext(Dispatchers.IO) { repo.importPdf(uri, folder) }
                } catch (e: Exception) {
                    ErrorLog.record("İçe aktarılamadı: $uri", e)
                    failed++
                }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            val ok = uris.size - failed
            val resolver = getApplication<Application>().contentResolver
            val word = uris.any { u ->
                val type = runCatching { resolver.getType(u) }.getOrNull().orEmpty()
                "word" in type || "officedocument" in type ||
                    u.toString().endsWith(".docx", true) || u.toString().endsWith(".doc", true)
            }
            message = when {
                word && ok == 0 -> "Word dosyası doğrudan açılmaz. Önce Word ya da WPS'te \"PDF olarak kaydet\" de, sonra PDF'i içe aktar."
                failed == 0 && ok == 1 -> null
                failed == 0 -> "$ok PDF içe aktarıldı"
                ok == 0 -> "PDF açılamadı. Dosya bozuk ya da şifreli olabilir."
                else -> "$ok PDF içe aktarıldı, $failed tanesi açılamadı"
            }
            if (openAfter && last != null) {
                path = emptyList()
                open(last)
            }
        }
    }

    fun linkFolder(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val folder = withContext(Dispatchers.IO) { repo.linkFolder(treeUri) }
                rootFolders = withContext(Dispatchers.IO) { repo.linkedFolders() }
                message = "\"${folder.name}\" bağlandı. İçine attığın PDF'ler burada görünecek."
            } catch (e: Exception) {
                ErrorLog.record("Klasör bağlanamadı", e)
                message = "Klasör bağlanamadı"
            }
        }
    }

    fun unlinkFolder(folder: FolderRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.unlinkFolder(folder.treeUri) }
            rootFolders = withContext(Dispatchers.IO) { repo.linkedFolders() }
        }
    }

    fun rename(doc: DocRef, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.rename(doc.id, clean) }
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    /** Silinen belge önce çöp kutusuna gider (30 gün). */
    fun delete(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.moveToTrash(doc.id) }
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(getApplication(), listOf("doc_${doc.id}")) }
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            trash = withContext(Dispatchers.IO) { repo.trashDocs() }
            message = "\"${doc.title}\" çöp kutusuna taşındı (Ayarlar → Çöp kutusu)"
        }
    }

    var trash by mutableStateOf<List<DocRef>>(emptyList())
        private set

    fun restoreFromTrash(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.restoreFromTrash(doc.id) }
            refresh()
        }
    }

    /** Kalıcı sil: PDF, notlar, ses kayıtları, kartlar. */
    fun purge(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { purgeNow(doc.id) }
            prefs.edit().remove(pageKey(doc)).remove("study_${doc.id}").apply()
            refresh()
        }
    }

    fun emptyTrash() {
        val ids = trash.map { it.id }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { purgeNow(it) } }
            refresh()
        }
    }

    private fun purgeNow(id: String) {
        repo.delete(id)
        store.delete(id)
        if (isFavorite(QuickItem.DOC, id)) viewModelScope.launch { saveQuick(quickItems.filterNot { it.type == QuickItem.DOC && it.id == id }) }
    }

    fun trashDaysLeft(doc: DocRef): Int {
        val at = repo.deletedAt(doc.id)
        if (at == 0L) return TRASH_DAYS
        val left = TRASH_DAYS - ((System.currentTimeMillis() - at) / (24L * 60 * 60 * 1000)).toInt()
        return left.coerceAtLeast(0)
    }

    fun lastPage(doc: DocRef) = prefs.getInt(pageKey(doc), 0)

    fun saveLastPage(doc: DocRef, page: Int) {
        prefs.edit().putInt(pageKey(doc), page).apply()
    }

    private fun pageKey(doc: DocRef) = "page_${doc.id}"

    override fun onCleared() {
        recorder?.stop()
        recorder = null
        // Son değişiklikler kaybolmasın.
        val open = openInk ?: return
        val snapshot = dirtyPages.associateWith { open.ink.content(it) }
        snapshot.forEach { (page, content) -> runCatching { store.save(open.doc.id, page, content) } }
    }

    companion object {
        private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

        private const val TAG = "LibraryViewModel"
        private val COURSE_COLORS = intArrayOf(
            0xFF5B7FD6.toInt(), 0xFF3FA36B.toInt(), 0xFFE0893A.toInt(), 0xFFD65B7F.toInt(),
            0xFF8E63CE.toInt(), 0xFF3AA6B9.toInt(), 0xFFC9A227.toInt(), 0xFF6D7DE0.toInt(),
        )
        private const val SAVE_DELAY_MS = 800L
        private const val AUTO_BACKUP_INTERVAL_MS = 20L * 60 * 60 * 1000
        const val TRASH_DAYS = 30
        /** "Yenilikler" penceresinin ana sürümü: 2.0'a güncelleyen kullanıcı bir kez görür. */
        const val MAJOR = 2
        const val LOCK_AFTER_MS = 60_000L
    }
}
