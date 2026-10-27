package com.sounote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.DocRef
import com.sounote.app.ink.InkDocument
import com.sounote.app.pdf.PdfContainer

/**
 * Tablette ikinci belge, açık belgenin yanında: okumak için (kaydır, yakınlaştır, notları görünür).
 * Not alma ana bölmede; bu bölme belgeyi değiştirmez, sadece kaldığı sayfayı hatırlar.
 */
@Composable
fun SideDocPane(vm: LibraryViewModel, doc: DocRef, darkTheme: Boolean, onChange: () -> Unit, onClose: () -> Unit) {
    var ink by remember(doc.id) { mutableStateOf<InkDocument?>(null) }
    var error by remember(doc.id) { mutableStateOf(false) }
    LaunchedEffect(doc.id) { ink = vm.inkForSide(doc) }
    val layers = remember(doc.id) { vm.loadLayers(doc) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Icon(SouIcons.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                doc.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            TextButton(onClick = onChange) { Text("Değiştir") }
            IconButton(onClick = onClose) { Icon(SouIcons.Close, contentDescription = "Yan belgeyi kapat") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val loaded = ink
            when {
                error -> Text("PDF açılamadı", textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                loaded == null -> Unit
                else -> androidx.compose.runtime.key(doc.id) {
                    AndroidView(
                        factory = { ctx ->
                            PdfContainer(ctx).also { container ->
                                container.setLowLatency(false)
                                container.pdfView.apply {
                                    readOnly = true
                                    onPageChanged = { p, _ -> vm.saveLastPage(doc, p) }
                                    onError = { error = true }
                                    imagesDir = vm.imagesDir(doc)
                                    setDarkTheme(darkTheme)
                                    this.ink = loaded
                                    this.layers = layers
                                    open(doc.uri, vm.lastPage(doc))
                                }
                            }
                        },
                        update = { it.pdfView.setDarkTheme(darkTheme) },
                        onRelease = { it.release() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Yan yana açılacak belgeyi seç: son açılanlar üstte, adla aranır. */
@Composable
fun SideDocPicker(vm: LibraryViewModel, current: DocRef, onPick: (DocRef) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val docs = remember(vm.imported) {
        vm.imported.filter { it.id != current.id }.sortedByDescending { vm.lastOpened(it) }
    }
    val shown = docs.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yan yana aç") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Belge ara") },
                    leadingIcon = { Icon(SouIcons.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(
                        "Kütüphanende başka belge yok",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Icon(SouIcons.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
