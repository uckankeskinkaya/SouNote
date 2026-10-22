package com.sounote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Notlu PDF hangi sayfalarla oluşturulsun: tümü, bu sayfa ya da aralık. Sonuç 0'dan başlayan aralık (null = tümü). */
@Composable
fun ExportDialog(
    pageCount: Int,
    currentPage: Int,
    toFile: Boolean,
    onExport: (IntRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(0) } // 0 tümü, 1 bu sayfa, 2 aralık
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("$pageCount") }
    val f = from.toIntOrNull()
    val t = to.toIntOrNull()
    val rangeValid = f != null && t != null && f in 1..pageCount && t in f..pageCount

    @Composable
    fun Option(index: Int, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = choice == index, onClick = { choice = index })
            Text(label)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (toFile) "Notlu PDF'i kaydet" else "Notlu PDF'i paylaş") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Option(0, "Bütün sayfalar ($pageCount)")
                Option(1, "Sadece bu sayfa (${currentPage + 1})")
                Option(2, "Sayfa aralığı")
                if (choice == 2) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = from,
                            onValueChange = { v -> from = v.filter { it.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text("İlk") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                        Text("–")
                        OutlinedTextField(
                            value = to,
                            onValueChange = { v -> to = v.filter { it.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text("Son") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    if (!rangeValid) {
                        Text(
                            "1 ile $pageCount arasında, ilk ≤ son olmalı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice != 2 || rangeValid,
                onClick = {
                    onExport(
                        when (choice) {
                            1 -> currentPage..currentPage
                            2 -> (f!! - 1)..(t!! - 1)
                            else -> null
                        },
                    )
                },
            ) { Text(if (toFile) "Kaydet" else "Paylaş") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
