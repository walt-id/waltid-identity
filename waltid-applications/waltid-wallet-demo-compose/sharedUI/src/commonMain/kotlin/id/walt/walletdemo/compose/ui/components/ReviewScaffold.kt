package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
    val blurEnabled = !LocalWalletVisualPreferences.current.opaqueControls
    // Measure the controls before the body so the first frame has correct end clearance.
    // The scroll viewport ends above the controls. An overlay with end padding alone leaves
    // bring-into-view, accessibility scrolling and hit testing unaware of the obstruction.
    SubcomposeLayout(modifier.fillMaxWidth().imePadding()) { constraints ->
        require(constraints.hasBoundedHeight) { "Wallet review needs a bounded screen or sheet host" }
        val loose = constraints.copy(minHeight = 0)
        val heading = subcompose("header") { header?.invoke() }.singleOrNull()?.measure(loose)
        val bodyHeight = (constraints.maxHeight - (heading?.height ?: 0)).coerceAtLeast(0)
        val controls = if (actions != null || feedback != null) subcompose("footer") {
            WalletFooter(hazeState = hazeState, feedback = feedback, actions = actions)
        }.single().measure(loose.copy(maxHeight = bodyHeight)) else null
        val viewportHeight = (bodyHeight - (controls?.height ?: 0)).coerceAtLeast(0)
        val body = subcompose("content") {
            Column(
                Modifier.fillMaxWidth().testTag("wallet.review.content")
                    .verticalScroll(scrollState)
                    // Capture the scrolling content inside its visual clip. The footer can sample
                    // that source without extending the interactive/accessibility viewport beneath it.
                    .then(if (controls != null && blurEnabled) Modifier.hazeSource(hazeState) else Modifier)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
            )
        }.single().measure(loose.copy(minHeight = if (fillViewport) viewportHeight else 0, maxHeight = viewportHeight))
        val height = constraints.constrainHeight((heading?.height ?: 0) + body.height + (controls?.height ?: 0))
        layout(constraints.maxWidth, height) {
            heading?.placeRelative(0, 0)
            body.placeRelative(0, heading?.height ?: 0)
            controls?.placeRelative(0, height - controls.height)
        }
    }
}
