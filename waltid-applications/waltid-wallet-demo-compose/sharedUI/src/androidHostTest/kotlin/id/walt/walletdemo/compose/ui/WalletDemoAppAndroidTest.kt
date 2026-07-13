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
