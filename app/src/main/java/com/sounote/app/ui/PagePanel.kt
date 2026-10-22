@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sounote.app.ink.InkDocument
import com.sounote.app.pdf.PageTemplate
import com.sounote.app.pdf.PdfPages
import com.sounote.app.pdf.PageThumbLoader

/** Sayfa işlemleri (paneldeki menüden). */
class PageActions(
    val onGoTo: (Int) -> Unit,
    val onInsert: (at: Int, template: PageTemplate) -> Unit,
    val onDelete: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onToggleBookmark: (Int) -> Unit,
    val onDuplicate: (Int) -> Unit = {},
)

/** Soldaki sayfa paneli: küçük resimler, yer imleri, sayfa ekle/sil/taşı. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagePanel(
    pageCount: Int,
    currentPage: Int,
    bookmarks: Set<Int>,
    ink: InkDocument,
    loader: PageThumbLoader,
    actions: PageActions,
    outline: List<PdfPages.OutlineEntry>? = null,
    onShowOutline: () -> Unit = {},
) {
    var onlyBookmarks by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var inserting by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    val pages = if (onlyBookmarks) (0 until pageCount).filter { it in bookmarks } else (0 until pageCount).toList()
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) {
        if (currentPage in 0 until pageCount) grid.scrollToItem(currentPage)
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.width(280.dp).fillMaxHeight()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = !showOutline,
                    onClick = { showOutline = false },
                    label = { Text("Sayfalar") },
                )
                FilterChip(
                    selected = showOutline,
                    onClick = {
                        showOutline = true
                        onShowOutline()
                    },
                    label = { Text("İçindekiler") },
                )
                if (!showOutline) {
                    IconToggle(onlyBookmarks) { onlyBookmarks = !onlyBookmarks }
                }
            }
            HorizontalDivider()
            if (showOutline) {
                OutlineList(outline, onGoTo = actions.onGoTo)
                return@Column
            }
            if (onlyBookmarks && pages.isEmpty()) {
                Text(
                    "Yer imi yok. Üst çubuktaki ☆ ile ya da sayfaya basılı tutarak ekleyebilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = grid,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(pages, key = { it }) { i ->
                    var menu by remember { mutableStateOf(false) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(onClick = { actions.onGoTo(i) }, onLongClick = { menu = true }),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .border(
                                    if (i == currentPage) 2.5.dp else 1.dp,
                                    if (i == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(6.dp),
                                ),
                        ) {
                            PageThumb(i, ink, loader)
                            if (i in bookmarks) {
                                Icon(
                                    Icons.Default.Star, null,
                                    tint = Color(0xFFF2B705),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Önüne boş sayfa ekle") }, onClick = { menu = false; inserting = i })
                                DropdownMenuItem(text = { Text("Arkasına boş sayfa ekle") }, onClick = { menu = false; inserting = i + 1 })
                                DropdownMenuItem(text = { Text("Çoğalt (notlarıyla)") }, onClick = { menu = false; actions.onDuplicate(i) })
                                if (i > 0) DropdownMenuItem(text = { Text("Yukarı taşı") }, onClick = { menu = false; actions.onMove(i, i - 1) })
                                if (i < pageCount - 1) DropdownMenuItem(text = { Text("Aşağı taşı") }, onClick = { menu = false; actions.onMove(i, i + 1) })
                                DropdownMenuItem(
                                    text = { Text(if (i in bookmarks) "Yer imini kaldır" else "Yer imi ekle") },
                                    onClick = { menu = false; actions.onToggleBookmark(i) },
                                )
                                if (pageCount > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Sayfayı sil", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; deleting = i },
                                    )
                                }
                            }
                        }
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            HorizontalDivider()
            OutlinedButton(
                onClick = { inserting = pageCount },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text("Sona boş sayfa ekle") }
        }
    }

    inserting?.let { at ->
        TemplateDialog(
            title = "Boş sayfa ekle",
            onPick = { t ->
                inserting = null
                actions.onInsert(at, t)
            },
            onDismiss = { inserting = null },
        )
    }
    deleting?.let { i ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("${i + 1}. sayfa silinsin mi?") },
            text = { Text("Sayfa ve üzerindeki notlar silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.onDelete(i)
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

/** Sadece yer imleri düğmesi (yıldız). */
@Composable
private fun IconToggle(on: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Star,
            contentDescription = "Sadece yer imleri",
            tint = if (on) Color(0xFFF2B705) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** PDF'in kendi içindekiler listesi; dokununca o sayfaya. */
@Composable
private fun OutlineList(outline: List<PdfPages.OutlineEntry>?, onGoTo: (Int) -> Unit) {
    when {
        outline == null -> Text("Yükleniyor…", modifier = Modifier.padding(16.dp))
        outline.isEmpty() -> Text(
            "Bu PDF'te içindekiler bilgisi yok.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(outline.size) { k ->
                val e = outline[k]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { if (e.page >= 0) onGoTo(e.page) })
                        .padding(start = (12 + e.level * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Text(
                        e.title,
                        style = if (e.level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (e.page >= 0) {
                        Text("${e.page + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumb(page: Int, ink: InkDocument, loader: PageThumbLoader) {
    val content = ink.content(page)
    val bitmap by produceState<Bitmap?>(loader.cached(page, content), page, content) {
        if (value == null) value = loader.load(page, content)
    }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = "Sayfa ${page + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Boş sayfa şablonu seçimi. */
@Composable
fun TemplateDialog(title: String, onPick: (PageTemplate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageTemplate.entries.forEach { t ->
                    TextButton(onClick = { onPick(t) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
