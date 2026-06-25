package id.walt.walletdemo.compose.ui

import kotlin.test.Test

class WalletDemoAppIosTest {
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
}
