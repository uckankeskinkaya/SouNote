package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sounote.app.data.Exam

/**
 * Sınavın türü (4.0): neyle ilgili olduğu simge ve renkle gösterilir. Tür, sınavın [Exam.kind]'ından ve adından çıkarılır;
 * eşleşme yoksa genel sınav (quiz) sayılır.
 */
enum class ExamType(val label: String, private val light: Long, private val dark: Long) {
    QUIZ("Sınav", 0xFFC2410C, 0xFFFB923C),
    WRITING("Writing", 0xFFBE185D, 0xFFF472B6),
    SPEAKING("Speaking", 0xFF0369A1, 0xFF38BDF8),
    PRESENTATION("Sunum", 0xFF7C3AED, 0xFFA78BFA),
    LISTENING("Listening", 0xFFB45309, 0xFFFBBF24),
    TASK("Ödev", 0xFF15803D, 0xFF4ADE80),
    FINAL("Kur atlama", 0xFF374151, 0xFFD1D5DB),
    ;

    val icon: ImageVector
        get() = when (this) {
            QUIZ -> SouIcons.Quiz
            WRITING -> SouIcons.EditNote
            SPEAKING -> SouIcons.RecordVoiceOver
            PRESENTATION -> SouIcons.CoPresent
            LISTENING -> SouIcons.Headphones
            TASK -> SouIcons.TaskAlt
            FINAL -> SouIcons.Trophy
        }

    @Composable
    fun color(): Color = Color(if (LocalSouColors.current.isDark) dark else light)
}

fun examType(e: Exam): ExamType {
    val t = (e.title + " " + e.notes).lowercase()
    fun has(vararg w: String) = w.any { it in t }
    return when {
        has("kur atlama", "yeterlik", "proficiency", "final") -> ExamType.FINAL
        e.kind == "presentation" || has("sunum", "presentation") -> ExamType.PRESENTATION
        has("speaking", "konuşma", "sözlü", "video") -> ExamType.SPEAKING
        has("writing", "essay", "yazma", "paragraph") -> ExamType.WRITING
        has("listening", "dinleme") -> ExamType.LISTENING
        e.kind == "task" -> ExamType.TASK
        else -> ExamType.QUIZ
    }
}

/** Türün yuvarlatılmış kare simge rozeti (açık renk zemin, türün renginde simge). */
@Composable
fun ExamTypeBadge(type: ExamType, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val c = type.color()
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(c.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(type.icon, contentDescription = type.label, tint = c, modifier = Modifier.size(size * 0.55f)) }
}
