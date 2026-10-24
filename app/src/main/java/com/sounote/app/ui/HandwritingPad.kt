package com.sounote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.sounote.app.ErrorLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kalemle yazma paneli: yazılan el yazısı (ML Kit, Türkçe) yazıya çevrilip nota eklenir.
 * İlk kullanımda Türkçe tanıma modeli (~20 MB) bir kez indirilir; sonra internetsiz çalışır.
 */
@Composable
internal fun HandwritingPad(onText: (String) -> Unit, onBackspace: () -> Unit, onNewLine: () -> Unit, onClose: () -> Unit) {
    var status by remember { mutableStateOf("Hazırlanıyor…") }
    var ready by remember { mutableStateOf(false) }
    val recognizer = remember { mutableStateOf<DigitalInkRecognizer?>(null) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    val ink = remember { mutableStateOf(Ink.builder()) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        val id = runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag("tr") }.getOrNull()
        if (id == null) {
            status = "Bu cihazda Türkçe el yazısı tanıma yok"
        } else {
            val model = DigitalInkRecognitionModel.builder(id).build()
            val manager = RemoteModelManager.getInstance()
            fun open() {
                recognizer.value = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
                ready = true
                status = "Yaz, bir an bekle: yazıya dönüşür"
            }
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) {
                    open()
                } else {
                    status = "Türkçe model indiriliyor (bir kez, ~20 MB)…"
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener { open() }
                        .addOnFailureListener {
                            ErrorLog.record("El yazısı modeli indirilemedi", it)
                            status = "Model indirilemedi: internet bağlantısını kontrol et"
                        }
                }
            }.addOnFailureListener { status = "Tanıma başlatılamadı" }
        }
        onDispose { recognizer.value?.close() }
    }

    fun recognizeNow() {
        val r = recognizer.value ?: return
        if (strokes.isEmpty()) return
        val built = ink.value.build()
        r.recognize(built).addOnSuccessListener { result ->
            val text = result.candidates.firstOrNull()?.text.orEmpty()
            if (text.isNotBlank()) onText(text)
            strokes.clear()
            ink.value = Ink.builder()
        }.addOnFailureListener { ErrorLog.record("El yazısı tanınamadı", it) }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kalemle yaz", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onText(" ") }) { Text("␣ Boşluk") }
            TextButton(onClick = onNewLine) { Text("↵") }
            TextButton(onClick = onBackspace) { Text("⌫") }
            TextButton(onClick = onClose) { Text("Kapat") }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .pointerInput(ready) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pending?.cancel()
                        val stroke = Ink.Stroke.builder()
                        stroke.addPoint(Ink.Point.create(down.position.x, down.position.y, System.currentTimeMillis()))
                        current.add(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            stroke.addPoint(Ink.Point.create(change.position.x, change.position.y, System.currentTimeMillis()))
                            current.add(change.position)
                            change.consume()
                        }
                        ink.value.addStroke(stroke.build())
                        strokes.add(current.toList())
                        current.clear()
                        // Kalem kalktıktan kısa süre sonra tanı (yeni çizgi başlarsa bekle).
                        pending = scope.launch {
                            delay(900)
                            recognizeNow()
                        }
                    }
                },
        ) {
            val base = size.height * 0.72f
            drawLine(Color(0xFFDCE4F7), Offset(16f, base), Offset(size.width - 16f, base), strokeWidth = 2f)
            val style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            (strokes + listOf(current.toList())).forEach { pts ->
                if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    }
                    drawPath(path, Color(0xFF1E3A8A), style = style)
                }
            }
        }
    }
}
