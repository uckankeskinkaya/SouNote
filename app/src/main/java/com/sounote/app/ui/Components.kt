@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ortak bileşenler: aynı işi gören her şey her ekranda aynı görünsün.
 * Kart, bölüm başlığı, arama kutusu, boş/yükleniyor/hata durumları, ayar satırları, alt sayfa (bottom sheet).
 */

/** Kartların zemini: açık temada beyaz, koyu temada biraz açık yüzey. */
@Composable
internal fun cardBackground(): Color =
    if (LocalSouColors.current.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest

/** Standart kart: yumuşak köşe, ince kenarlık, gölge yok (koyu temada da okunaklı). */
@Composable
internal fun Modifier.card(radius: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(cardBackground())
        .border(1.dp, LocalSouColors.current.cardBorder, shape)
}

/** Bölüm başlığı: başlık, isteğe bağlı açıklama ve sağda "Tümü →" gibi bir eylem. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text("$action →") }
    }
}

/** Hap biçimli arama kutusu (yazılabilir). */
@Composable
internal fun SouSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 6.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Temizle") }
        }
        trailing?.invoke(this)
    }
}

/** Boş durum: büyük emoji, başlık, açıklama ve eylemler. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyState(
    emoji: String,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        ) { Text(emoji, fontSize = 40.sp) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        if (actions != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) { actions() }
        }
    }
}

/** Yükleniyor göstergesi (kısa açıklamayla). */
@Composable
internal fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

enum class BannerKind { INFO, SUCCESS, ERROR }

/** Bilgi / başarı / hata şeridi. */
@Composable
internal fun Banner(
    text: String,
    kind: BannerKind = BannerKind.INFO,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val sou = LocalSouColors.current
    val (bg, fg, emoji) = when (kind) {
        BannerKind.INFO -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "ℹ️")
        BannerKind.SUCCESS -> Triple(sou.success.copy(alpha = 0.16f), MaterialTheme.colorScheme.onSurface, "✅")
        BannerKind.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "⚠️")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Renkli köşeli zemin üstünde emoji (hızlı eylemler, ayar satırları, ders kartları). */
@Composable
internal fun EmojiTile(emoji: String, color: Color, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.16f)),
    ) { Text(emoji, fontSize = (size.value * 0.48f).sp) }
}

/** Favori yıldızı (dokununca ekler/çıkarır). */
@Composable
internal fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (favorite) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = if (favorite) "Favorilerden çıkar" else "Favorilere ekle",
            tint = if (favorite) LocalSouColors.current.star else LocalContentColor.current,
        )
    }
}

/** Yuvarlak ilerleme göstergesi (yüzdesi ortada). */
@Composable
internal fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    stroke: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    showLabel: Boolean = true,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animate = LocalMotionLevel.current != MotionLevel.OFF
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (animate) 600 else 0), label = "ring")
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val s = Size(this.size.width - w, this.size.height - w)
            val o = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, o, s, style = Stroke(w))
            drawArc(color, -90f, 360f * p, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (showLabel) {
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/** Küçük istatistik kutusu: değer büyük, açıklama küçük. */
@Composable
internal fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.card(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Ekran üst çubuğu: arka planla aynı renkte, kaydırınca hafif yükselir. */
@Composable
internal fun SouTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

// ---- Ayar satırları (Ayarlar ve diğer listeler) ----

@Composable
internal fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().card(), content = content)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 68.dp))
}

@Composable
internal fun SettingRow(
    emoji: String,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        ) { Text(emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SwitchRow(emoji: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(emoji, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- Alt sayfa (bottom sheet): telefonda bağlamsal menüler ----

/** Bağlamsal işlemler için alt sayfa. */
@Composable
internal fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
internal fun SheetItem(emoji: String, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(emoji, fontSize = 18.sp, modifier = Modifier.padding(end = 16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** İnce yatay ilerleme çubuğu (yuvarlak uçlu). */
@Composable
internal fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp,
) {
    val animate = LocalMotionLevel.current != MotionLevel.OFF
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (animate) 600 else 0), label = "bar")
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}
