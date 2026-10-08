@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import kotlinx.cinterop.toKString

import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/** Compose iOS/Skia content on an iOS simulator; does not imitate a UIKit provider container. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
class WalletVisualIosTest {
    private var previousTimeZone: String? = null
    @kotlin.test.BeforeTest fun pinTimeZone() {
        previousTimeZone = platform.posix.getenv("TZ")?.toKString()
        platform.posix.setenv("TZ", "UTC", 1)
        platform.posix.tzset()
    }
    @kotlin.test.AfterTest fun restoreTimeZone() {
        previousTimeZone?.let { platform.posix.setenv("TZ", it, 1) } ?: platform.posix.unsetenv("TZ")
        platform.posix.tzset()
    }

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
    @Test fun nearbyReview() = scenario(size = Size(320f, 568f), fontScale = 1.5f) { nearbyState("review") }
    @Test fun nearbyExpired() = scenario { nearbyState("expired") }
    @Test fun nearbyReceipt() = scenario { nearbyState("receipt") }
    @Test fun externalReceiving() = scenario() { externalReceiving() }
    @Test fun externalUnavailableCallback() = scenario() { externalReceiving(unavailable = true) }
    @Test fun providerSharingReview() = scenario { providerSharingReview() }
    @Test fun sharingCredentialInformation() = scenario { sharingCredentialInformation() }
    @Test
    fun compactProviderSharingReview() = scenario(size = Size(320f, 568f), dark = true, fontScale = 1.5f) { providerSharingReview(compact = true) }
    @Test fun providerOfferReview() = scenario { providerOfferReview() }
    @Test fun paymentSheet() = scenario { paymentReview(sheet = true) }
    @Test fun paymentLoading() = scenario { paymentState(blocked = false) }
    @Test fun paymentBlocked() = scenario { paymentState(blocked = true) }
    @Test fun paymentLocalizedCompact() = scenario(size = Size(320f, 568f), fontScale = 1.5f) { localizedPayment() }


    @Test fun providerReceivingPreparing() = scenario() { providerReceivingState("preparing") }
    @Test fun providerReceivingAuthorization() = scenario() { providerReceivingState("authorization") }
    @Test fun providerReceivingFailure() = scenario() { providerReceivingState("failure") }
    @Test fun providerReceivingPartialResult() = scenario() { providerReceivingState("partial_result") }
    @Test fun providerPreparing() = scenario() { providerSharingStatus() }
    @Test fun providerFailure() = scenario() { providerSharingStatus(failure = true) }

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
    @Test fun pinCompact() = scenario(size = Size(320f, 568f), fontScale = 1.5f, dark = true) { pin("compact_dark_large_text") }

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
    fun compactBatchOffer() = scenario(size = Size(320f, 568f), dark = true, fontScale = 1.5f) { batchOffer(compact = true) }

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

    private fun scenario(size: Size = Size(393f, 852f), dark: Boolean = false, fontScale: Float = 1f,
                         block: WalletVisualScenarios.() -> Unit) = runSkikoComposeUiTest(size = size) {
        WalletVisualScenarios(this,
            captureImage = { id ->
                val root = if (id.endsWith(".unsigned_confirmation"))
                onNode(isRoot() and hasAnyDescendant(hasTestTag("payment-unsigned-confirm")))
            else if (id.startsWith("external.") || id.startsWith("sharing.") || id.startsWith("receiving.provider") || id.startsWith("payment.sheet"))
                    onNode(isRoot() and hasAnyDescendant(hasTestTag("wallet.review.sheet"))) else onRoot()
                root.captureRoboImage(this, filePath = "compose-ios-phone-en-light/$id.png")
            },
            // Headless Skia tests have no UIKit window from which to read the display theme.
            platformTheme = { content -> CompositionLocalProvider(
                LocalSystemTheme provides if (dark) SystemTheme.Dark else SystemTheme.Light,
                LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content) },
        ).block()
    }
}
