package id.walt.walletdemo.compose.ui

import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletDemoProximityAndroidTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun userFixedPermissionShowsSettingsWithoutChangingTheSdkAction() =
        scenarios.userFixedPermissionShowsSettingsWithoutChangingTheSdkAction()

    @Test
    fun engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting() =
        scenarios.engagementKeepsTheExactDeviceQRCodeVisibleWhileConnecting()

    @Test
    fun reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions() =
        scenarios.reviewSeparatesReaderTrustAndSendsOnlyExplicitHolderActions()

    @Test
    fun reviewDoesNotInventAnIdentityForAnUnsignedReader() =
        scenarios.reviewDoesNotInventAnIdentityForAnUnsignedReader()
}
