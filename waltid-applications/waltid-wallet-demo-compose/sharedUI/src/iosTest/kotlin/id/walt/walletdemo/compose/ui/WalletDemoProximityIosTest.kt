@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.getenv
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.test.onRoot
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.cinterop.toKString
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class WalletDemoProximityIosTest {
    private val scenarios = WalletDemoProximityTestScenarios()

    @Test
    fun unmatchedRequestShowsContextAndPagesLargeRequestsWithoutConsent() =
        scenarios.unmatchedRequestShowsContextAndPagesLargeRequestsWithoutConsent(
            runUiTest = { block -> runSkikoComposeUiTest(size = Size(393f, 852f), block = block) },
        ) { id ->
            getenv("WALLET_UI_CAPTURE_DIR")?.toKString()?.let { directory ->
                onRoot().captureRoboImage(this, filePath = "$directory/compose-ios/$id.png")
            }
        }

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
    @Test
    fun replacementReviewAndCancellationRemoveStaleConsentFromTheScreen() =
        scenarios.replacementReviewAndCancellationRemoveStaleConsentFromTheScreen()
}
