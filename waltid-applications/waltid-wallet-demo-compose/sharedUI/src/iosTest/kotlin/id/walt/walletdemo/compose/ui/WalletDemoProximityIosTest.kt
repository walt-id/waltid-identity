package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoProximityIosTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun connectingHidesEngagementChoicesAndQrCode() =
        scenarios.connectingHidesEngagementChoicesAndQrCode()

    @Test
    fun nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode() =
        scenarios.nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode(requiresUserAction = true)

    @Test
    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() =
        scenarios.reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions()

    @Test
    fun reviewDoesNotInventAnIdentityForAnUnsignedReader() =
        scenarios.reviewDoesNotInventAnIdentityForAnUnsignedReader()
    @Test
    fun guidedChoicesRemainUsableWithLargeTextAndDoNotShowRadios() =
        scenarios.guidedChoicesRemainUsableWithLargeTextAndDoNotShowRadios()

    @Test
    fun completedPresentationShowsDoneAndNoConnectionControls() =
        scenarios.completedPresentationShowsDoneAndNoConnectionControls()
}
