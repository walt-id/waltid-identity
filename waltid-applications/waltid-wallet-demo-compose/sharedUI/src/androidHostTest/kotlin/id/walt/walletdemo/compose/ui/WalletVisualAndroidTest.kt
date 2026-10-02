package id.walt.walletdemo.compose.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    @Config(qualifiers = "en-rUS-w320dp-h568dp-night-mdpi")
    fun compactBatchOffer() = scenario(fontScale = 1.5f) { batchOffer(compact = true) }

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

    private fun scenario(fontScale: Float = 1f, block: WalletVisualScenarios.() -> Unit) = runComposeUiTest {
        WalletVisualScenarios(this, captureImage = { id ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            onRoot().captureRoboImage("$directory/android-api35-phone-en-light/$id.png")
        }, platformTheme = { content ->
            // Android RenderThread ripples do not follow the Compose test clock.
            // These are settled-state screenshots; interaction feedback remains enabled in the app.
            CompositionLocalProvider(LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)
        }).block()
    }
}
