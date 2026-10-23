package com.sounote.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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

/** Animasyonlar: basınca hafif küçülme, sırayla beliren kartlar, ekran geçişleri. */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 280

    /** Ana ekranlar arası (sekme) geçiş: yöne göre hafif kayma + solma. */
    fun <S> tabTransition(forward: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        val dir = if (forward) 1 else -1
        (fadeIn(tween(MEDIUM, delayMillis = 60)) + slideInHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { dir * it / 12 })
            .togetherWith(fadeOut(tween(SHORT)) + slideOutHorizontally(tween(MEDIUM)) { -dir * it / 16 })
    }

    /** Belge / not açılıp kapanırken: büyüyerek gelir, küçülerek gider. */
    fun <S> screenTransition(opening: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        if (opening) {
            (fadeIn(tween(MEDIUM)) + scaleIn(tween(MEDIUM, easing = FastOutSlowInEasing), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(SHORT)))
        } else {
            fadeIn(tween(MEDIUM)).togetherWith(fadeOut(tween(SHORT)) + scaleOut(tween(SHORT), targetScale = 0.96f))
        }
    }
}

/** Basılı tutarken yaylı küçülme. Tıklamayı yutmaz, mevcut clickable ile birlikte çalışır. */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
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
