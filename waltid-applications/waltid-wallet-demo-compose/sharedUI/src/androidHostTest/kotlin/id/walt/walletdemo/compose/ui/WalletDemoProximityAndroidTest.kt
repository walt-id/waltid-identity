package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w393dp-h852dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class WalletDemoProximityAndroidTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun unmatchedRequestShowsContextAndPagesLargeRequestsWithoutConsent() =
        scenarios.unmatchedRequestShowsContextAndPagesLargeRequestsWithoutConsent { id ->
            System.getenv("WALLET_UI_CAPTURE_DIR")?.let { directory ->
                onRoot().captureRoboImage("$directory/android/$id.png")
            }
        }

    @Test
    fun userFixedPermissionShowsSettingsWithoutChangingTheSdkAction() =
        scenarios.userFixedPermissionShowsSettingsWithoutChangingTheSdkAction()

    @Test
    fun connectingHidesEngagementChoicesAndQrCode() =
        scenarios.connectingHidesEngagementChoicesAndQrCode()

    @Test
    fun nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode() =
        scenarios.nfcOnlyEngagementShowsHoldGuidanceWithoutInventingAQrCode()

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
    @Test
    fun replacementReviewAndCancellationRemoveStaleConsentFromTheScreen() =
        scenarios.replacementReviewAndCancellationRemoveStaleConsentFromTheScreen()
}
