package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoProximityIosTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting() =
        scenarios.engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting()

    @Test
    fun nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode() =
        scenarios.nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode()

    @Test
    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() =
        scenarios.reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions()

    @Test
    fun reviewDoesNotInventAnIdentityForAnUnsignedReader() =
        scenarios.reviewDoesNotInventAnIdentityForAnUnsignedReader()
}
