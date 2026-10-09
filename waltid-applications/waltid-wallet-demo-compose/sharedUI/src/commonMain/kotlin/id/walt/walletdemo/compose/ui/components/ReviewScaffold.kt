package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences

/**
 * Review chrome for receive and share: details scroll, actions stay pinned.
 *
 * @param fillViewport When true, the scaffold occupies the host (in-app tabs). When false, it wraps
 * the review and only grows as tall as the heading, credential, and actions — the Digital Credentials
 * trays — while still scrolling if that content would overflow the screen.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ReviewScaffold(
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    feedback: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val hazeState = rememberHazeState()
    val preferences = LocalWalletVisualPreferences.current
    val blurEnabled = !preferences.opaqueControls && preferences.blurSupported
    // Measure the controls before the body so the first frame has correct end clearance.
    // Content scrolls behind the material. Its measured end clearance keeps the final row reachable;
    // the footer intercepts its own empty area as well as its controls.
    SubcomposeLayout(modifier.fillMaxWidth().imePadding()) { constraints ->
        require(constraints.hasBoundedHeight) { "Wallet review needs a bounded screen or sheet host" }
        val loose = constraints.copy(minHeight = 0)
        val heading = subcompose("header") { header?.invoke() }.singleOrNull()?.measure(loose)
        val bodyHeight = (constraints.maxHeight - (heading?.height ?: 0)).coerceAtLeast(0)
        val controls = if (actions != null || feedback != null) subcompose("footer") {
            WalletFooter(hazeState = hazeState, feedback = feedback, actions = actions)
        }.single().measure(loose.copy(maxHeight = bodyHeight)) else null
        val endClearance = (controls?.height ?: 0).toDp()
        val body = subcompose("content") {
            val platformScroll = LocalBringIntoViewSpec.current
            val footerHeight = controls?.height ?: 0
            val unobscuredScroll = remember(platformScroll, footerHeight) {
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                        platformScroll.calculateScrollDistance(offset, size, (containerSize - footerHeight).coerceAtLeast(0f))
                }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides unobscuredScroll) {
                Column(
                    Modifier.fillMaxWidth().testTag("wallet.review.content")
                        .verticalScroll(scrollState)
                        .then(if (controls != null && blurEnabled) Modifier.hazeSource(hazeState) else Modifier)
                        .padding(horizontal = 20.dp)
                        .then(if (controls == null) Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                        .padding(top = 20.dp, bottom = 12.dp + endClearance),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Only the vertical review viewport reserves the footer; nested fields/scrollers
                    // retain their platform behavior.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides platformScroll) { content() }
                }
            }
        }.single().measure(loose.copy(minHeight = if (fillViewport) bodyHeight else 0, maxHeight = bodyHeight))
        val height = constraints.constrainHeight((heading?.height ?: 0) + maxOf(body.height, controls?.height ?: 0))
        layout(constraints.maxWidth, height) {
            heading?.placeRelative(0, 0)
            body.placeRelative(0, heading?.height ?: 0)
            controls?.placeRelative(0, height - controls.height)
        }
    }
}
