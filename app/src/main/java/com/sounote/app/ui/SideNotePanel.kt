package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.DocRef
import com.sounote.app.data.EssayText
import kotlinx.coroutines.delay

/**
 * Büyük tablette PDF'in yanında açılan yazılı not. Belgeye bağlıdır (her belgenin kendi notu),
 * yazılı notlar arasında da görünür; yazdıkça kaydedilir. Çizim ekranını yavaşlatmasın diye animasyonsuz.
 */
@Composable
internal fun SideNotePanel(vm: LibraryViewModel, doc: DocRef, onClose: () -> Unit) {
    val note = remember(doc.id) { vm.sideNoteFor(doc) }
    var text by remember(note.id) { mutableStateOf(note.body) }
    val latest by rememberUpdatedState(text)
    LaunchedEffect(note.id, text) {
        if (text == note.body) return@LaunchedEffect
        delay(500)
        vm.saveTextNote(note.id, note.title, text)
    }
    DisposableEffect(note.id) {
        onDispose { if (latest != note.body) vm.saveTextNote(note.id, note.title, latest) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("📝 Yan not", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${EssayText.countWords(text)} kelime · yazılı notlarında da görünür",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Yan notu kapat") }
        }
        val color = MaterialTheme.colorScheme.onSurface
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 24.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "Bu belgeyle ilgili notlarını yaz…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}
