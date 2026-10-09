package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/** Compose iOS/Skia content on an iOS simulator; does not imitate a UIKit provider container. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
class WalletVisualIosTest {
    @Test fun pinSetup() = scenario { pin("setup") }
    @Test fun pinMismatch() = scenario { pin("mismatch") }
    @Test fun pinBiometrics() = scenario { pin("biometrics_enabled") }

    @Test fun homeEmpty() = scenario { walletHome(empty = true) }
    @Test fun homeCredential() = scenario { walletHome() }
    @Test fun scanEmpty() = scenario { scanner("empty") }
    @Test fun scanUnsupported() = scenario { scanner("unsupported") }
    @Test fun scanWebLink() = scenario { scanner("link") }

    @Test
    fun settingsRoot() = scenario { settingsRoot() }

    @Test
    fun credentialDetails() = scenario { credentialDetails() }

    @Test
    fun localizedCredentialDetails() = scenario { localizedCredentialDetails() }

    @Test
    fun batchOffer() = scenario { batchOffer() }

    @Test
    fun offerDefinitions() = scenario { offerDefinitions() }

    @Test
    fun compactBatchOffer() = scenario(size = Size(320f, 568f), dark = true, fontScale = 1.5f) { batchOffer(compact = true) }

    @Test
    fun batchOfferWithNothingSelected() = scenario { batchOffer(noneSelected = true) }

    @Test
    fun paymentReview() = scenario { paymentReview() }

    @Test
    fun credentialImages() = scenario { credentialImages() }

    @Test
    fun partialBatchResult() = scenario { partialBatchResult() }

    @Test
    fun nearbyReady() = scenario { nearbyReady() }

    private fun scenario(size: Size = Size(393f, 852f), dark: Boolean = false, fontScale: Float = 1f,
                         block: WalletVisualScenarios.() -> Unit) = runSkikoComposeUiTest(size = size) {
        WalletVisualScenarios(this,
            captureImage = { id -> onRoot().captureRoboImage(this, filePath = "compose-ios-phone-en-light/$id.png") },
            // Headless Skia tests have no UIKit window from which to read the display theme.
            platformTheme = { content -> CompositionLocalProvider(
                LocalSystemTheme provides if (dark) SystemTheme.Dark else SystemTheme.Light,
                LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content) },
        ).block()
    }
}
