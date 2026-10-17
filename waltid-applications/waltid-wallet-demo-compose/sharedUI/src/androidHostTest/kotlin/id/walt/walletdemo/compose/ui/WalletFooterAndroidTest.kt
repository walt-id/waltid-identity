package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletFooterAndroidTest {
    private val scenarios = WalletFooterTestScenarios()

    @Test fun feedbackKeepsTopContentAndActionsStableWithLargeText() =
        scenarios.feedbackKeepsTopContentAndActionsStableWithLargeText()

    @Test fun compactReviewWrapsContentAndLeavesTheLastRowAboveActions() =
        scenarios.compactReviewWrapsContentAndLeavesTheLastRowAboveActions()

    @Test fun ordinaryScrollToKeepsInteractiveRowsAboveTheFooter() =
        scenarios.ordinaryScrollToKeepsInteractiveRowsAboveTheFooter()
}
