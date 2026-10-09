package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import kotlin.test.Test

@OptIn(InternalComposeUiApi::class)
class WalletFooterIosTest {
    private val scenarios = WalletFooterTestScenarios { content ->
        CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light, content = content)
    }

    @Test fun feedbackKeepsTopContentAndActionsStableWithLargeText() =
        scenarios.feedbackKeepsTopContentAndActionsStableWithLargeText()

    @Test fun compactReviewWrapsContentAndLeavesTheLastRowAboveActions() =
        scenarios.compactReviewWrapsContentAndLeavesTheLastRowAboveActions()

    @Test fun ordinaryScrollToKeepsInteractiveRowsAboveTheFooter() =
        scenarios.ordinaryScrollToKeepsInteractiveRowsAboveTheFooter()
}
