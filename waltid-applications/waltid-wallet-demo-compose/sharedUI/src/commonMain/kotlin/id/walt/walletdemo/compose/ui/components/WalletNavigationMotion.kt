package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/** A restrained directional cue for hierarchy; the navigator owns cancellation and interactive Back. */
internal fun <S> AnimatedContentTransitionScope<S>.walletNavigationMotion(
    forward: Boolean, reduceMotion: Boolean, rightToLeft: Boolean,
): ContentTransform {
    if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val direction = if (forward != rightToLeft) AnimatedContentTransitionScope.SlideDirection.Left
        else AnimatedContentTransitionScope.SlideDirection.Right
    // The new page travels from the edge; its parent moves a quarter-width behind it.
    // Back reverses that relationship instead of fading two barely moving pages together.
    val timing = tween<IntOffset>(360, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    return (slideIntoContainer(direction, timing, initialOffset = { if (forward) it else it / 4 }) togetherWith
        slideOutOfContainer(direction, timing, targetOffset = { if (forward) it / 4 else it }))
        .apply { targetContentZIndex = if (forward) 1f else -1f }
}
