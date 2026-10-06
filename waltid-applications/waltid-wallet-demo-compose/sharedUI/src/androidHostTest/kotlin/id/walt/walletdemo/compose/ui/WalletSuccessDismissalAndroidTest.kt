package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletSuccessDismissalAndroidTest {
    private val scenarios = WalletSuccessDismissalTestScenarios()
    @Test fun idleSuccessClosesOnceAfterFiveSeconds() = scenarios.idleSuccessClosesOnceAfterFiveSeconds()
    @Test fun interactionKeepsTheResultOpen() = scenarios.interactionKeepsTheResultOpen()
    @Test fun backgroundAndReplacementCancelThePreviousTimer() = scenarios.backgroundAndReplacementCancelThePreviousTimer()
    @Test fun screenReaderAndIncompleteResultsStayOpen() = scenarios.screenReaderAndIncompleteResultsStayOpen()
}
