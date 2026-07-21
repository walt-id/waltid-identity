package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoAppAndroidTest {
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
