package com.sounote.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hareket sistemi: ekranın amacına göre. Ana sayfa ve listelerde hafif geçişler; belge ve çizim
 * ekranında animasyon yok. [MotionLevel] (Ayarlar → Arayüz ve sistemin animasyon ayarı) her yerde uyulur.
 */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 280

    private fun none(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None

    /** Ana ekranlar arası (sekme) geçiş: yöne göre hafif kayma + solma. */
    fun <S> tabTransition(forward: Boolean, level: MotionLevel = MotionLevel.FULL): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        when (level) {
            MotionLevel.OFF -> none()
            MotionLevel.REDUCED -> fadeIn(tween(SHORT)) togetherWith fadeOut(tween(120))
            MotionLevel.FULL -> {
                val dir = if (forward) 1 else -1
                (fadeIn(tween(MEDIUM, delayMillis = 60)) + slideInHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { dir * it / 14 })
                    .togetherWith(fadeOut(tween(SHORT)) + slideOutHorizontally(tween(MEDIUM)) { -dir * it / 18 })
            }
        }
    }

    /** Belge / not açılıp kapanırken: büyüyerek gelir, küçülerek gider. */
    fun <S> screenTransition(opening: Boolean, level: MotionLevel = MotionLevel.FULL): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        when {
            level == MotionLevel.OFF -> none()
            level == MotionLevel.REDUCED -> fadeIn(tween(SHORT)) togetherWith fadeOut(tween(120))
            opening -> (fadeIn(tween(MEDIUM)) + scaleIn(tween(MEDIUM, easing = FastOutSlowInEasing), initialScale = 0.95f))
                .togetherWith(fadeOut(tween(SHORT)))
            else -> fadeIn(tween(MEDIUM)).togetherWith(fadeOut(tween(SHORT)) + scaleOut(tween(SHORT), targetScale = 0.96f))
        }
    }

    /** Görünümler arası (ör. liste ↔ takvim) basit solma. */
    fun <S> fade(level: MotionLevel): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        if (level == MotionLevel.OFF) none() else fadeIn(tween(if (level == MotionLevel.FULL) MEDIUM else SHORT)) togetherWith fadeOut(tween(SHORT))
    }
}

/** Basılı tutarken yaylı küçülme. Tıklamayı yutmaz, mevcut clickable ile birlikte çalışır. */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
    if (LocalMotionLevel.current == MotionLevel.OFF) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/** İlk görünüşte sırayla beliren öğe: [index] kadar gecikmeyle aşağıdan yukarı kayarak solar. */
fun Modifier.appear(index: Int = 0, stepMs: Int = 45): Modifier = composed {
    val level = LocalMotionLevel.current
    if (level != MotionLevel.FULL) return@composed this
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(1f) }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * stepMs).toLong())
        launch { alpha.animateTo(1f, tween(Motion.MEDIUM)) }
        offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * dy
    }
}

/** [content]'i sırayla belirme animasyonuyla sarar. */
@androidx.compose.runtime.Composable
fun Appear(index: Int, modifier: Modifier = Modifier, content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier.appear(index)) { content() }
}
