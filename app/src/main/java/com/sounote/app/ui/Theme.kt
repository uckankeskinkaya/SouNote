package com.sounote.app.ui

import android.app.ActivityManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

/*
 * SouNote 2.0 tasarım sistemi: renk paletleri, vurgu rengi, tipografi, köşe ve boşluk token'ları.
 * Sabit tema: sistemin dinamik renkleri (HyperOS) bazı yüzeyleri düşük kontrastlı yapıyordu.
 * PDF sayfaları ve notların renkleri temadan etkilenmez (sayfa her zaman kendi rengiyle çizilir).
 */

// ---- Yüzey paletleri ----

/** Koyu temanın yüzeyleri. Açık tema her zaman "SouNote Light". */
enum class DarkPalette(val label: String, val description: String, val preview: Long) {
    OBSIDIAN("Obsidian Purple", "Obsidyen yüzeyler, mor ve indigo vurgular", 0xFF13121C),
    MIDNIGHT("Midnight Blue", "Gece mavisi, elektrik mavisi ve camgöbeği", 0xFF0E1527),
    GRAPHITE("Grafit", "Nötr koyu gri (1.0 görünümü)", 0xFF16181C),
}

private val SouNoteLight = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF17126B),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E4F2),
    onSecondaryContainer = Color(0xFF1B1D2E),
    tertiary = Color(0xFF00687A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFEFF5),
    onTertiaryContainer = Color(0xFF00313A),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFFBFBFE),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE4E4EE),
    onSurfaceVariant = Color(0xFF4A4B5A),
    surfaceTint = Color(0xFF4F46E5),
    outline = Color(0xFF777889),
    outlineVariant = Color(0xFFCDCED9),
    surfaceBright = Color(0xFFFBFBFE),
    surfaceDim = Color(0xFFDCDCE4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F8),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E7EF),
    surfaceContainerHighest = Color(0xFFE1E1EA),
)

private val Obsidian = darkColorScheme(
    primary = Color(0xFFC3B5FF),
    onPrimary = Color(0xFF2D1A73),
    primaryContainer = Color(0xFF46339E),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFFC9C2E6),
    onSecondary = Color(0xFF302A49),
    secondaryContainer = Color(0xFF34304A),
    onSecondaryContainer = Color(0xFFE4DEFA),
    tertiary = Color(0xFF8FD4FF),
    onTertiary = Color(0xFF003549),
    tertiaryContainer = Color(0xFF1D3F55),
    onTertiaryContainer = Color(0xFFCDEBFF),
    background = Color(0xFF0E0D16),
    onBackground = Color(0xFFECEAF5),
    surface = Color(0xFF13121C),
    onSurface = Color(0xFFECEAF5),
    surfaceVariant = Color(0xFF2A2836),
    onSurfaceVariant = Color(0xFFC6C2D7),
    surfaceTint = Color(0xFFC3B5FF),
    outline = Color(0xFF8E89A3),
    outlineVariant = Color(0xFF3E3B4D),
    surfaceBright = Color(0xFF393647),
    surfaceDim = Color(0xFF0E0D16),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF16151F),
    surfaceContainer = Color(0xFF1B1A26),
    surfaceContainerHigh = Color(0xFF24222F),
    surfaceContainerHighest = Color(0xFF2D2B3A),
)

private val Midnight = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF0F4C99),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFFBAC6E0),
    onSecondary = Color(0xFF243048),
    secondaryContainer = Color(0xFF2A3854),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFF5FE1E6),
    onTertiary = Color(0xFF00373A),
    tertiaryContainer = Color(0xFF004F55),
    onTertiaryContainer = Color(0xFFA8F4F7),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE6ECF8),
    surface = Color(0xFF0E1527),
    onSurface = Color(0xFFE6ECF8),
    surfaceVariant = Color(0xFF22304A),
    onSurfaceVariant = Color(0xFFB9C5DB),
    surfaceTint = Color(0xFF7AB8FF),
    outline = Color(0xFF8090AD),
    outlineVariant = Color(0xFF34435F),
    surfaceBright = Color(0xFF2E3B57),
    surfaceDim = Color(0xFF0A1020),
    surfaceContainerLowest = Color(0xFF070C18),
    surfaceContainerLow = Color(0xFF111A2E),
    surfaceContainer = Color(0xFF152036),
    surfaceContainerHigh = Color(0xFF1C2942),
    surfaceContainerHighest = Color(0xFF24334F),
)

private val Graphite = darkColorScheme(
    primary = Color(0xFFA9BEFF),
    onPrimary = Color(0xFF0B2A73),
    primaryContainer = Color(0xFF23439F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBCC6DC),
    onSecondary = Color(0xFF263041),
    secondaryContainer = Color(0xFF3A4252),
    onSecondaryContainer = Color(0xFFDCE2F0),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF5E3A10),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFC2C6CF),
    surfaceTint = Color(0xFFA9BEFF),
    outline = Color(0xFF8D929C),
    outlineVariant = Color(0xFF50545E),
    surfaceBright = Color(0xFF3A3D44),
    surfaceDim = Color(0xFF111316),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = Color(0xFF1F2125),
    surfaceContainerHigh = Color(0xFF292B30),
    surfaceContainerHighest = Color(0xFF34363B),
)

// ---- Vurgu rengi ----

/** Bir vurgu renginin açık ve koyu temadaki tonları. */
@Immutable
data class AccentTones(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

/** Hazır vurgu renkleri (yüzeyler paletten gelir, sadece vurgu değişir). Ad tercihlerde saklanır, değiştirme. */
enum class ColorTheme(
    val label: String,
    val lightPrimary: Long, val lightContainer: Long, val lightOnContainer: Long,
    val darkPrimary: Long, val darkOnPrimary: Long, val darkContainer: Long, val darkOnContainer: Long,
) {
    INDIGO("İndigo", 0xFF4F46E5, 0xFFE2E0FF, 0xFF17126B, 0xFFC0BBFF, 0xFF241A7A, 0xFF3A31B5, 0xFFE2E0FF),
    BLUE("Mavi", 0xFF2F5BD3, 0xFFDCE4FF, 0xFF0A2470, 0xFFA9BEFF, 0xFF0B2A73, 0xFF23439F, 0xFFDCE4FF),
    CYAN("Camgöbeği", 0xFF00728A, 0xFFC4ECF5, 0xFF00303B, 0xFF6FD6EE, 0xFF003641, 0xFF00566A, 0xFFC4ECF5),
    GREEN("Yeşil", 0xFF2E7D4F, 0xFFCDEFD8, 0xFF0B3A1F, 0xFF8FD9A8, 0xFF0A3A1E, 0xFF1E5C39, 0xFFCDEFD8),
    TEAL("Turkuaz", 0xFF00796B, 0xFFC4EEE8, 0xFF00332D, 0xFF7FD6CB, 0xFF00382F, 0xFF005048, 0xFFC4EEE8),
    PURPLE("Mor", 0xFF7048C8, 0xFFE8DDFF, 0xFF2A0F66, 0xFFCDB8FF, 0xFF3A1580, 0xFF5531A8, 0xFFE8DDFF),
    PINK("Pembe", 0xFFC2357A, 0xFFFFD8E8, 0xFF4D0A2C, 0xFFFFAFCF, 0xFF5E0F37, 0xFF8F2358, 0xFFFFD8E8),
    RED("Kırmızı", 0xFFC62828, 0xFFFFDAD6, 0xFF410002, 0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6),
    ORANGE("Turuncu", 0xFFC2570C, 0xFFFFDCC6, 0xFF4A1A00, 0xFFFFB68A, 0xFF5A2200, 0xFF8A3F08, 0xFFFFDCC6),
    GRAPHITE("Grafit", 0xFF3C4350, 0xFFDDE2EA, 0xFF141B26, 0xFFC3CAD6, 0xFF2A313D, 0xFF444C5A, 0xFFDDE2EA),
    ;

    val swatch get() = Color(lightPrimary)
    val tones
        get() = AccentTones(
            Color(lightPrimary), Color(lightContainer), Color(lightOnContainer),
            Color(darkPrimary), Color(darkOnPrimary), Color(darkContainer), Color(darkOnContainer),
        )
}

/** Kullanıcının vurgu rengi seçimi. */
sealed interface Accent {
    /** Temanın kendi vurgusu (SouNote Light: indigo, Obsidian: mor, Midnight: elektrik mavisi). */
    data object Auto : Accent
    data class Preset(val theme: ColorTheme) : Accent
    data class Custom(val argb: Int) : Accent

    val label: String
        get() = when (this) {
            Auto -> "Tema rengi"
            is Preset -> theme.label
            is Custom -> "Özel renk"
        }
}

/** Seçilen herhangi bir renkten açık/koyu tonları türetir (HSL açıklığı ayarlanarak). */
fun customTones(argb: Int): AccentTones {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.35f, 0.9f)
    fun tone(l: Float, sat: Float = s) = Color(ColorUtils.HSLToColor(floatArrayOf(h, sat, l)))
    return AccentTones(
        lightPrimary = tone(0.42f),
        lightContainer = tone(0.9f, s * 0.8f),
        lightOnContainer = tone(0.16f),
        darkPrimary = tone(0.78f),
        darkOnPrimary = tone(0.2f),
        darkContainer = tone(0.32f),
        darkOnContainer = tone(0.9f, s * 0.8f),
    )
}

private fun ColorScheme.withTones(t: AccentTones, dark: Boolean): ColorScheme =
    if (dark) {
        copy(
            primary = t.darkPrimary, onPrimary = t.darkOnPrimary,
            primaryContainer = t.darkContainer, onPrimaryContainer = t.darkOnContainer, surfaceTint = t.darkPrimary,
        )
    } else {
        copy(
            primary = t.lightPrimary, onPrimary = Color.White,
            primaryContainer = t.lightContainer, onPrimaryContainer = t.lightOnContainer, surfaceTint = t.lightPrimary,
        )
    }

/** Paletin ve vurgu rengin birleşimi. */
fun colorSchemeFor(dark: Boolean, palette: DarkPalette, accent: Accent): ColorScheme {
    val base = if (!dark) SouNoteLight else when (palette) {
        DarkPalette.OBSIDIAN -> Obsidian
        DarkPalette.MIDNIGHT -> Midnight
        DarkPalette.GRAPHITE -> Graphite
    }
    return when (accent) {
        Accent.Auto -> base
        is Accent.Preset -> base.withTones(accent.theme.tones, dark)
        is Accent.Custom -> base.withTones(customTones(accent.argb), dark)
    }
}

// ---- Ek renk token'ları ----

/** Material'de olmayan, uygulamaya özgü renkler. */
@Immutable
data class SouColors(
    val isDark: Boolean,
    /** Karşılama kartı degradesi (üstündeki yazı beyaz). */
    val hero: List<Color>,
    /** Koyu temada çok hafif aurora parıltısı. */
    val aurora: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val star: Color,
    val cardBorder: Color,
)

val LocalSouColors = staticCompositionLocalOf {
    SouColors(
        false, listOf(Color(0xFF4F46E5), Color(0xFF17126B)), Color.Transparent,
        Color(0xFF2E7D32), Color(0xFFE0893A), Color(0xFFD64545), Color(0xFFF2B705), Color(0x33000000),
    )
}

private fun souColors(scheme: ColorScheme, dark: Boolean, palette: DarkPalette): SouColors {
    val hero = if (dark) {
        listOf(scheme.primaryContainer, scheme.surfaceContainerLowest)
    } else {
        listOf(scheme.primary, scheme.onPrimaryContainer)
    }
    val aurora = when {
        !dark -> Color.Transparent
        palette == DarkPalette.MIDNIGHT -> Color(0xFF5FE1E6)
        palette == DarkPalette.OBSIDIAN -> Color(0xFF8F7BFF)
        else -> Color.Transparent
    }
    return SouColors(
        isDark = dark,
        hero = hero,
        aurora = aurora,
        success = if (dark) Color(0xFF7FD69A) else Color(0xFF2E7D32),
        warning = if (dark) Color(0xFFFFB86B) else Color(0xFFC2570C),
        danger = if (dark) Color(0xFFFF8A80) else Color(0xFFD64545),
        star = Color(0xFFF2B705),
        cardBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.55f else 0.7f),
    )
}

// ---- Tipografi ----

/*
 * Sistem yazı tipi (Türkçe karakterlerin hepsi var, her cihazda okunaklı ve hızlı). Başlıklar kalın, gövde normal.
 */
private val SouTypography: Typography = Typography().let { t ->
    fun TextStyle.w(weight: FontWeight, spacing: Float = 0f) = copy(fontWeight = weight, letterSpacing = spacing.sp)
    t.copy(
        displayLarge = t.displayLarge.w(FontWeight.Bold, -0.5f),
        displayMedium = t.displayMedium.w(FontWeight.Bold, -0.25f),
        displaySmall = t.displaySmall.w(FontWeight.Bold),
        headlineLarge = t.headlineLarge.w(FontWeight.Bold, -0.25f),
        headlineMedium = t.headlineMedium.w(FontWeight.Bold),
        headlineSmall = t.headlineSmall.w(FontWeight.Bold),
        titleLarge = t.titleLarge.w(FontWeight.Bold),
        titleMedium = t.titleMedium.w(FontWeight.SemiBold, 0.1f),
        titleSmall = t.titleSmall.w(FontWeight.SemiBold, 0.1f),
        labelLarge = t.labelLarge.w(FontWeight.SemiBold, 0.1f),
        labelMedium = t.labelMedium.w(FontWeight.Medium, 0.3f),
        labelSmall = t.labelSmall.w(FontWeight.Medium, 0.3f),
    )
}

private val SouShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---- Boşluk, yoğunluk ve hareket ----

/** Arayüz yoğunluğu: kompakt düzende boşluklar küçülür. */
enum class UiDensity(val label: String, val factor: Float) {
    COMFORTABLE("Rahat", 1f),
    COMPACT("Kompakt", 0.72f),
}

/** Animasyon yoğunluğu. Sistemde animasyonlar kapalıysa her zaman [OFF]. */
enum class MotionLevel(val label: String) {
    FULL("Tam"),
    REDUCED("Azaltılmış"),
    OFF("Kapalı"),
}

/** Boşluk token'ları (yoğunluğa göre ölçeklenir). */
@Immutable
data class Spacing(val factor: Float) {
    val xs: Dp get() = (4 * factor).dp
    val sm: Dp get() = (8 * factor).dp
    val md: Dp get() = (12 * factor).dp
    val lg: Dp get() = (16 * factor).dp
    val xl: Dp get() = (20 * factor).dp
    val xxl: Dp get() = (28 * factor).dp

    /** Ana sayfa bölümleri arası. */
    val section: Dp get() = (26 * factor).dp

    /** Kart iç boşluğu. */
    val card: Dp get() = (18 * factor).dp
}

val LocalSpacing = staticCompositionLocalOf { Spacing(1f) }
val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.FULL }

/** Kullanıcı tercihi + sistemin "animasyonları kaldır" ayarı + düşük bellekli cihaz. */
@Composable
private fun effectiveMotion(user: MotionLevel): MotionLevel {
    val context = LocalContext.current
    val system = remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        when {
            scale == 0f -> MotionLevel.OFF
            lowRam -> MotionLevel.REDUCED
            else -> MotionLevel.FULL
        }
    }
    return if (system.ordinal > user.ordinal) system else user
}

/** Tema değişince renkler yumuşakça geçer (animasyon kapalıysa anında). */
@Composable
private fun animated(target: ColorScheme, motion: MotionLevel): ColorScheme {
    if (motion == MotionLevel.OFF) return target
    val spec = tween<Color>(if (motion == MotionLevel.FULL) 320 else 160)
    @Composable
    fun Color.a(): Color = animateColorAsState(this, spec, label = "theme").value
    return target.copy(
        primary = target.primary.a(),
        onPrimary = target.onPrimary.a(),
        primaryContainer = target.primaryContainer.a(),
        onPrimaryContainer = target.onPrimaryContainer.a(),
        secondaryContainer = target.secondaryContainer.a(),
        background = target.background.a(),
        onBackground = target.onBackground.a(),
        surface = target.surface.a(),
        onSurface = target.onSurface.a(),
        surfaceVariant = target.surfaceVariant.a(),
        onSurfaceVariant = target.onSurfaceVariant.a(),
        outlineVariant = target.outlineVariant.a(),
        surfaceContainerLowest = target.surfaceContainerLowest.a(),
        surfaceContainerLow = target.surfaceContainerLow.a(),
        surfaceContainer = target.surfaceContainer.a(),
        surfaceContainerHigh = target.surfaceContainerHigh.a(),
        surfaceContainerHighest = target.surfaceContainerHighest.a(),
    )
}

/** [dark] null ise sistem ayarı izlenir. */
@Composable
fun SouNoteTheme(
    dark: Boolean? = null,
    palette: DarkPalette = DarkPalette.OBSIDIAN,
    accent: Accent = Accent.Auto,
    density: UiDensity = UiDensity.COMFORTABLE,
    motion: MotionLevel = MotionLevel.FULL,
    content: @Composable () -> Unit,
) {
    val isDark = dark ?: isSystemInDarkTheme()
    val target = remember(isDark, palette, accent) { colorSchemeFor(isDark, palette, accent) }
    val level = effectiveMotion(motion)
    val scheme = animated(target, level)
    val sou = remember(target, isDark, palette) { souColors(target, isDark, palette) }
    CompositionLocalProvider(
        LocalSouColors provides sou,
        LocalSpacing provides Spacing(density.factor),
        LocalMotionLevel provides level,
    ) {
        MaterialTheme(colorScheme = scheme, typography = SouTypography, shapes = SouShapes) {
            // Renk verilmemiş yazılar temanın yazı rengini alsın (Surface/Scaffold dışında varsayılan siyahtı:
            // tablet düzeninde koyu temada başlıklar okunmuyordu).
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
