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
    private lateinit var previousTimeZone: java.util.TimeZone
    @org.junit.Before fun pinTimeZone() {
        previousTimeZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }
    @org.junit.After fun restoreTimeZone() { java.util.TimeZone.setDefault(previousTimeZone) }

    @Test fun controls() = scenario { controls() }
    @Test fun controlsRtl() = scenario { controls(rtl = true) }

    @Test fun settingsReader() = scenario { settingsRoot("reader") }
    @Test fun settingsReaderRequired() = scenario { settingsRoot("reader_required") }
    @Test fun readerTrustImport() = scenario { readerTrustImport() }
    @Test fun settingsDcApiEnabled() = scenario { settingsRoot("dc_api") }
    @Test fun settingsDcApiDisabled() = scenario { settingsRoot("dc_api", reviewEnabled = false) }
    @Test fun settingsNearby() = scenario { settingsRoot("nearby") }
    @Test fun settingsConnection() = scenario { settingsRoot("connection") }
    @Test fun settingsTechnical() = scenario { settingsRoot("technical") }
    @Test fun accountEmpty() = scenario { account("empty") }
    @Test fun accountBusy() = scenario { account("busy") }
    @Test fun accountExpired() = scenario { account("expired") }
    @Test fun nearbyPermission() = scenario { nearbyState("permission") }
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-notnight-mdpi")
    fun nearbyReview() = scenario(fontScale = 1.5f) { nearbyState("review") }
    @Test fun nearbyExpired() = scenario { nearbyState("expired") }
    @Test fun nearbyReceipt() = scenario { nearbyState("receipt") }
    @Test fun externalReceiving() = scenario(sheetHost = true) { externalReceiving() }
    @Test fun externalUnavailableCallback() = scenario(sheetHost = true) { externalReceiving(unavailable = true) }
    @Test fun providerSharingReview() = scenario(sheetHost = true) { providerSharingReview() }
    @Test fun sharingCredentialInformation() = scenario(sheetHost = true) { sharingCredentialInformation() }
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-night-mdpi")
    fun compactProviderSharingReview() = scenario(fontScale = 1.5f, sheetHost = true) { providerSharingReview(compact = true) }
    @Test fun providerOfferReview() = scenario(sheetHost = true) { providerOfferReview() }
    @Test fun paymentSheet() = scenario(sheetHost = true) { paymentReview(sheet = true) }
    @Test fun paymentLoading() = scenario { paymentState(blocked = false) }
    @Test fun paymentBlocked() = scenario { paymentState(blocked = true) }
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-notnight-mdpi")
    fun paymentLocalizedCompact() = scenario(fontScale = 1.5f) { localizedPayment() }


    @Test fun providerReceivingPreparing() = scenario(sheetHost = true) { providerReceivingState("preparing") }
    @Test fun providerReceivingAuthorization() = scenario(sheetHost = true) { providerReceivingState("authorization") }
    @Test fun providerReceivingFailure() = scenario(sheetHost = true) { providerReceivingState("failure") }
    @Test fun providerReceivingPartialResult() = scenario(sheetHost = true) { providerReceivingState("partial_result") }
    @Test fun providerPreparing() = scenario(sheetHost = true) { providerSharingStatus() }
    @Test fun providerFailure() = scenario(sheetHost = true) { providerSharingStatus(failure = true) }

    @Test fun keyApprovalUnavailable() = scenario { keySetup("approval_unavailable") }
    @Test fun keySummary() = scenario { keySetup("summary") }
    @Test fun keyRecovery() = scenario { keySetup("recovery") }
    @Test fun keyStorage() = scenario { keySetup("storage") }
    @Test fun keyApproval() = scenario { keySetup("approval") }

    @Test fun accessRejected() = scenario { walletAccess("rejected") }
    @Test fun accessBiometricFallback() = scenario { walletAccess("biometric_fallback") }
    @Test fun accessBiometricUnavailable() = scenario { walletAccess("biometric_unavailable") }
    @Test fun accessBiometricLockout() = scenario { walletAccess("biometric_lockout") }
    @Test fun accessSettings() = scenario { walletAccess("default") }
    @Test fun accessCurrentPin() = scenario { walletAccess("current_pin") }
    @Test fun accessNewPin() = scenario { walletAccess("new_pin") }
    @Test fun accessConfirmation() = scenario { walletAccess("confirmation") }
    @Test fun accessSaveFailure() = scenario { walletAccess("save_failure") }
    @Test fun accessPinChanged() = scenario { walletAccess("pin_changed") }

    @Test fun pinSetup() = scenario { pin("setup") }
    @Test fun pinMismatch() = scenario { pin("mismatch") }
    @Test fun pinConfirmation() = scenario { pin("confirmation") }
    @Test fun pinBiometricPrompt() = scenario { pin("biometric_prompt") }
    @Test fun pinUnlock() = scenario { pin("unlock") }
    @Test fun pinRtl() = scenario { pin("rtl") }
    @Test fun biometricCancelled() = scenario { biometricSetup() }
    @Test fun biometricUnavailable() = scenario { biometricSetup(unavailable = true) }
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-night-mdpi")
    fun pinCompact() = scenario(fontScale = 1.5f) { pin("compact_dark_large_text") }

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

    @Test fun singleOffer() = scenario { singleOffer() }

    @Test
    fun offerDefinitions() = scenario { offerDefinitions() }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h568dp-night-mdpi")
    fun compactBatchOffer() = scenario(fontScale = 1.5f) { batchOffer(compact = true) }

    @Test
    fun batchOfferWithNothingSelected() = scenario { batchOffer(noneSelected = true) }

    @Test
    fun paymentReview() = scenario(sheetHost = true) { paymentReview() }

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
        // Dialog captures include the test Activity behind them. Demo hosts have no action bar.
        if (sheetHost) runOnUiThread { requireNotNull(activity).actionBar?.hide() }
        WalletVisualScenarios(this, captureImage = { id ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            val root = if (id.endsWith(".unsigned_confirmation"))
                onNode(isRoot() and hasAnyDescendant(hasTestTag("payment-unsigned-confirm")))
            else if (id.startsWith("external.") || id.startsWith("sharing.") || id.startsWith("receiving.provider") || id.startsWith("payment.sheet"))
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
