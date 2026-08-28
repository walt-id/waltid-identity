package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoProximityIosTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting() =
        scenarios.engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting()

    @Test
    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() =
        scenarios.reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions()
}
