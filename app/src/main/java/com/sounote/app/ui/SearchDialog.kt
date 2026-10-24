@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.LibraryViewModel
import com.sounote.app.LibraryViewModel.SearchHit
import com.sounote.app.data.SearchIndex
import kotlinx.coroutines.delay

/** Arama sonucunun türü (filtre çipleri ve gruplar). */
private enum class HitKind(val label: String) {
    COURSE("Dersler"), FOLDER("Klasörler"), DOC("Belgeler"), NOTE("Notlar"), EXAM("Sınav ve ödevler"), PDF("PDF içeriği"), CARD("Kartlar"),
}

private fun kindOf(h: SearchHit) = when (h) {
    is SearchHit.CourseHit, is SearchHit.TopicHit -> HitKind.COURSE
    is SearchHit.FolderHit -> HitKind.FOLDER
    is SearchHit.DocTitle -> HitKind.DOC
    is SearchHit.Note -> HitKind.NOTE
    is SearchHit.ExamHit -> HitKind.EXAM
    is SearchHit.PdfPage -> HitKind.PDF
    is SearchHit.FlashCard -> HitKind.CARD
}

/**
 * Evrensel arama: dersler ve konular, klasörler, belge adları, yazılı notlar, sınav/ödevler,
 * PDF içerikleri (sayfa sayfa) ve bilgi kartları. Hepsi cihazda aranır; hiçbir şey sunucuya gitmez.
 */
@Composable
fun SearchDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var cardHit by remember { mutableStateOf<SearchHit.FlashCard?>(null) }
    var filter by remember { mutableStateOf<HitKind?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.ensureSearchIndex()
        runCatching { focus.requestFocus() }
    }
    // Yazmayı bırakınca ara (her harfte değil).
    LaunchedEffect(query, vm.searchVersion, vm.imported, vm.textNotes) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        delay(250)
        searching = true
        results = vm.search(query)
        searching = false
    }

    /** Sonuca dokununca: aramayı hatırla, pencereyi kapat, içeriği aç. */
    fun go(action: () -> Unit) {
        vm.rememberSearch(query)
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(8.dp),
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kapat") }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Ders, belge, not, sınav ara…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Temizle") }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                val counts = results.groupingBy(::kindOf).eachCount()
                if (results.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    ) {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tümü (${results.size})") })
                        HitKind.entries.filter { (counts[it] ?: 0) > 0 }.forEach { k ->
                            FilterChip(selected = filter == k, onClick = { filter = if (filter == k) null else k }, label = { Text("${k.label} (${counts[k]})") })
                        }
                    }
                }
                vm.indexing?.let { (i, n) ->
                    Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            "Belgeler aranabilir hale getiriliyor (${i + 1}/$n)… İlk seferde biraz sürer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(progress = { (i + 1) / n.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    val q = SearchIndex.norm(query.trim())
                    val shown = results.filter { filter == null || kindOf(it) == filter }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
                    ) {
                        if (q.length < 2) {
                            if (vm.recentSearches.isNotEmpty()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Son aramalar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = vm::clearRecentSearches) { Text("Temizle") }
                                    }
                                }
                                item {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        vm.recentSearches.forEach { r ->
                                            AssistChip(onClick = { query = r }, label = { Text("🕘 $r") })
                                        }
                                    }
                                }
                            }
                            item {
                                Hint(
                                    "Bir kelime yaz: dersler ve konular, klasörler, belge adları, yazılı notlar, sınav ve ödevler, " +
                                        "PDF'lerin içindeki yazılar (sayfa sayfa) ve bilgi kartları aranır. Arama cihazda yapılır. " +
                                        "Taranmış (fotoğraf) PDF'lerin içi aranamaz.",
                                )
                            }
                        } else if (!searching && results.isEmpty()) {
                            item { Hint("\"${query.trim()}\" bulunamadı") }
                        }
                        shown.groupBy(::kindOf).toSortedMap().forEach { (kind, hits) ->
                            item(key = "h:" + kind.name) {
                                Text(
                                    "${kind.label} · ${hits.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                )
                            }
                            items(hits) { hit ->
                                when (hit) {
                                    is SearchHit.CourseHit -> HitRow(hit.course.emoji.ifEmpty { "📘" }, highlight(hit.course.name, q), "Ders · ${hit.course.topics.size} konu") {
                                        go {
                                            vm.focusCourse = hit.course.id
                                            vm.tab = LibraryViewModel.Tab.COURSES
                                        }
                                    }
                                    is SearchHit.TopicHit -> HitRow("🧩", highlight(hit.topic.name, q), "${hit.course.name} · konu · ${hit.topic.resources.size} kaynak") {
                                        go {
                                            vm.focusTopic = hit.topic.id
                                            vm.tab = LibraryViewModel.Tab.COURSES
                                        }
                                    }
                                    is SearchHit.FolderHit -> HitRow(hit.folder.icon.ifEmpty { "📁" }, highlight(hit.folder.name, q), "Klasör") {
                                        go {
                                            vm.openUserFolder(hit.folder.id)
                                            vm.tab = LibraryViewModel.Tab.LIBRARY
                                        }
                                    }
                                    is SearchHit.ExamHit -> HitRow(
                                        hit.exam.kindEmoji,
                                        highlight(hit.exam.title.ifBlank { hit.exam.course }, q),
                                        "${hit.exam.kindLabel} · ${hit.exam.course} · ${countdownText(hit.exam.time)}",
                                    ) { go { vm.tab = LibraryViewModel.Tab.EXAMS } }
                                    is SearchHit.DocTitle -> HitRow("📄", highlight(hit.doc.title, q), "Belge") { go { vm.open(hit.doc) } }
                                    is SearchHit.PdfPage -> HitRow("🔎", highlight(hit.snippet, q), "${hit.doc.title} · sayfa ${hit.page + 1}") {
                                        go { vm.openAt(hit.doc, hit.page) }
                                    }
                                    is SearchHit.Note -> HitRow("📝", highlight(hit.note.displayTitle, q), hit.snippet) { go { vm.openTextNote(hit.note) } }
                                    is SearchHit.FlashCard -> HitRow("🃏", highlight(hit.card.frontText.ifBlank { "(resimli kart)" }, q), "${hit.doc.title} · kart") {
                                        vm.rememberSearch(query)
                                        cardHit = hit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    cardHit?.let { hit ->
        AlertDialog(
            onDismissRequest = { cardHit = null },
            title = { Text(hit.card.frontText.ifBlank { "Bilgi kartı" }) },
            text = { Text(hit.card.back.ifBlank { "(arka yüz boş)" }) },
            confirmButton = {
                TextButton(onClick = {
                    cardHit = null
                    onDismiss()
                    if (hit.card.page >= 0) vm.openAt(hit.doc, hit.card.page) else vm.open(hit.doc)
                }) { Text("Belgeyi aç") }
            },
            dismissButton = { TextButton(onClick = { cardHit = null }) { Text("Kapat") } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
    )
}

@Composable
private fun HitRow(emoji: String, title: AnnotatedString, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .appear()
            .card()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        ) { Text(emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Aranan kelimeyi kalın ve renkli gösterir. */
@Composable
private fun highlight(text: String, q: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        if (q.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = SearchIndex.norm(text)
        var i = 0
        while (true) {
            val j = lower.indexOf(q, i)
            // Türkçe küçük harf dönüşümünde uzunluk değişirse vurgu atlanır.
            if (j < 0 || lower.length != text.length) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, j))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, background = color.copy(alpha = 0.12f))) {
                append(text.substring(j, j + q.length))
            }
            i = j + q.length
        }
    }
}
