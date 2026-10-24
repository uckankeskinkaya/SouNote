package com.sounote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
    com.sounote.app.ink.PenType.GEL -> R.drawable.pen_gel
    com.sounote.app.ink.PenType.NEON -> R.drawable.pen_neon
    com.sounote.app.ink.PenType.DOTTED -> R.drawable.pen_dotted
    com.sounote.app.ink.PenType.BROAD -> R.drawable.pen_broad
    com.sounote.app.ink.PenType.SUMI -> R.drawable.pen_sumi
}

/** Defter kapakları (Canva). Seçim belge başına tercihlerde: "cover_<belge kimliği>" (0 = kapak yok, ilk sayfa görünür). */
object Covers {
    val ART = listOf(
        R.drawable.cover_1, R.drawable.cover_2, R.drawable.cover_3,
        R.drawable.cover_4, R.drawable.cover_5, R.drawable.cover_6,
    )

    /** Kapak değişince kartlar yeniden çizilsin. */
    private val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun get(context: android.content.Context, id: String): Int {
        version.intValue
        return context.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE).getInt("cover_$id", 0)
            .takeIf { it in 0..ART.size } ?: 0
    }

    fun set(context: android.content.Context, id: String, cover: Int) {
        context.getSharedPreferences("viewer", android.content.Context.MODE_PRIVATE).edit().putInt("cover_$id", cover).apply()
        version.intValue++
    }
}

/** Kapak resmi; etiket alanına belgenin adı yazılır. */
@Composable
internal fun CoverArt(cover: Int, title: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        Image(
            painterResource(Covers.ART[cover - 1]),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Kapaktaki beyaz etiket: üstten ~%10, genişliğin ~%50'si
        androidx.compose.material3.Text(
            title,
            color = Color(0xFF1A1C2E),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.115f)
                .size(width = maxWidth * 0.46f, height = maxHeight * 0.1f),
        )
    }
}

/** Kapak seçici: "Kapak yok" + hazır kapaklar. */
@Composable
internal fun CoverPicker(selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        items(Covers.ART.size + 1) { i ->
            val sel = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 52.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier,
                    )
                    .clickable { onSelect(i) },
            ) {
                if (i == 0) {
                    Icon(SouIcons.Description, contentDescription = "Kapak yok", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Image(
                        painterResource(Covers.ART[i - 1]),
                        contentDescription = "Kapak $i",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
