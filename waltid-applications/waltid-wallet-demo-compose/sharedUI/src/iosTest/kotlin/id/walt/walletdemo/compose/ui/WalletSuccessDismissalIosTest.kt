package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletSuccessDismissalIosTest {
    private val scenarios = WalletSuccessDismissalTestScenarios()
    @Test fun idleSuccessClosesOnceAfterFiveSeconds() = scenarios.idleSuccessClosesOnceAfterFiveSeconds()
    @Test fun interactionKeepsTheResultOpen() = scenarios.interactionKeepsTheResultOpen()
    @Test fun backgroundAndReplacementCancelThePreviousTimer() = scenarios.backgroundAndReplacementCancelThePreviousTimer()
    @Test fun screenReaderAndIncompleteResultsStayOpen() = scenarios.screenReaderAndIncompleteResultsStayOpen()
}
