package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
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
    // Both pages travel the full width, matching native iOS. A partial exit leaves
    // transparent content, such as the PIN heading, underneath the incoming text.
    val timing = WalletMotion.navigation<IntOffset>()
    return (slideIntoContainer(direction, timing) togetherWith slideOutOfContainer(direction, timing))
        .apply { targetContentZIndex = if (forward) 1f else -1f }
}
