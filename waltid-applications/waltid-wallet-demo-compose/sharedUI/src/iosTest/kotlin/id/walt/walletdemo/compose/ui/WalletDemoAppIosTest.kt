package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoAppIosTest {
    private val legacyScenarios = WalletDemoAppTestScenarios()
    private val sheetScenarios = WalletSheetInteractionTestScenarios()

    @Test
    fun pinStorageFailureStaysLockedUntilRetrySucceeds() =
        legacyScenarios.pinStorageFailureStaysLockedUntilRetrySucceeds()

    @Test
    fun walletLaunchesCredentialsFirstWithoutPermanentTabs() =
        sheetScenarios.walletLaunchesCredentialsFirstWithoutPermanentTabs()

    @Test
    fun manualReceiveStaysInOneSheetThroughConsentAndSuccess() =
        sheetScenarios.manualReceiveStaysInOneSheetThroughConsentAndSuccess()

    @Test
    fun wrongFlowOffersSafeSwitch() =
        sheetScenarios.wrongFlowOffersSafeSwitch()

    @Test
    fun presentationReviewKeepsVerifierAndCredentialRequirementsVisible() =
        sheetScenarios.presentationReviewKeepsVerifierAndCredentialRequirementsVisible()
}
