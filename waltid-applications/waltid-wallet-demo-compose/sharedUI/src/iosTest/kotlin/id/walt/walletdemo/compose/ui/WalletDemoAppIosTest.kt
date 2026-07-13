package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoAppIosTest {
    private val scenarios = WalletDemoAppTestScenarios()

    @Test
    fun pinSetupBootstrapsWalletAndShowsCredentials() =
        scenarios.pinSetupBootstrapsWalletAndShowsCredentials()

    @Test
    fun savedCredentialOpensNeutralDetails() =
        scenarios.savedCredentialOpensNeutralDetails()

    @Test
    fun receiveFlowUpdatesStatusAndCredentialList() =
        scenarios.receiveFlowUpdatesStatusAndCredentialList()

    @Test
    fun presentFlowUpdatesStatus() =
        scenarios.presentFlowUpdatesStatus()

    @Test
    fun deepLinkReceiveAndPresentFlowUpdatesStatus() =
        scenarios.deepLinkReceiveAndPresentFlowUpdatesStatus()

    @Test
    fun credentialsPersistAcrossControllerRecreation() =
        scenarios.credentialsPersistAcrossControllerRecreation()
}
