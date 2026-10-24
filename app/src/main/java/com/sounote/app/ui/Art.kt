package com.sounote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sounote.app.R
import com.sounote.app.data.Course
import com.sounote.app.data.Exam

/*
 * Görseller: ders ve kalem çizimleri (Canva'da üretildi, res/drawable-nodpi), içerik türlerinin simgeleri.
 */

/** Dersin çizimi (katalog kimliğine ya da adına göre); bilinmeyen derste null. */
internal fun courseArt(course: Course): Int? {
    val key = (course.id + " " + course.name).lowercase()
    return when {
        "grammar" in key -> R.drawable.course_grammar
        "vocab" in key -> R.drawable.course_vocabulary
        "reading" in key -> R.drawable.course_reading
        "listening" in key -> R.drawable.course_listening
        "writing" in key -> R.drawable.course_writing
        "speaking" in key -> R.drawable.course_speaking
        "unit" in key || "ünite" in key -> R.drawable.course_unit
        "exam" in key || "sınav" in key -> R.drawable.course_exam
        else -> null
    }
}

/** Ders görseli kutucuğu: çizim varsa çizim, yoksa ders rengiyle kitap simgesi. */
@Composable
internal fun CourseArt(course: Course, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val color = Color(course.color)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color.copy(alpha = 0.12f)),
    ) {
        val art = courseArt(course)
        if (art != null) {
            Image(painterResource(art), contentDescription = null, modifier = Modifier.padding(size * 0.14f))
        } else {
            Icon(SouIcons.MenuBook, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Sınav türünün simgesi (sınav, ödev, sunum). */
internal val Exam.kindIcon: ImageVector
    get() = when (kind) {
        "task" -> SouIcons.Assignment
        "presentation" -> SouIcons.CoPresent
        else -> SouIcons.Event
    }

/** Küçük, renkli simge rozeti (liste satırlarının başında). */
@Composable
internal fun IconBadge(icon: ImageVector, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.12f)),
    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.56f)) }
}

/** Kalem türünün çizimi (dikey, uç aşağıda). */
internal fun penArt(type: com.sounote.app.ink.PenType): Int = when (type) {
    com.sounote.app.ink.PenType.FOUNTAIN -> R.drawable.pen_fountain
    com.sounote.app.ink.PenType.BALLPOINT -> R.drawable.pen_ballpoint
    com.sounote.app.ink.PenType.FINELINER -> R.drawable.pen_fineliner
    com.sounote.app.ink.PenType.MARKER -> R.drawable.pen_marker
    com.sounote.app.ink.PenType.PENCIL -> R.drawable.pen_pencil
    com.sounote.app.ink.PenType.PASTEL -> R.drawable.pen_pastel
    com.sounote.app.ink.PenType.BRUSH -> R.drawable.pen_brush
    com.sounote.app.ink.PenType.CALLIGRAPHY -> R.drawable.pen_calligraphy
    com.sounote.app.ink.PenType.DASHED -> R.drawable.pen_fineliner
}
