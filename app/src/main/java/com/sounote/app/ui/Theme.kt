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
 * SouNote 3 tasarım sistemi (sade: nötr yüzeyler, tek vurgu rengi, emoji yerine simgeler): renk paletleri, vurgu rengi, tipografi, köşe ve boşluk token'ları.
 * Sabit tema: sistemin dinamik renkleri (HyperOS) bazı yüzeyleri düşük kontrastlı yapıyordu.
 * PDF sayfaları ve notların renkleri temadan etkilenmez (sayfa her zaman kendi rengiyle çizilir).
 */

// ---- Temalar (logonun 6 hali) ----

/**
 * Uygulamanın teması: her biri logonun bir hali (renkleri, ışığı ve uygulama içindeki logosu).
 * Koyu temada logonun zemini ve parıltısı, açık temada logonun kâğıdı. Ad tercihlerde ("logoTheme") saklanır, değiştirme.
 */
enum class AppTheme(
    val label: String,
    val description: String,
    val logo: Int,
    /** Koyu zemin, iki ışık rengi, koyu temada vurgu / üstündeki yazı / kutu. */
    val bg: Long, val glowA: Long, val glowB: Long,
    val darkPrimary: Long, val darkOnPrimary: Long, val darkContainer: Long,
    /** Açık temada kâğıt, vurgu ve mürekkep. */
    val paper: Long, val lightPrimary: Long, val ink: Long,
    /** Karşılama kartı degradesi. */
    val heroA: Long, val heroB: Long,
) {
    ELECTRIC(
        "Elektrik", "Lacivert, parlayan elektrik mavisi", com.sounote.app.R.drawable.logo_tile,
        0xFF010518, 0xFF1F4FFF, 0xFF3D8BFF, 0xFF5482FF, 0xFFFFFFFF, 0xFF15329A,
        0xFFEAF0FF, 0xFF1E4BF0, 0xFF081133, 0xFF2A5BFF, 0xFF0A1A6E,
    ),
    SAPPHIRE(
        "Safir", "Gece mavisi cam, gümüş ışık", com.sounote.app.R.drawable.logo_sapphire,
        0xFF040A1A, 0xFF2C5BA8, 0xFFA9C6F5, 0xFF86A8E8, 0xFF06173D, 0xFF1F3F7C,
        0xFFEEF2F9, 0xFF2A52A0, 0xFF0B1630, 0xFF3A66B8, 0xFF0B1A3E,
    ),
    SILVER(
        "Gümüş", "Antrasit, beyaz ışıltı", com.sounote.app.R.drawable.logo_silver,
        0xFF0C0E12, 0xFF7F92B5, 0xFFF2F4F8, 0xFFB3C3DF, 0xFF16233D, 0xFF35435F,
        0xFFF1F2F5, 0xFF4A5F86, 0xFF14171D, 0xFF5C6F93, 0xFF1B2130,
    ),
    MONO(
        "Siyah beyaz", "Sade, tek renk", com.sounote.app.R.drawable.logo_mono,
        0xFF040404, 0xFFBDBDBD, 0xFFFFFFFF, 0xFFE8E8E8, 0xFF111111, 0xFF3A3A3A,
        0xFFF4F4F4, 0xFF1F1F1F, 0xFF0F0F0F, 0xFF4A4A4A, 0xFF0D0D0D,
    ),
    EMERALD(
        "Zümrüt", "Koyu yeşil, krem kâğıt, altın uç", com.sounote.app.R.drawable.logo_emerald,
        0xFF06120C, 0xFF2F7A52, 0xFFE0BC6A, 0xFF6CC497, 0xFF06301B, 0xFF1D5A3B,
        0xFFF6F0DF, 0xFF256E48, 0xFF0E1F16, 0xFF2E7D55, 0xFF0B2416,
    ),
    ROSE(
        "Gül", "Mürdüm, parlayan pembe", com.sounote.app.R.drawable.logo_rose,
        0xFF150A10, 0xFFFF4F8B, 0xFFFF9EC0, 0xFFFF7FA8, 0xFF4A0A24, 0xFF7A1E45,
        0xFFFFF0F5, 0xFFC2356F, 0xFF2A0E1A, 0xFFE04A85, 0xFF3A0F22,
    ),
    ;

    companion object {
        /** 3.0'daki koyu paletlerden geçiş. */
        fun fromLegacy(name: String?): AppTheme? = when (name) {
            "MIDNIGHT", "OBSIDIAN" -> ELECTRIC
            "GRAPHITE" -> SILVER
            else -> null
        }
    }
}

private fun mix(a: Long, b: Long, t: Float) = Color(ColorUtils.blendARGB(a.toInt(), b.toInt(), t))

/** Temanın koyu hali: zemin + ışık renginden kademeli yüzeyler. */
private fun darkSchemeOf(t: AppTheme): ColorScheme {
    val white = 0xFFFFFFFF
    return darkColorScheme(
        primary = Color(t.darkPrimary),
        onPrimary = Color(t.darkOnPrimary),
        primaryContainer = Color(t.darkContainer),
        onPrimaryContainer = mix(white, t.darkPrimary, 0.15f),
        secondary = mix(t.glowB, white, 0.25f),
        onSecondary = Color(t.bg),
        secondaryContainer = mix(t.bg, t.darkPrimary, 0.26f),
        onSecondaryContainer = mix(white, t.glowB, 0.12f),
        tertiary = mix(t.glowB, white, 0.15f),
        onTertiary = Color(t.bg),
        tertiaryContainer = mix(t.bg, t.glowB, 0.32f),
        onTertiaryContainer = mix(white, t.glowB, 0.1f),
        background = Color(t.bg),
        onBackground = mix(white, t.glowB, 0.08f),
        surface = mix(t.bg, t.glowA, 0.04f),
        onSurface = mix(white, t.glowB, 0.08f),
        surfaceVariant = mix(t.bg, t.glowA, 0.24f),
        onSurfaceVariant = mix(white, t.glowA, 0.35f).copy(alpha = 0.92f),
        surfaceTint = Color(t.darkPrimary),
        outline = mix(t.bg, t.glowB, 0.45f),
        outlineVariant = mix(t.bg, t.glowA, 0.26f),
        surfaceBright = mix(t.bg, t.glowA, 0.3f),
        surfaceDim = Color(t.bg),
        surfaceContainerLowest = mix(t.bg, 0xFF000000, 0.35f),
        surfaceContainerLow = mix(t.bg, t.glowA, 0.07f),
        surfaceContainer = mix(t.bg, t.glowA, 0.11f),
        surfaceContainerHigh = mix(t.bg, t.glowA, 0.16f),
        surfaceContainerHighest = mix(t.bg, t.glowA, 0.22f),
    )
}

/** Temanın açık hali: logonun kâğıdı üstünde mürekkep ve vurgu. */
private fun lightSchemeOf(t: AppTheme): ColorScheme {
    val white = 0xFFFFFFFF
    return lightColorScheme(
        primary = Color(t.lightPrimary),
        onPrimary = Color.White,
        primaryContainer = mix(t.paper, t.lightPrimary, 0.16f),
        onPrimaryContainer = mix(t.ink, t.lightPrimary, 0.3f),
        secondary = mix(t.lightPrimary, t.ink, 0.3f),
        onSecondary = Color.White,
        secondaryContainer = mix(t.paper, t.lightPrimary, 0.12f),
        onSecondaryContainer = Color(t.ink),
        tertiary = mix(t.glowB, t.ink, 0.45f),
        onTertiary = Color.White,
        tertiaryContainer = mix(t.paper, t.glowB, 0.25f),
        onTertiaryContainer = Color(t.ink),
        background = Color(t.paper),
        onBackground = Color(t.ink),
        surface = mix(t.paper, white, 0.5f),
        onSurface = Color(t.ink),
        surfaceVariant = mix(t.paper, t.lightPrimary, 0.1f),
        onSurfaceVariant = mix(t.ink, t.paper, 0.32f),
        surfaceTint = Color(t.lightPrimary),
        outline = mix(t.ink, t.paper, 0.5f),
        outlineVariant = mix(t.paper, t.lightPrimary, 0.18f),
        surfaceBright = mix(t.paper, white, 0.5f),
        surfaceDim = mix(t.paper, t.ink, 0.1f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = mix(t.paper, white, 0.4f),
        surfaceContainer = mix(t.paper, t.lightPrimary, 0.04f),
        surfaceContainerHigh = mix(t.paper, t.lightPrimary, 0.08f),
        surfaceContainerHighest = mix(t.paper, t.lightPrimary, 0.12f),
    )
}

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
    /** Temanın kendi vurgusu (logodaki renk). */
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

/** Temanın ve vurgu renginin birleşimi. */
fun colorSchemeFor(dark: Boolean, theme: AppTheme, accent: Accent): ColorScheme {
    val base = if (dark) darkSchemeOf(theme) else lightSchemeOf(theme)
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
    /** Temanın ışık renkleri (zemin parıltısı, kart kenarı) ve logosu. */
    val glowA: Color = Color(0xFF1F4FFF),
    val glowB: Color = Color(0xFF3D8BFF),
    val logo: Int = com.sounote.app.R.drawable.logo_tile,
)

val LocalSouColors = staticCompositionLocalOf {
    SouColors(
        false, listOf(Color(0xFF2A5BFF), Color(0xFF0A1A6E)), Color.Transparent,
        Color(0xFF2E7D32), Color(0xFFE0893A), Color(0xFFD64545), Color(0xFFF2B705), Color(0x33000000),
    )
}

private fun souColors(scheme: ColorScheme, dark: Boolean, theme: AppTheme): SouColors = SouColors(
    isDark = dark,
    hero = listOf(Color(theme.heroA), Color(theme.heroB)),
    aurora = Color.Transparent,
    success = if (dark) Color(0xFF7FD69A) else Color(0xFF2E7D32),
    warning = if (dark) Color(0xFFFFB86B) else Color(0xFFC2570C),
    danger = if (dark) Color(0xFFFF8A80) else Color(0xFFD64545),
    star = Color(0xFFF2B705),
    cardBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.6f else 0.55f),
    glowA = Color(theme.glowA),
    glowB = Color(theme.glowB),
    logo = theme.logo,
)

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
        headlineSmall = t.headlineSmall.w(FontWeight.SemiBold),
        titleLarge = t.titleLarge.w(FontWeight.SemiBold),
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
    palette: AppTheme = AppTheme.ELECTRIC,
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
