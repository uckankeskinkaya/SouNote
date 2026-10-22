package com.sounote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.ink.TextBox
import com.sounote.app.ink.ToolState
import com.sounote.app.pdf.FormField
import kotlin.math.roundToInt

/** Metin kutusu yazma/düzenleme. Metni boş bırakıp kaydedersen kutu silinir. */
@Composable
fun TextBoxDialog(
    box: TextBox,
    isNew: Boolean,
    onSave: (TextBox) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(box.id) { mutableStateOf(TextFieldValue(box.text, TextRange(box.text.length))) }
    var size by remember(box.id) { mutableStateOf(box.fontSize) }
    var color by remember(box.id) { mutableStateOf(box.color) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Yazı ekle" else "Yazıyı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Yazı sayfada beyaz kâğıt üstünde duracak: burada da öyle göster, renkler kaybolmasın.
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 3,
                    placeholder = { Text("Buraya yaz…", color = Color(0xFF8A8F99)) },
                    textStyle = TextStyle(color = Color(color), fontSize = 18.sp),
                    colors = paperFieldColors(Color(color)),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).focusRequester(focus),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Boyut", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = size,
                        onValueChange = { size = it.roundToInt().toFloat() },
                        valueRange = 6f..48f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("${size.roundToInt()} pt", modifier = Modifier.width(48.dp))
                }
                ColorRow(ToolState.PEN_COLORS, color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(box.copy(text = value.text.trimEnd(), fontSize = size, color = color)) }) {
                Text("Tamam")
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Sil") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

/** Beyaz "kâğıt" görünümlü yazı alanı: seçilen yazı rengi koyu temada da okunur. */
@Composable
private fun paperFieldColors(textColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = textColor,
    unfocusedTextColor = textColor,
    cursorColor = textColor,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0xFFC9CCD3),
)

/** PDF form metin alanını doldurma. */
@Composable
fun FormTextDialog(field: FormField, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(field) { mutableStateOf(TextFieldValue(field.value, TextRange(field.value.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Form alanı") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (field.maxLength <= 0 || it.text.length <= field.maxLength) value = it
                },
                singleLine = !field.multiLine,
                minLines = if (field.multiLine) 3 else 1,
                colors = paperFieldColors(Color(0xFF1B1B1F)),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.text) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** PDF form listesinden seçim. */
@Composable
fun FormChoiceDialog(field: FormField, onSave: (IntArray) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(field) { mutableStateOf(field.selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seç") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(field.items) { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (field.multiSelect) {
                                    if (i in selected) selected - i else selected + i
                                } else {
                                    setOf(i)
                                }
                            },
                    ) {
                        if (field.multiSelect) {
                            Checkbox(checked = i in selected, onCheckedChange = null)
                        } else {
                            RadioButton(selected = i in selected, onClick = null)
                        }
                        Text(label, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.sorted().toIntArray()) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
