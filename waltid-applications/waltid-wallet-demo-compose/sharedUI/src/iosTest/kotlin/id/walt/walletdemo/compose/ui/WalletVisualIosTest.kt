package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/** Compose iOS/Skia content on an iOS simulator; does not imitate a UIKit provider container. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
class WalletVisualIosTest {
    @Test fun pinCreate() = scenario { pin(id.walt.walletdemo.compose.ui.screens.PinSetupPage.Create) }
    @Test fun pinConfirm() = scenario { pin(id.walt.walletdemo.compose.ui.screens.PinSetupPage.Confirm) }
    @Test fun pinBiometrics() = scenario { pin(id.walt.walletdemo.compose.ui.screens.PinSetupPage.Biometrics) }

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
    fun batchOffer() = scenario { batchOffer() }

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

    private fun scenario(block: WalletVisualScenarios.() -> Unit) = runSkikoComposeUiTest(size = Size(393f, 852f)) {
        WalletVisualScenarios(this,
            capture = { id -> onRoot().captureRoboImage(this, filePath = "compose-ios-phone-en-light/$id.png") },
            // Headless Skia tests have no UIKit window from which to read the display theme.
            platformTheme = { content -> CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light, content = content) },
        ).block()
    }
}
