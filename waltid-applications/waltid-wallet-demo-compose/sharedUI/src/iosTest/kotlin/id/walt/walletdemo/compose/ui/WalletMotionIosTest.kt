package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import kotlin.test.Test

@OptIn(InternalComposeUiApi::class)
class WalletMotionIosTest {
    private val scenarios = WalletMotionTestScenarios { content ->
        CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light, content = content)
    }
    @Test fun closingAndReopeningDetailsDuringMotionDoesNotReplayAStaleTransition() =
        scenarios.closingAndReopeningDetailsDuringMotionDoesNotReplayAStaleTransition()
    @Test fun reducedMotionSettlesTheCardStackWithoutWaitingForItsAnimationDuration() =
        scenarios.reducedMotionSettlesTheCardStackWithoutWaitingForItsAnimationDuration()
}
