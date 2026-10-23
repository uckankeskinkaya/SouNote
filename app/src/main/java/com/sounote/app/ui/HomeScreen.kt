package com.sounote.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
    var newNotebook by remember { mutableStateOf(false) }
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
        DropdownMenuItem(text = { Text("Sil", color = MaterialTheme.colorScheme.error) }, onClick = { dismiss(); deleting = doc })
    }
    val importPdf = { pickPdfs.launch(arrayOf("application/pdf")) }

    val recent = remember(vm.imported) {
        vm.imported.filter { vm.lastOpened(it) > 0 }.sortedByDescending { vm.lastOpened(it) }.take(12)
    }
    val days = remember(vm.imported) { vm.studyDays(7) }
    val empty = vm.loaded && vm.imported.isEmpty() && vm.rootFolders.isEmpty()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            ) {
                Hero(
                    greeting = greeting(vm.userName),
                    todayMs = days.first().second,
                    docCount = vm.imported.size,
                )

                val last = recent.firstOrNull()
                if (empty) {
                    StartCard(onNotebook = { newNotebook = true }, onImport = importPdf)
                } else if (last != null) {
                    ContinueCard(last, vm.lastPage(last), vm.lastOpened(last), menu = { d -> docMenu(last, d) }) { vm.open(last) }
                }

                Section("Hızlı başla")
                QuickActions(
                    listOf(
                        QuickAction(rememberVectorPainter(Icons.Default.Add), "Yeni defter", "Şablonlu boş sayfalar", Color(0xFF2F5BD3)) {
                            newNotebook = true
                        },
                        QuickAction(rememberVectorPainter(Icons.Default.Edit), "Hızlı not", "Hemen yazmaya başla", Color(0xFFE0893A)) {
                            vm.quickNote()
                        },
                        QuickAction(painterResource(R.drawable.ic_text), "Yazılı not", "Klavyeyle not, yapılacaklar", Color(0xFF8E63CE)) {
                            vm.newTextNote()
                        },
                        QuickAction(painterResource(R.drawable.ic_pdf), "PDF içe aktar", "Ders notlarını getir", Color(0xFFD64545)) {
                            importPdf()
                        },
                        QuickAction(painterResource(R.drawable.ic_folder), "Klasör bağla", "Yeni PDF'ler kendiliğinden gelsin", Color(0xFF3FA36B)) {
                            pickFolder.launch(null)
                        },
                    ),
                )

                if (recent.size > 1) {
                    Section("Son açılanlar", action = "Kütüphane") { vm.tab = LibraryViewModel.Tab.LIBRARY }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(recent, key = { it.id }) { doc ->
                            Box(Modifier.width(132.dp)) { DocCard(doc, onClick = { vm.open(doc) }, menu = { d -> docMenu(doc, d) }) }
                        }
                    }
                }

                if (vm.textNotes.isNotEmpty()) {
                    Section("Yazılı notlar", action = "Yeni not") { vm.newTextNote() }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(vm.textNotes.take(12), key = { "t" + it.id }) { n ->
                            Box(Modifier.width(132.dp)) {
                                TextNoteCard(n, onClick = { vm.openTextNote(n) }, menu = { d -> TextNoteMenuItems(n, vm, d) { deletingText = n } })
                            }
                        }
                    }
                }

                BoxWithConstraints {
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
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.gradient)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.9f, center = Offset(size.width * 0.95f, 0f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.45f, center = Offset(size.width * 0.72f, size.height * 1.05f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(date, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Text(greeting, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(motto, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    HeroChip("⏱ Bugün ${formatStudy(todayMs)}")
                    HeroChip("📚 $docCount belge")
                }
            }
            AppLogo(84.dp, Modifier.padding(start = 16.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().card().combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }).padding(16.dp),
    ) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
        Box(
            Modifier
                .width(84.dp)
                .aspectRatio(0.75f)
                .clip(thumbShape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
        ) {
            Thumbnail(doc)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text("Kaldığın yerden devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(doc.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Sayfa ${page + 1} · $ago",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onOpen) { Text("Devam et") }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
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
    val onClick: () -> Unit,
)

@Composable
private fun QuickActions(actions: List<QuickAction>) {
    BoxWithConstraints {
        val columns = if (maxWidth > 900.dp) 5 else if (maxWidth > 640.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            actions.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { a -> ActionTile(a, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(a: QuickAction, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.card().clickable(onClick = a.onClick).padding(18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(a.color.copy(alpha = 0.15f)),
        ) {
            Icon(a.icon, contentDescription = null, tint = a.color, modifier = Modifier.size(24.dp))
        }
        Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            a.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeekCard(days: List<Pair<Long, Long>>, modifier: Modifier) {
    val dayName = remember { SimpleDateFormat("EEE", TR) }
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
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
                            .height((84f * ms / max).dp.coerceAtLeast(4.dp))
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
        Crossfade(targetState = index, label = "tip") { i ->
            Text(TIPS[i], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = 72.dp))
        }
        TextButton(onClick = { index = (index + 1) % TIPS.size }, modifier = Modifier.align(Alignment.End)) {
            Text("Sonraki ipucu")
        }
    }
}
