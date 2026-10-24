@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.EssayText
import com.sounote.app.data.QuickItem
import com.sounote.app.data.TextNote
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

/**
 * Akademik yazı stüdyosu: essay'ler ve taslaklar (yazılı notlar). Yeni essay şablonu, arama,
 * filtreler, kelime sayıları ve giriş–gelişme–sonuç yapısının durumu.
 */
@Composable
fun WritingScreen(vm: LibraryViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 tümü, 1 essay, 2 sabitlenen, 3 favori
    var newMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TextNote?>(null) }
    var graderOpen by remember { mutableStateOf(false) }
    val q = query.trim().lowercase(TR)
    val notes = vm.textNotes.filter { n ->
        (q.isEmpty() || n.title.lowercase(TR).contains(q) || n.plainBody.lowercase(TR).contains(q)) &&
            when (filter) {
                1 -> EssayText.isEssay(n.body)
                2 -> n.pinned
                3 -> vm.isFavorite(QuickItem.NOTE, n.id)
                else -> true
            }
    }
    val totalWords = remember(vm.textNotes) { vm.textNotes.sumOf { EssayText.countWords(it.plainBody) } }

    Scaffold(
        topBar = { SouTopBar("Yazı stüdyosu", subtitle = "Essay'ler, taslaklar ve yazılı notlar") },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { newMenu = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Yeni yazı") },
                )
                DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("🏗️ Essay (giriş, gelişme, sonuç)") },
                        onClick = { newMenu = false; vm.newTextNote(EssayText.TEMPLATE) },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("📝 Serbest metin") },
                        onClick = { newMenu = false; vm.newTextNote() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("⭐ Süreli essay, asistan puanlasın") },
                        onClick = { newMenu = false; graderOpen = true },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SouSearchField(query, { query = it }, "Yazılarında ara")
                        Banner(
                            "Süreli essay yaz, asistan L4 ölçütleriyle puanlasın ve düzeltsin.",
                            BannerKind.INFO,
                            action = "Essay puanla",
                            onAction = { graderOpen = true },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("Tümü", "🏗️ Essay'ler", "📌 Sabitlenen", "⭐ Favoriler").forEachIndexed { i, label ->
                                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label, maxLines = 1) })
                            }
                        }
                        if (vm.textNotes.isNotEmpty()) {
                            Text(
                                "${vm.textNotes.size} yazı · toplam $totalWords kelime · yazdıkça kendiliğinden kaydedilir",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (vm.textNotes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            "✍️", "İlk yazına başla",
                            "Essay şablonu giriş, gelişme ve sonuç başlıklarıyla gelir; istersen serbest yazarsın. " +
                                "Yazma koçu dil bilgisi, kelime ve yapı önerileri verir; metnini sen onaylamadan değiştirmez.",
                        ) {
                            Button(onClick = { vm.newTextNote(EssayText.TEMPLATE) }) { Text("Yeni essay") }
                            OutlinedButton(onClick = { vm.newTextNote() }) { Text("Serbest metin") }
                        }
                    }
                } else if (notes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState("🔍", "Sonuç yok", "Aramayı ya da filtreyi değiştir.")
                    }
                }
                items(notes, key = { it.id }) { n ->
                    Box(Modifier.animateItem(), propagateMinConstraints = true) {
                        WritingCard(
                            n,
                            favorite = vm.isFavorite(QuickItem.NOTE, n.id),
                            onClick = { vm.openTextNote(n) },
                            menu = { d -> TextNoteMenuItems(n, vm, d) { deleting = n } },
                        )
                    }
                }
            }
        }
    }

    if (graderOpen) EssayGraderScreen(vm) { graderOpen = false }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Yazı silinsin mi?") },
            text = { Text("\"${n.displayTitle}\" kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { vm.deleteTextNote(n); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

/** Yazı kartı: başlık, metnin başı, kelime sayısı ve essay yapısı. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WritingCard(note: TextNote, favorite: Boolean, onClick: () -> Unit, menu: @Composable (dismiss: () -> Unit) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val words = remember(note.body) { EssayText.countWords(note.plainBody) }
    val structure = remember(note.body) { EssayText.structure(note.body) }
    val essay = structure.count { it } >= 2
    val ago = remember(note.updated) {
        DateUtils.getRelativeTimeSpanString(note.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.98f)
            .card()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.pinned) Text("📌", fontSize = 14.sp)
            if (favorite) Text(" ★", color = LocalSouColors.current.star, fontSize = 16.sp)
        }
        Text(
            EssayText.plain(note.plainBody).lineSequence().filter { it.isNotBlank() }.take(4).joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("$words kelime")
            if (essay) {
                listOf("Giriş", "Gelişme", "Sonuç").forEachIndexed { i, label ->
                    Tag((if (structure[i]) "✓ " else "○ ") + label, highlighted = structure[i])
                }
            }
            if (note.hasSketch) Tag("🎨 çizim")
        }
        Text(ago, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
    }
}

@Composable
private fun Tag(text: String, highlighted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
