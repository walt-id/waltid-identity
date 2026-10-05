package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WalletFooterTestScenarios(
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    @Composable private fun theme(content: @Composable () -> Unit) = platformTheme { WalletDemoTheme(content = content) }

    fun feedbackKeepsTopContentAndActionsStableWithLargeText() = runComposeUiTest {
        val message = mutableStateOf(false)
        setContent {
            theme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                    Box(Modifier.size(320.dp, 500.dp)) {
                        ReviewScaffold(header = { WalletScreenHeader("Review") },
                            feedback = { if (message.value) Text("Your changes have been saved.", Modifier.testTag("footer-message")) },
                            actions = {
                                WalletActions(WalletAction("Confirm and continue", {}, testTag = "footer-primary"),
                                    WalletAction("Return to the previous step", {}))
                            }) {
                            repeat(30) { Text("Credential information $it", Modifier.testTag("footer-item-$it")) }
                        }
                    }
                }
            }
        }
        val top = onNodeWithTag("footer-item-0").getUnclippedBoundsInRoot().top
        val actionBottom = onNodeWithTag("footer-primary").getUnclippedBoundsInRoot().bottom
        runOnIdle { message.value = true }
        onNodeWithTag("footer-message").assertIsDisplayed()
        assertEquals(top, onNodeWithTag("footer-item-0").getUnclippedBoundsInRoot().top)
        assertEquals(actionBottom, onNodeWithTag("footer-primary").getUnclippedBoundsInRoot().bottom)
        onNodeWithTag("wallet.review.content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        onNodeWithTag("footer-item-29").assertIsDisplayed()
        val last = onNodeWithTag("footer-item-29").getUnclippedBoundsInRoot()
        val footer = onNodeWithTag("wallet.footer").getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= footer.top - 8.dp, "The last item must clear feedback and wrapped controls")
        assertEquals(footer.right - 16.dp, onNodeWithTag("footer-primary").getUnclippedBoundsInRoot().right)
    }

    fun compactReviewWrapsContentAndLeavesTheLastRowAboveActions() = runComposeUiTest {
        setContent {
            theme {
                Box(Modifier.size(320.dp, 500.dp)) {
                    ReviewScaffold(fillViewport = false, actions = {
                        WalletActions(WalletAction("Receive", {}, testTag = "footer-primary"), WalletAction("Cancel", {}))
                    }) { Text("A short review", Modifier.testTag("footer-last")) }
                }
            }
        }
        onNodeWithTag("footer-primary").assertIsDisplayed()
        val content = onNodeWithTag("wallet.review.content").getUnclippedBoundsInRoot()
        val footer = onNodeWithTag("wallet.footer").getUnclippedBoundsInRoot()
        assertTrue(content.bottom - content.top < 300.dp, "Compact hosts must wrap the review rather than occupy the screen")
        assertTrue(onNodeWithTag("footer-last").getUnclippedBoundsInRoot().bottom <= footer.top - 8.dp)
        assertEquals(content.bottom, footer.top)
    }

    fun ordinaryScrollToKeepsInteractiveRowsAboveTheFooter() = runComposeUiTest {
        var clicked = -1
        setContent {
            theme {
                Box(Modifier.size(320.dp, 500.dp)) {
                    ReviewScaffold(feedback = { Text("Ready to share") }, actions = {
                        WalletActions(WalletAction("Share", {}, testTag = "footer-primary"), WalletAction("Cancel", {}))
                    }) {
                        repeat(20) { index ->
                            TextButton(onClick = { clicked = index }, modifier = Modifier.testTag("footer-link-$index")) {
                                Text("Inspect credential $index")
                            }
                        }
                    }
                }
            }
        }
        for (index in listOf(8, 19, 2)) {
            val row = onNodeWithTag("footer-link-$index")
            row.performScrollTo().assertIsDisplayed().performClick()
            runOnIdle { assertEquals(index, clicked, "Ordinary scrolling must leave the row tappable") }
            assertTrue(row.getUnclippedBoundsInRoot().bottom <= onNodeWithTag("wallet.footer").getUnclippedBoundsInRoot().top)
        }
    }
}
