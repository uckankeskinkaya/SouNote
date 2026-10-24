@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sounote.app.LibraryViewModel
import com.sounote.app.ErrorLog
import com.sounote.app.data.BodyPart
import com.sounote.app.data.CoachSuggestion
import com.sounote.app.data.Essay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em
import com.sounote.app.data.Sketch
import com.sounote.app.data.Sketches
import com.sounote.app.data.TextNote
import com.sounote.app.data.joinBody
import com.sounote.app.data.splitBody
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHECK_OPEN = "☐ "
private const val CHECK_DONE = "☑ "
private const val BULLET = "• "

/** Satır başı işaretleri: Enter'a basınca yeni satıra da konur. */
private val LIST_PREFIXES = listOf(CHECK_OPEN, CHECK_DONE, BULLET)

/** İmlecin bulunduğu satırın başlangıcı ve sonu. */
private fun lineBounds(text: String, pos: Int): Pair<Int, Int> {
    val p = pos.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (p - 1).coerceAtLeast(0)).let { if (p == 0 || it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', p).let { if (it < 0) text.length else it }
    return start to end
}

/** Satırın başındaki işareti değiştirir: yoksa ekler, aynısıysa kaldırır. */
private fun setLinePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val (start, _) = lineBounds(text, v.selection.start)
    val current = LIST_PREFIXES.firstOrNull { text.startsWith(it, start) }
    val replacement = when {
        prefix == CHECK_OPEN && current == CHECK_OPEN -> CHECK_DONE
        prefix == CHECK_OPEN && current == CHECK_DONE -> CHECK_OPEN
        current == prefix -> ""
        else -> prefix
    }
    val removed = current?.length ?: 0
    val newText = text.substring(0, start) + replacement + text.substring(start + removed)
    val shift = replacement.length - removed
    val cursor = (v.selection.start + shift).coerceIn(start + replacement.length, newText.length)
    return TextFieldValue(newText, TextRange(cursor))
}

/** Enter'a basılınca liste işaretini yeni satıra taşır; boş maddede Enter listeyi bitirir. */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return new
    val pos = new.selection.start
    if (pos == 0 || new.text[pos - 1] != '\n') return new
    val (start, end) = lineBounds(new.text, pos - 1)
    val line = new.text.substring(start, end)
    val prefix = LIST_PREFIXES.firstOrNull { line.startsWith(it) } ?: return new
    if (line == prefix) {
        // Boş madde: işareti ve yeni satırı kaldır.
        val t = new.text.substring(0, start) + new.text.substring(pos)
        return TextFieldValue(t, TextRange(start))
    }
    val next = if (prefix == CHECK_DONE) CHECK_OPEN else prefix
    val t = new.text.substring(0, pos) + next + new.text.substring(pos)
    return TextFieldValue(t, TextRange(pos + next.length))
}

/** Düzenleyicideki parça: yazı alanı ya da çizim. */
private sealed interface EditorPart {
    data class Text(val value: TextFieldValue) : EditorPart
    data class Drawing(val id: String) : EditorPart

    fun toBodyPart(): BodyPart = when (this) {
        is Text -> BodyPart.Text(value.text)
        is Drawing -> BodyPart.Drawing(id)
    }
}

private fun insertAtCursor(v: TextFieldValue, t: String): TextFieldValue {
    val s = v.selection.start.coerceIn(0, v.text.length)
    val e = v.selection.end.coerceIn(s, v.text.length)
    return TextFieldValue(v.text.substring(0, s) + t + v.text.substring(e), TextRange(s + t.length))
}

private fun wordCount(text: String) = Essay.countWords(text)

/** Satır başına "# " / "## " başlık işareti koyar ya da kaldırır. */
private fun toggleHeading(v: TextFieldValue, level: Int): TextFieldValue {
    val text = v.text
    val (start, _) = lineBounds(text, v.selection.start)
    val current = Regex("^#{1,3} ").find(text.substring(start))?.value.orEmpty()
    val wanted = "#".repeat(level) + " "
    val replacement = if (current == wanted) "" else wanted
    val newText = text.substring(0, start) + replacement + text.substring(start + current.length)
    val cursor = (v.selection.start + replacement.length - current.length).coerceIn(start, newText.length)
    return TextFieldValue(newText, TextRange(cursor))
}

/** Seçimi [marker] ile sarar (kalın: **, italik: _); seçim yoksa işaret çiftini koyup imleci arasına alır. */
private fun wrapSelection(v: TextFieldValue, marker: String): TextFieldValue {
    val s = v.selection.min.coerceIn(0, v.text.length)
    val e = v.selection.max.coerceIn(s, v.text.length)
    val inner = v.text.substring(s, e)
    val t = v.text.substring(0, s) + marker + inner + marker + v.text.substring(e)
    return if (s == e) TextFieldValue(t, TextRange(s + marker.length)) else TextFieldValue(t, TextRange(s, e + 2 * marker.length))
}

/** Yazarken Markdown işaretlerini biçimli gösterir; metin ve imleç konumları aynen kalır. */
private class MarkdownVisual(private val accent: Color, private val dim: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(s)
        var lineStart = 0
        for (line in s.split('\n')) {
            Regex("^(#{1,3}) ").find(line)?.let { m ->
                val level = m.groupValues[1].length
                b.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (level == 1) 1.3.em else if (level == 2) 1.15.em else 1.05.em, color = accent),
                    lineStart, lineStart + line.length,
                )
                b.addStyle(SpanStyle(color = dim), lineStart, lineStart + m.value.length)
            }
            lineStart += line.length + 1
        }
        Regex("\\*\\*(.+?)\\*\\*").findAll(s).forEach { m ->
            b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            b.addStyle(SpanStyle(color = dim), m.range.first, m.range.first + 2)
            b.addStyle(SpanStyle(color = dim), m.range.last - 1, m.range.last + 1)
        }
        Regex("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])").findAll(s).forEach { m ->
            b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
            b.addStyle(SpanStyle(color = dim), m.range.first, m.range.first + 1)
            b.addStyle(SpanStyle(color = dim), m.range.last, m.range.last + 1)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Klavyeyle yazılan not: başlık + metin, liste/yapılacaklar, otomatik kayıt. */
@Composable
fun TextNoteScreen(note: TextNote, vm: LibraryViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var title by remember(note.id) { mutableStateOf(note.title) }
    // Not metni yazı ve çizim parçalarından oluşur; her yazı parçası ayrı bir yazı alanı.
    var parts by remember(note.id) {
        mutableStateOf<List<EditorPart>>(
            splitBody(note.body).map {
                when (it) {
                    is BodyPart.Text -> EditorPart.Text(TextFieldValue(it.text, TextRange(it.text.length)))
                    is BodyPart.Drawing -> EditorPart.Drawing(it.id)
                }
            },
        )
    }
    var focusIdx by remember(note.id) { mutableStateOf(parts.indexOfLast { it is EditorPart.Text }) }
    var pad by remember { mutableStateOf(false) }
    var assistant by remember { mutableStateOf(false) }
    // Odak modu: araç çubukları gizlenir, sadece metin kalır.
    var focus by rememberSaveable { mutableStateOf(false) }
    // Yan panel: 1 = yazma koçu, 2 = yapı (bölümler)
    var panel by remember { mutableStateOf(0) }
    // Koç önerisi uygulanmadan önceki hali (bir adım geri almak için)
    var beforeCoach by remember { mutableStateOf<List<EditorPart>?>(null) }
    var editingSketch by remember { mutableStateOf<Sketch?>(null) }
    val sketchCache = remember(note.id) { mutableStateMapOf<String, Sketch>() }
    val scope = rememberCoroutineScope()
    val bodyText = joinBody(parts.map { it.toBodyPart() })

    // Çizimleri arka planda yükle
    LaunchedEffect(parts) {
        parts.filterIsInstance<EditorPart.Drawing>().forEach { d ->
            if (d.id !in sketchCache) sketchCache[d.id] = withContext(Dispatchers.IO) { Sketches.load(context, d.id) }
        }
    }

    fun updateFocused(f: (TextFieldValue) -> TextFieldValue) {
        val i = focusIdx.takeIf { parts.getOrNull(it) is EditorPart.Text } ?: parts.indexOfLast { it is EditorPart.Text }
        val cur = parts[i] as EditorPart.Text
        parts = parts.toMutableList().also { it[i] = EditorPart.Text(f(cur.value)) }
        focusIdx = i
    }

    /** Çizimi imlecin olduğu yere ekler: yazı parçası ikiye bölünür. */
    fun insertSketch(id: String) {
        val i = focusIdx.takeIf { parts.getOrNull(it) is EditorPart.Text } ?: parts.indexOfLast { it is EditorPart.Text }
        val v = (parts[i] as EditorPart.Text).value
        val at = v.selection.start.coerceIn(0, v.text.length)
        val before = v.text.substring(0, at).removeSuffix("\n")
        val after = v.text.substring(at).removePrefix("\n")
        parts = parts.toMutableList().also {
            it[i] = EditorPart.Text(TextFieldValue(before, TextRange(before.length)))
            it.add(i + 1, EditorPart.Drawing(id))
            it.add(i + 2, EditorPart.Text(TextFieldValue(after, TextRange(0))))
        }
        focusIdx = i + 2
    }

    /** Çizimi kaldırır; iki yanındaki yazılar birleşir. */
    fun removeSketch(id: String) {
        val i = parts.indexOfFirst { it is EditorPart.Drawing && it.id == id }
        if (i < 0) return
        val prev = (parts.getOrNull(i - 1) as? EditorPart.Text)?.value?.text.orEmpty()
        val next = (parts.getOrNull(i + 1) as? EditorPart.Text)?.value?.text.orEmpty()
        val merged = listOf(prev, next).filter { it.isNotEmpty() }.joinToString("\n")
        parts = parts.toMutableList().also {
            it.subList(maxOf(0, i - 1), minOf(it.size, i + 2)).clear()
            it.add(maxOf(0, i - 1), EditorPart.Text(TextFieldValue(merged, TextRange(prev.length))))
        }
        focusIdx = maxOf(0, i - 1)
    }
    /** Koçun önerisini metne uygular (kullanıcı "Uygula" deyince). Metinde bulunamazsa false. */
    fun applySuggestion(sug: CoachSuggestion): Boolean {
        val i = parts.indexOfFirst { it is EditorPart.Text && it.value.text.contains(sug.original) }
        if (i < 0) return false
        val v = (parts[i] as EditorPart.Text).value
        val at = v.text.indexOf(sug.original)
        val t = v.text.replaceRange(at, at + sug.original.length, sug.suggestion)
        beforeCoach = parts
        parts = parts.toMutableList().also { it[i] = EditorPart.Text(TextFieldValue(t, TextRange(at + sug.suggestion.length))) }
        return true
    }

    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val latestTitle by rememberUpdatedState(title)
    val latestBody by rememberUpdatedState(bodyText)
    val fontSize = vm.textFontSize

    // Yazmayı bırakınca yarım saniye içinde kaydet; ekrandan çıkarken de kaydet.
    LaunchedEffect(note.id, title, bodyText) {
        delay(500)
        vm.saveTextNote(note.id, title, bodyText)
    }
    DisposableEffect(note.id) {
        onDispose { vm.saveTextNote(note.id, latestTitle, latestBody) }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    fun close() {
        vm.saveTextNote(note.id, title, bodyText)
        vm.closeTextNote()
    }

    fun shareText() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            val plain = note.copy(body = bodyText).plainBody
            putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) plain else "$title\n\n$plain")
        }
        context.startActivity(Intent.createChooser(send, "Notu paylaş"))
    }

    fun sharePdf() {
        vm.saveTextNote(note.id, title, bodyText)
        val current = vm.openText ?: note.copy(title = title, body = bodyText)
        vm.textNotePdf(current) { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "PDF'i paylaş"))
        }
    }

    val updated = vm.openText?.updated ?: note.updated
    val edited = remember(updated) { SimpleDateFormat("d MMMM HH:mm", Locale.forLanguageTag("tr")).format(Date(updated)) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (focus) "Odak modu" else "Yazı stüdyosu", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = ::close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    TextButton(onClick = { focus = !focus; if (focus) panel = 0 }) { Text(if (focus) "Odaktan çık" else "🎯 Odak") }
                    if (!focus) TextButton(onClick = { vm.updateTextFontSize(fontSize - 2f) }) { Text("A−", fontSize = 14.sp) }
                    if (!focus) TextButton(onClick = { vm.updateTextFontSize(fontSize + 2f) }) { Text("A+", fontSize = 18.sp) }
                    if (!focus) Box {
                        IconButton(onClick = { shareMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Paylaş")
                        }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(text = { Text("Metin olarak paylaş") }, onClick = { shareMenu = false; shareText() })
                            DropdownMenuItem(text = { Text("PDF olarak paylaş") }, onClick = { shareMenu = false; sharePdf() })
                        }
                    }
                    if (!focus) Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Diğer")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val pinned = vm.openText?.pinned == true
                            DropdownMenuItem(
                                text = { Text(if (pinned) "Sabitlemeyi kaldır" else "Başa sabitle") },
                                onClick = {
                                    menu = false
                                    vm.saveTextNote(note.id, title, bodyText)
                                    vm.textNotes.find { it.id == note.id }?.let(vm::togglePinText)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Deftere çevir (kalemle yaz)") },
                                onClick = {
                                    menu = false
                                    vm.saveTextNote(note.id, title, bodyText)
                                    vm.textNoteToDocument(vm.openText ?: note.copy(title = title, body = bodyText))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            // Biçim ve araç düğmeleri (odak modunda gizli)
            if (!focus) Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                AssistChip(onClick = { panel = if (panel == 1) 0 else 1 }, label = { Text("🎓 Yazma koçu") })
                AssistChip(onClick = { panel = if (panel == 2) 0 else 2 }, label = { Text("📑 Yapı") })
                AssistChip(onClick = { updateFocused { toggleHeading(it, 1) } }, label = { Text("H1", fontWeight = FontWeight.Bold) })
                AssistChip(onClick = { updateFocused { toggleHeading(it, 2) } }, label = { Text("H2", fontWeight = FontWeight.SemiBold) })
                AssistChip(onClick = { updateFocused { wrapSelection(it, "**") } }, label = { Text("B", fontWeight = FontWeight.Bold) })
                AssistChip(onClick = { updateFocused { wrapSelection(it, "_") } }, label = { Text("I", fontStyle = FontStyle.Italic) })
                AssistChip(
                    onClick = {
                        updateFocused { v ->
                            val prefix = if (v.text.isBlank() || v.text.endsWith("\n\n")) "" else if (v.text.endsWith("\n")) "\n" else "\n\n"
                            TextFieldValue(v.text + prefix + Essay.TEMPLATE, TextRange(v.text.length + prefix.length + "## Introduction\n".length))
                        }
                    },
                    label = { Text("🏗️ Essay şablonu") },
                )
                AssistChip(onClick = { assistant = true }, label = { Text("🤖 Asistan") })
                AssistChip(onClick = { pad = !pad }, label = { Text(if (pad) "✍️ Kalemle yaz (açık)" else "✍️ Kalemle yaz") })
                AssistChip(onClick = { editingSketch = Sketch(Sketches.newId(), emptyList()) }, label = { Text("🎨 Çizim ekle") })
                AssistChip(onClick = { updateFocused { setLinePrefix(it, CHECK_OPEN) } }, label = { Text("☐ Yapılacak") })
                AssistChip(onClick = { updateFocused { setLinePrefix(it, BULLET) } }, label = { Text("• Madde") })
                AssistChip(
                    onClick = {
                        val stamp = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr")).format(Date())
                        updateFocused { insertAtCursor(it, stamp) }
                    },
                    label = { Text("📅 Tarih") },
                )
            }
            if (!focus) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val side = panel != 0 && maxWidth >= 900.dp
            Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = if (focus) 720.dp else 860.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                ) {
                    val textColor = MaterialTheme.colorScheme.onBackground
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.replace("\n", " ").take(120) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = (fontSize + 8).sp, fontWeight = FontWeight.Bold, color = textColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (title.isEmpty()) {
                                Text("Başlık", fontSize = (fontSize + 8).sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.35f))
                            }
                            inner()
                        },
                    )
                    val plain = note.copy(body = bodyText).plainBody
                    Text(
                        "$edited · ${wordCount(plain)} kelime · ${plain.count { !it.isWhitespace() }} karakter · otomatik kaydedilir",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                    )
                    val textStyle = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp, color = textColor)
                    val visual = MarkdownVisual(MaterialTheme.colorScheme.primary, textColor.copy(alpha = 0.35f))
                    val lastText = parts.indexOfLast { it is EditorPart.Text }
                    parts.forEachIndexed { i, part ->
                        when (part) {
                            is EditorPart.Text -> BasicTextField(
                                value = part.value,
                                onValueChange = { v ->
                                    parts = parts.toMutableList().also { it[i] = EditorPart.Text(continueList(part.value, v)) }
                                    focusIdx = i
                                },
                                textStyle = textStyle,
                                visualTransformation = visual,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (i == lastText) Modifier.heightIn(min = 300.dp) else Modifier)
                                    .onFocusChanged { if (it.isFocused) focusIdx = i },
                                decorationBox = { inner ->
                                    if (part.value.text.isEmpty() && parts.size == 1) {
                                        Text(
                                            "Yazmaya başla… ✍️ ile kalemle de yazabilirsin",
                                            fontSize = fontSize.sp,
                                            color = textColor.copy(alpha = 0.35f),
                                        )
                                    }
                                    inner()
                                },
                            )
                            is EditorPart.Drawing -> {
                                val sketch = sketchCache[part.id]
                                if (sketch != null) {
                                    SketchView(sketch, onEdit = { editingSketch = sketch }, onDelete = { removeSketch(part.id) })
                                }
                            }
                        }
                    }
                }
            }
            if (side) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(Modifier.width(380.dp).fillMaxHeight()) {
                    WritingSidePanel(
                        vm, panel, bodyText,
                        onApply = ::applySuggestion,
                        canUndo = beforeCoach != null,
                        onUndo = { beforeCoach?.let { parts = it }; beforeCoach = null },
                        onClose = { panel = 0 },
                    )
                }
            }
            }
            if (panel != 0 && !side) {
                ActionSheet(if (panel == 1) "🎓 Yazma koçu" else "📑 Yapı", onDismiss = { panel = 0 }) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        WritingSidePanel(
                            vm, panel, bodyText,
                            onApply = ::applySuggestion,
                            canUndo = beforeCoach != null,
                            onUndo = { beforeCoach?.let { parts = it }; beforeCoach = null },
                            onClose = { panel = 0 },
                            showHeader = false,
                        )
                    }
                }
            }
            }
            if (pad) {
                HandwritingPad(
                    onText = { t ->
                        updateFocused { v ->
                            val before = v.text.substring(0, v.selection.start.coerceIn(0, v.text.length))
                            val needsSpace = t != " " && before.isNotEmpty() && !before.endsWith(" ") && !before.endsWith("\n")
                            insertAtCursor(v, if (needsSpace) " $t" else t)
                        }
                    },
                    onBackspace = {
                        updateFocused { v ->
                            val at = v.selection.start.coerceIn(0, v.text.length)
                            if (at == 0) v else TextFieldValue(v.text.removeRange(at - 1, at), TextRange(at - 1))
                        }
                    },
                    onNewLine = { updateFocused { insertAtCursor(it, "\n") } },
                    onClose = { pad = false },
                )
            }
        }
    }

    if (assistant) {
        AssistantDialog(
            vm,
            AssistantSource.Note(title.ifBlank { "Başlıksız not" }, note.copy(body = bodyText).plainBody),
            onDismiss = { assistant = false },
        )
    }

    editingSketch?.let { sk ->
        SketchEditor(
            initial = sk,
            onSave = { saved ->
                val isNew = parts.none { it is EditorPart.Drawing && it.id == saved.id }
                sketchCache[saved.id] = saved
                scope.launch(Dispatchers.IO) {
                    runCatching { Sketches.save(context, saved) }.onFailure { ErrorLog.record("Çizim kaydedilemedi", it) }
                }
                if (isNew) insertSketch(saved.id) else parts = parts.toList()
                editingSketch = null
                vm.saveTextNote(note.id, title, joinBody(parts.map { it.toBodyPart() }))
            },
            onDismiss = { editingSketch = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Not silinsin mi?") },
            text = { Text("Bu not kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    title = ""
                    parts = listOf(EditorPart.Text(TextFieldValue("")))
                    vm.deleteTextNote(vm.openText ?: note)
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

/** Kütüphane ve ana sayfadaki yazılı not kartı: kâğıt üstünde metnin başı. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextNoteCard(
    note: TextNote,
    onClick: () -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val ago = remember(note.updated) {
        DateUtils.getRelativeTimeSpanString(note.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Column(Modifier.pressScale().combinedClickable(onClick = onClick, onLongClick = if (menu != null) ({ menuOpen = true }) else null)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(shape)
                .background(Color(0xFFFFFDF6))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    note.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C20),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (note.hasSketch) "🎨 " else "") + note.plainBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A4E57),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.pinned) Text("📌", modifier = Modifier.align(Alignment.TopEnd))
            Text(
                "Aa",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (menu != null) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
            }
        }
        Text(
            note.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            ago,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Yazılı notu metin olarak paylaş. */
internal fun shareTextNote(context: android.content.Context, note: TextNote) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, note.title)
        putExtra(Intent.EXTRA_TEXT, if (note.title.isBlank()) note.body else "${note.title}\n\n${note.body}")
    }
    context.startActivity(Intent.createChooser(send, "Notu paylaş"))
}

/** PDF dosyasını paylaşma penceresi. */
internal fun sharePdfFile(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "PDF'i paylaş"))
}

/** SouNote paketini (notlu belge) paylaşır: arkadaşın SouNote ile açınca notlar düzenlenebilir gelir. */
internal fun sharePackageFile(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "SouNote ile aç: notlarımla birlikte gelir 📒")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Notlu belgeyi gönder"))
}

/** Yazılı not kartına basılı tutunca: aç, sabitle, paylaş, deftere çevir, sil. */
@Composable
internal fun TextNoteMenuItems(note: TextNote, vm: LibraryViewModel, dismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    DropdownMenuItem(text = { Text("Aç") }, onClick = { dismiss(); vm.openTextNote(note) })
    val fav = vm.isFavorite(com.sounote.app.data.QuickItem.NOTE, note.id)
    DropdownMenuItem(
        text = { Text(if (fav) "Favorilerden çıkar" else "Favorilere ekle") },
        onClick = { dismiss(); vm.toggleFavorite(com.sounote.app.data.QuickItem.NOTE, note.id) },
    )
    DropdownMenuItem(
        text = { Text(if (note.pinned) "Sabitlemeyi kaldır" else "Başa sabitle") },
        onClick = { dismiss(); vm.togglePinText(note) },
    )
    DropdownMenuItem(text = { Text("Metin olarak paylaş") }, onClick = { dismiss(); shareTextNote(context, note) })
    DropdownMenuItem(
        text = { Text("PDF olarak paylaş") },
        onClick = { dismiss(); vm.textNotePdf(note) { sharePdfFile(context, it) } },
    )
    DropdownMenuItem(text = { Text("Deftere çevir (kalemle yaz)") }, onClick = { dismiss(); vm.textNoteToDocument(note) })
    DropdownMenuItem(
        text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
        onClick = { dismiss(); onDelete() },
    )
}
