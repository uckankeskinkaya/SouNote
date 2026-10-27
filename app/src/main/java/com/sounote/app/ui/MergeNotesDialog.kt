package com.sounote.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.sounote.app.LibraryViewModel

/** Paylaşılan paketteki belge kütüphanede zaten var: notları birleştir ya da ayrı kopya olarak ekle. */
@Composable
fun MergeNotesDialog(vm: LibraryViewModel, p: LibraryViewModel.PendingMerge) {
    val layer = LibraryViewModel.mergeLayerName(p.ext.sender)
    AlertDialog(
        onDismissRequest = { vm.answerMerge(null) },
        title = { Text("Bu belge sende zaten var") },
        text = {
            Column {
                Text("\"${p.target.title}\"", style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (p.ext.sender.isNotBlank()) "Gönderen: ${p.ext.sender}. " else "") +
                        "Gelen notları kendi notlarına ekleyebilirsin. Çizgiler ayrı bir katmana (\"$layer\") gelir; " +
                        "Katmanlar'dan gizleyebilir ya da silebilirsin. Senin notlarına dokunulmaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { vm.answerMerge(true) }) { Text("Notları birleştir") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.answerMerge(null) }) { Text("Vazgeç") }
                TextButton(onClick = { vm.answerMerge(false) }) { Text("Ayrı kopya") }
            }
        },
    )
}
