package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bounded production content. System bars and provider hosts have separate integration coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w393dp-h852dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class WalletVisualAndroidTest {
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

    private fun scenario(block: WalletVisualScenarios.() -> Unit) = runComposeUiTest {
        WalletVisualScenarios(this, captureImage = { id ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            onRoot().captureRoboImage("$directory/android-api35-phone-en-light/$id.png")
        }, platformTheme = { content ->
            // Android RenderThread ripples do not follow the Compose test clock.
            CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
        }).block()
    }
}
