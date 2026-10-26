package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences

/** The sheet follows an already animated disclosure instead of animating its height a second time. */
internal class WalletExpansionAnimations {
    private val active = mutableStateMapOf<Any, Unit>()
    val running: Boolean get() = active.isNotEmpty()
    fun start(key: Any) { active[key] = Unit }
    fun finish(key: Any) { active.remove(key) }
}

internal val LocalWalletExpansionAnimations = staticCompositionLocalOf<WalletExpansionAnimations?> { null }

@Composable
internal fun WalletExpandableContent(expanded: Boolean, content: @Composable () -> Unit) {
    val visibility = remember { MutableTransitionState(expanded) }
    visibility.targetState = expanded
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    TrackWalletExpansion(!visibility.isIdle)
    AnimatedVisibility(visibility,
        enter = expandVertically(WalletMotion.disclosure(reduceMotion), expandFrom = Alignment.Top) +
            fadeIn(WalletMotion.disclosure(reduceMotion)),
        exit = shrinkVertically(WalletMotion.disclosure(reduceMotion), shrinkTowards = Alignment.Top) +
            fadeOut(WalletMotion.disclosure(reduceMotion))) { content() }
}

/** Includes custom expanders, such as the error banner's line limit. */
@Composable
internal fun TrackWalletExpansion(running: Boolean) {
    val owner = LocalWalletExpansionAnimations.current
    val key = remember { Any() }
    DisposableEffect(owner, running) {
        if (running) owner?.start(key)
        onDispose { owner?.finish(key) }
    }
}
