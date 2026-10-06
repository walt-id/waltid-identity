package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/** A restrained directional cue for hierarchy; the navigator owns cancellation and interactive Back. */
internal fun <S> AnimatedContentTransitionScope<S>.walletNavigationMotion(
    forward: Boolean, reduceMotion: Boolean, rightToLeft: Boolean,
): ContentTransform {
    if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val direction = if (forward != rightToLeft) AnimatedContentTransitionScope.SlideDirection.Left
        else AnimatedContentTransitionScope.SlideDirection.Right
    return (slideIntoContainer(direction, tween(220), initialOffset = { it / 5 }) + fadeIn(tween(140))) togetherWith
        (slideOutOfContainer(direction, tween(220), targetOffset = { it / 5 }) + fadeOut(tween(140)))
}
