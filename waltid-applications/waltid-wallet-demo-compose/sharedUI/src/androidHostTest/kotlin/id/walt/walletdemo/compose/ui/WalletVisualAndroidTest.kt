package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
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
    @Test fun providerSharingReview() = scenario(sheetHost = true) { providerSharingReview() }
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-night-mdpi")
    fun compactProviderSharingReview() = scenario(fontScale = 1.5f, sheetHost = true) { providerSharingReview(compact = true) }
    @Test fun providerOfferReview() = scenario(sheetHost = true) { providerOfferReview() }
    @Test fun paymentSheet() = scenario(sheetHost = true) { paymentReview(sheet = true) }

    @Test fun keySummary() = scenario { keySetup("summary") }
    @Test fun keyRecovery() = scenario { keySetup("recovery") }
    @Test fun keyStorage() = scenario { keySetup("storage") }
    @Test fun keyApproval() = scenario { keySetup("approval") }

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

    @Test fun localSaveResult() = scenario { partialBatchResult(WalletDemoContinuationStatus.AwaitingLocalSave) }
    @Test fun remoteUncertainResult() = scenario { partialBatchResult(WalletDemoContinuationStatus.RemoteOutcomeUncertain) }
    @Test fun storageUncertainResult() = scenario { partialBatchResult(WalletDemoContinuationStatus.StorageOutcomeUncertain) }
    @Test fun partialFailureResult() = scenario { partialBatchResult(failure = true) }


    @Test
    fun nearbyReady() = scenario { nearbyReady() }

    private fun scenario(fontScale: Float = 1f, sheetHost: Boolean = false, block: WalletVisualScenarios.() -> Unit) = runAndroidComposeUiTest<ComponentActivity> {
        // A dialog capture includes the test Activity behind it. Provider activities have no action bar.
        if (sheetHost) runOnUiThread { requireNotNull(activity).actionBar?.hide() }
        WalletVisualScenarios(this, captureImage = { id ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            val root = if (id.startsWith("sharing.provider") || id.startsWith("receiving.provider") || id.startsWith("payment.sheet"))
                onNode(isRoot() and hasAnyDescendant(hasTestTag("wallet.review.sheet"))) else onRoot()
            root.captureRoboImage("$directory/android-api35-phone-en-light/$id.png")
        }, platformTheme = { content ->
            // Android RenderThread ripples do not follow the Compose test clock.
            // These are settled-state screenshots; interaction feedback remains enabled in the app.
            CompositionLocalProvider(LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)
        }).block()
    }
}
