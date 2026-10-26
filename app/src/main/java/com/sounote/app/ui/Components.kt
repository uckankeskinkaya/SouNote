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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Kartların zemini (düz renk gereken yerler için): açık temada beyaz, koyu temada lacivert-mavi yüzey. */
@Composable
internal fun cardBackground(): Color =
    if (LocalSouColors.current.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest

/**
 * Standart kart, logodaki cam kutucuk gibi: koyu temada yarı saydam lacivert-mavi, sol üstten ışıyan ince kenar;
 * açık temada beyaz, mavi ışıltılı kenar.
 */
@Composable
internal fun Modifier.card(radius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val dark = LocalSouColors.current.isDark
    val fill = if (dark) {
        Brush.linearGradient(listOf(Color(0xCC0E2068), Color(0xB3071240)))
    } else {
        Brush.linearGradient(listOf(Color.White, Color(0xFFF4F7FF)))
    }
    val edge = if (dark) {
        Brush.linearGradient(listOf(Color(0x995C86FF), Color(0x262A4BB0), Color(0x4D3D6BFF)))
    } else {
        Brush.linearGradient(listOf(Color(0x997F9CFF), Color(0x33B8C8F5), Color(0x664F7BFF)))
    }
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
}

/** Bölüm başlığı: başlık ve sağda "Tümü" gibi bir eylem. */
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
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
        Icon(SouIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            IconButton(onClick = { onValueChange("") }) { Icon(SouIcons.Close, contentDescription = "Temizle") }
        }
        trailing?.invoke(this)
    }
}

/** Boş durum: simge, başlık, (varsa) tek satır açıklama ve eylemler. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String? = null,
    modifier: Modifier = Modifier,
    /** Simge yerine çizim (res/drawable-nodpi empty_*). */
    @androidx.annotation.DrawableRes art: Int? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        if (art != null) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(art),
                contentDescription = null,
                modifier = Modifier.size(150.dp),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
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
    val (bg, fg, icon) = when (kind) {
        BannerKind.INFO -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, SouIcons.Info)
        BannerKind.SUCCESS -> Triple(sou.success.copy(alpha = 0.16f), MaterialTheme.colorScheme.onSurface, SouIcons.CheckCircle)
        BannerKind.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, SouIcons.Info)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Renkli köşeli zemin üstünde simge (hızlı eylemler, listeler). */
@Composable
internal fun IconTile(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.14f)),
    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f)) }
}

/** Favori yıldızı (dokununca ekler/çıkarır). */
@Composable
internal fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (favorite) SouIcons.StarFilled else SouIcons.Star,
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
internal fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.card(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
            containerColor = Color.Transparent,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().card(), content = content)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 56.dp))
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SwitchRow(icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) {
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
internal fun SheetItem(icon: ImageVector, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp).size(22.dp),
        )
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

/**
 * Uygulama zemini: logodaki gibi koyu lacivert üstünde mavi ve mor ışık parıltısı (açık temada çok hafif).
 * Renkler temadan gelir; ekran içerikleri bunun üstünde saydam zeminle çizilir.
 */
@Composable
internal fun Modifier.souBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalSouColors.current.isDark
    // Logodaki gibi: lacivert zemin üstünde parlayan elektrik mavisi ışıklar
    val top = if (dark) Color(0xFF041040) else Color(0xFFE2EAFF)
    val bottom = if (dark) scheme.background else Color(0xFFF1F5FF)
    val glow1 = if (dark) Color(0x8C1F4FFF) else Color(0x332A5BFF)
    val glow2 = if (dark) Color(0x593D8BFF) else Color(0x3386B4FF)
    val glow3 = if (dark) Color(0x266E8CFF) else Color(0x1A5482FF)
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val r1 = size.maxDimension * 0.8f
        val c1 = Offset(size.width * 0.92f, size.height * 0.0f)
        drawCircle(Brush.radialGradient(listOf(glow1, Color.Transparent), center = c1, radius = r1), r1, c1)
        val r2 = size.maxDimension * 0.65f
        val c2 = Offset(size.width * 0.0f, size.height * 1.0f)
        drawCircle(Brush.radialGradient(listOf(glow2, Color.Transparent), center = c2, radius = r2), r2, c2)
        val r3 = size.maxDimension * 0.45f
        val c3 = Offset(size.width * 0.25f, size.height * 0.4f)
        drawCircle(Brush.radialGradient(listOf(glow3, Color.Transparent), center = c3, radius = r3), r3, c3)
    }
}
