package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoAppAndroidTest {
    private val scenarios = WalletDemoAppTestScenarios()

    @Test
    fun pinSetupBootstrapsWalletAndShowsCredentials() =
        scenarios.pinSetupBootstrapsWalletAndShowsCredentials()

    @Test
    fun receiveFlowUpdatesStatusAndCredentialList() =
        scenarios.receiveFlowUpdatesStatusAndCredentialList()

    @Test
    fun presentFlowUpdatesStatus() =
        scenarios.presentFlowUpdatesStatus()

    @Test
    fun deepLinkOfferAutoNavigatesToReceiveAndCompletes() =
        scenarios.deepLinkOfferAutoNavigatesToReceiveAndCompletes()

    @Test
    fun deepLinkPresentationPopulatesInputField() =
        scenarios.deepLinkPresentationPopulatesInputField()

    @Test
    fun credentialsPersistAcrossControllerRecreation() =
        scenarios.credentialsPersistAcrossControllerRecreation()

    @Test
    fun receiveWithoutTxCodeSucceeds() =
        scenarios.receiveWithoutTxCodeSucceeds()

    @Test
    fun manualEntryWithTxCodeRequiredShowsPromptThenReceives() =
        scenarios.manualEntryWithTxCodeRequiredShowsPromptThenReceives()

    @Test
    fun qrScanWithTxCodeRequiredShowsPincodePrompt() =
        scenarios.qrScanWithTxCodeRequiredShowsPincodePrompt()
}
