package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences

/** Short, reversible travel with no overlapping text; the sheet alone animates its height. */
@Composable
internal fun rememberWalletSheetNavigationMotion(): (forward: Boolean) -> ContentTransform {
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val distance = with(LocalDensity.current) { 24.dp.roundToPx() }
    return remember(reduceMotion, rightToLeft, distance) {
        { forward ->
            val offset = if (forward != rightToLeft) distance else -distance
            val enter = if (reduceMotion) fadeIn(snap()) else
                slideInHorizontally(WalletMotion.sheetEnter()) { offset } + fadeIn(WalletMotion.sheetEnter())
            val exit = if (reduceMotion) fadeOut(snap()) else
                slideOutHorizontally(WalletMotion.sheetExit()) { -offset } + fadeOut(WalletMotion.sheetExit())
            ContentTransform(enter, exit, sizeTransform = walletNavigationSizeTransform(reduceMotion = true))
        }
    }
}

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
        .using(walletNavigationSizeTransform(reduceMotion))
        .apply { targetContentZIndex = if (forward) 1f else -1f }
}

/** Resize with the page transition instead of retaining the outgoing page's height until it exits. */
internal fun walletNavigationSizeTransform(reduceMotion: Boolean): SizeTransform =
    SizeTransform { _, _ -> WalletMotion.navigation(reduceMotion) }
