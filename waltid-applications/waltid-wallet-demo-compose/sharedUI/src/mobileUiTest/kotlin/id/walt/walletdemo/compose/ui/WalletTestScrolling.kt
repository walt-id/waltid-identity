package id.walt.walletdemo.compose.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import kotlin.math.abs
import kotlin.test.assertTrue

/** Scroll through the real viewport, including the area obscured by pinned review actions. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performScrollToContent(test: ComposeUiTest, alignTop: Boolean = false): SemanticsNodeInteraction {
    test.waitUntil(timeoutMillis = 5_000) {
        try {
            fetchSemanticsNode()
            true
        } catch (_: AssertionError) {
            false
        }
    }
    performScrollTo()
    repeat(3) {
        val target = fetchSemanticsNode()
        val scroll = generateSequence(target.parent) { it.parent }
            .firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null } ?: return this
        if (scroll.config.getOrNull(SemanticsProperties.TestTag) != "wallet.review.content") return this
        val footer = test.onAllNodesWithTag("wallet.footer").fetchSemanticsNodes().singleOrNull() ?: return this
        val visibleTop = scroll.positionInRoot.y
        val visibleBottom = footer.positionInRoot.y
        if (target.size.height > visibleBottom - visibleTop) return this
        val distance = if (alignTop) target.positionInRoot.y - visibleTop
            else target.positionInRoot.y + target.size.height - visibleBottom
        if (if (alignTop) abs(distance) <= 1f else distance <= 1f) {
            assertTrue(target.positionInRoot.y >= visibleTop - 1f, "Content must fit above the pinned actions")
            return this
        }
        test.runOnUiThread { scroll.config[SemanticsActions.ScrollBy].action?.invoke(0f, distance) }
        test.waitForIdle()
    }
    error("Content could not be scrolled above the pinned actions")
}
