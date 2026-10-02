package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
@OptIn(ExperimentalTestApi::class)
class WalletVisualAndroidTest {
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
        WalletVisualScenarios(this, capture = { id ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            onRoot().captureRoboImage("$directory/android-api35-phone-en-light/$id.png")
        }).block()
    }
}
