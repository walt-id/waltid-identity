package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import id.walt.walletdemo.compose.logic.WalletDemoCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoCredentialHolders
import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus
import id.walt.walletdemo.compose.logic.WalletDemoDeferredCredential
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.ui.components.SettingsCopyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityAction
import id.walt.wallet2.mobile.ProximityActionResult
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityConfiguration
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximitySessionConfiguration
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.walletdemo.compose.logic.ProximityPresentationBackend
import id.walt.wallet2.mobile.ProximityRuntimeObservation
import id.walt.wallet2.mobile.ProximitySession
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.ProximityTransportCapability
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.DemoBiometricResult
import id.walt.walletdemo.compose.logic.InMemoryDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.WalletDemoProximityApprovalMode
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.DemoPinStore
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController

import id.walt.walletdemo.compose.logic.WalletDemoKeyChoice
import id.walt.walletdemo.compose.logic.WalletDemoKeySetupOption
import id.walt.walletdemo.compose.logic.WalletDemoIdentitySetup
import id.walt.walletdemo.compose.logic.WalletDemoIdentityDetails
import id.walt.walletdemo.compose.ui.screens.IdentitySetupScreen
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.InMemoryDemoReaderTrustSettingsStore
import id.walt.walletdemo.compose.logic.WalletDemoBootstrapResult
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoCredentialClaimMetadata
import id.walt.walletdemo.compose.logic.WalletDemoIssuerMetadata
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceAuthorization
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceSession
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosure
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationError
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewResult
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoProximityTransportProfile
import id.walt.walletdemo.compose.logic.WalletDemoResponseEncryption
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionAvailability
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeRequirement
import id.walt.walletdemo.compose.logic.WalletDemoVerifierMetadata
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.isStatusVisible
import id.walt.walletdemo.compose.logic.statusText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.io.encoding.Base64
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WalletDemoAppTestScenarios(
    private val contentWrapper: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {

    fun biometricUnlockTakesPrecedenceThenFocusesPinAfterDecline() = runComposeUiTest {
        val memory = InMemoryDemoPinStore()
        memory.setBiometricUnlockEnabled(true)
        val store = object : DemoPinStore by memory { override fun hasPin() = true }
        val gate = CompletableDeferred<DemoBiometricResult>()
        var prompts = 0
        val biometrics = object : DemoBiometricAuthenticator {
            override fun availability() = DemoBiometricAvailability.Available
            override suspend fun authenticate(reason: String): DemoBiometricResult { prompts++; return gate.await() }
        }
        val controller = WalletDemoController(WalletUiTestWallet(), store, biometrics)
        setWalletContent { WalletDemoApp(controller) }
        waitUntil { prompts == 1 }
        onNodeWithTag(WalletUiTestTags.PinInput).assert(isFocused().not()).assertIsNotEnabled()
        gate.complete(DemoBiometricResult.Failed)
        waitUntil { !controller.state.value.isAuthenticating }
        waitForIdle()
        onNodeWithTag(WalletUiTestTags.PinInput).assertIsFocused().assertIsEnabled()
        controller.handleApplicationForegrounded()
        waitForIdle()
        assertEquals(1, prompts)
    }

    fun pinSetupRequiresFourDigitsAndMatchingConfirmation() = runComposeUiTest {
        val pinStore = InMemoryDemoPinStore()
        val controller = WalletDemoController(WalletUiTestWallet(), pinStore)
        setWalletContent { WalletDemoApp(controller) }
        onNodeWithText("Step 1 of 2").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinInput).assertIsDisplayed().assertIsFocused()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        onAllNodesWithTag(WalletUiTestTags.PinConfirmationInput).assertCountEquals(0)
        onAllNodesWithTag("wallet.pinBiometricToggle").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("１２12a3")
        assertEquals("123", (controller.state.value.auth as WalletAuthState.Setup).pin)
        onAllNodesWithTag(WalletUiTestTags.PinSubmitButton).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("45")
        onAllNodesWithTag(WalletUiTestTags.PinSubmitButton).assertCountEquals(0)
        onNodeWithText("Step 2 of 2").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PinInput).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).assertIsDisplayed().assertIsFocused()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).performTextInput("123")
        onNodeWithTag(WalletUiTestTags.PinBackButton).performClick()
        onNodeWithText("Step 1 of 2").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("1234")
        assertEquals("", (controller.state.value.auth as WalletAuthState.Setup).confirmation)
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).performTextInput("4321")
        onNodeWithText("PIN confirmation does not match").assertIsDisplayed()
        assertFalse(pinStore.hasPin())
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).performTextInput("1234")
        waitUntil { controller.state.value.auth == WalletAuthState.Unlocked }
        assertFalse(pinStore.isBiometricUnlockEnabled())
    }

    fun pinConfirmationPromptsBiometricsAndDeclineCompletesSetup() = runComposeUiTest {
        val pinStore = InMemoryDemoPinStore()
        val gate = CompletableDeferred<DemoBiometricResult>()
        var prompts = 0
        val biometrics = object : DemoBiometricAuthenticator {
            override fun availability() = DemoBiometricAvailability.Available
            override suspend fun authenticate(reason: String): DemoBiometricResult { prompts++; return gate.await() }
        }
        val controller = WalletDemoController(WalletUiTestWallet(), pinStore, biometrics)
        setWalletContent { WalletDemoApp(controller) }
        confirmNewPin()
        waitUntil { prompts == 1 }
        onNodeWithTag(WalletUiTestTags.BiometricSetupRetry).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).assertIsNotEnabled()
        onAllNodesWithTag("wallet.pinBiometricToggle").assertCountEquals(0)
        assertTrue(pinStore.hasPin())
        assertFalse(pinStore.isBiometricUnlockEnabled())
        gate.complete(DemoBiometricResult.Cancelled)
        waitUntil { !controller.state.value.isAuthenticating }
        onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).performClick()
        waitUntil { controller.state.value.auth == WalletAuthState.Unlocked }
        assertFalse(pinStore.isBiometricUnlockEnabled())
    }

    fun scannerPastePreservesEditsAndNeverStartsAFlow() = runComposeUiTest {
        val url = "openid-credential-offer://mock"
        val gate = CompletableDeferred<ClipEntry?>()
        var reads = 0
        var opened = false
        val clipboard = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = when (++reads) {
                1 -> gate.await()
                2 -> plainTextClipEntry(url)
                3 -> null
                else -> error("Clipboard permission denied")
            }
            override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
        }
        setWalletContent {
            // Keep TextField's platform clipboard-availability probes outside the paste-button fixture.
            id.walt.walletdemo.compose.ui.screens.WalletScanScreen(onBack = {},
                onOpen = { _, _ -> opened = true }, initialInput = "Original", clipboard = clipboard)
        }
        assertEquals(0, reads, "Clipboard access starts with the user's paste gesture")
        onNodeWithTag("wallet.scanPaste").performClick()
        waitUntil(timeoutMillis = 5_000) { reads > 0 }
        assertEquals(1, reads, "A pending paste must start only one clipboard read")
        onNodeWithTag("wallet.scanPaste").assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextReplacement("Edited while waiting")
        gate.complete(plainTextClipEntry(url))
        waitUntil { !onNodeWithTag("wallet.scanPaste").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        onNodeWithTag(WalletUiTestTags.ScanInput).assertTextContains("Edited while waiting")
        onNodeWithTag("wallet.scanPaste").performClick()
        onNodeWithTag(WalletUiTestTags.ScanInput).assertTextContains(url)
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsEnabled()
        onNodeWithTag("wallet.scanPaste").performClick()
        onNodeWithText("There is no text in the clipboard.").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ScanInput).assertTextContains(url)
        onNodeWithTag("wallet.scanPaste").performClick()
        onNodeWithText("Could not paste this link. Paste it into the field and try again.").assertIsDisplayed()
        assertFalse(opened)
        assertEquals(4, reads)
    }

    fun scannerResolvesWebLinksAndKeepsFailureRecoverable() = runComposeUiTest {
        var calls = 0
        var opened: id.walt.walletdemo.compose.logic.ResolvedWalletLink? = null
        setWalletContent {
            id.walt.walletdemo.compose.ui.screens.WalletScanScreen(onBack = {},
                onOpen = { url, kind -> opened = id.walt.walletdemo.compose.logic.ResolvedWalletLink(url, kind) },
                initialInput = "https://example.test/link", resolveLink = { value ->
                    id.walt.walletdemo.compose.logic.resolveWalletLink(value) {
                        calls++
                        id.walt.walletdemo.compose.logic.WalletLinkDocument(200, if (calls == 1) "<html>Landing page</html>"
                            else """{"credential_issuer":"https://issuer.example","credential_configuration_ids":["resident"]}""")
                    }
                })
        }
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil { calls == 1 }
        onNodeWithText("Try again").assertIsDisplayed()
        assertEquals(null, opened)
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil { opened != null }
        assertEquals(id.walt.walletdemo.compose.logic.WalletLinkKind.Offer, opened?.kind)
        assertEquals(2, calls)
    }

    fun scannerBackCancelsLinkResolution() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        var shown by mutableStateOf(true)
        var started = false
        var cancelled = false
        var opened = false
        setWalletContent {
            if (shown) id.walt.walletdemo.compose.ui.screens.WalletScanScreen(onBack = { shown = false },
                onOpen = { _, _ -> opened = true }, initialInput = "https://example.test/link", resolveLink = {
                    started = true
                    try { gate.await(); error("Should be cancelled") } finally { cancelled = true }
                })
        }
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil { started }
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.FlowBack).performClick()
        waitUntil { cancelled }
        gate.complete(Unit)
        waitForIdle()
        assertFalse(opened)
    }

    fun pinStorageFailureStaysLockedUntilRetrySucceeds() = runComposeUiTest {
        val pinStore = RecoverableDemoPinStore()
        val controller = WalletDemoController(WalletUiTestWallet(), pinStore)

        setWalletContent { WalletDemoApp(controller) }

        onNodeWithText("PIN storage unavailable").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinInput").assertCountEquals(0)

        pinStore.isAvailable = true
        onNodeWithTag("wallet.pinStorageRetryButton").performClick()
        waitForIdle()

        onNodeWithText("Enter your PIN").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinConfirmationInput").assertCountEquals(0)
    }

    fun pinSetupOffersPINOnlyWhenBiometricsAreUnavailable() = runComposeUiTest {
        val controller = WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        onNodeWithText("Choose a PIN").assertIsDisplayed()
        confirmNewPin()
        waitUntil { controller.state.value.auth == WalletAuthState.Unlocked }
        assertFalse(controller.isBiometricUnlockEnabled())
    }

    fun pinScreenRefreshesBiometricAvailabilityWhenItBecomesAvailable() = runComposeUiTest {
        val biometrics = RecordingDemoBiometricAuthenticator(available = false)
        val controller = WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore(), biometrics)
        setWalletContent { WalletDemoApp(controller) }
        beginPinConfirmation()
        assertFalse(controller.state.value.biometricUnlockAvailable)
        biometrics.available = true
        controller.refreshBiometricUnlockAvailability()
        waitForIdle()
        assertTrue(controller.state.value.biometricUnlockAvailable)
        onAllNodesWithText("Next, your device will offer biometric unlock. Decline to keep using your PIN.").assertCountEquals(0)
    }

    fun walletAccessChangesPinUsingTheSharedEntryFlow() = runComposeUiTest {
        val store = InMemoryDemoPinStore()
        val controller = WalletDemoController(WalletUiTestWallet(), store)
        setWalletContent { WalletDemoApp(controller) }
        confirmNewPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsWalletAccess).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsChangePin).performClick()
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("0000")
        waitUntil { (controller.state.value.access.pinChange as? id.walt.walletdemo.compose.logic.WalletPinChange.Current)?.error != null }
        onNodeWithText("Wrong PIN").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("1234")
        waitUntil { controller.state.value.access.pinChange is id.walt.walletdemo.compose.logic.WalletPinChange.NewPin }
        onNodeWithText("Choose a new PIN").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinCancelButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsChangePin).assertIsDisplayed().performClick()
        assertTrue(store.verifyPin("1234"))
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("1234")
        waitUntil { controller.state.value.access.pinChange is id.walt.walletdemo.compose.logic.WalletPinChange.NewPin }
        onNodeWithTag(WalletUiTestTags.PinInput).performTextInput("5678")
        onNodeWithTag(WalletUiTestTags.PinConfirmationInput).performTextInput("5678")
        waitUntil { controller.state.value.access.pinChange == null }
        onNodeWithText("PIN changed").assertIsDisplayed()
        assertTrue(store.verifyPin("5678"))
        assertFalse(store.verifyPin("1234"))
        onNodeWithTag(WalletUiTestTags.SettingsChangePin).assertIsDisplayed()
    }

    fun pinSetupKeepsClearReachable() = runComposeUiTest {
        val controller = WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }

        onNodeWithTag(WalletUiTestTags.PinClearButton).assertIsDisplayed()
    }

    fun keySetupDefaultNeedsOneConfirmation() = runComposeUiTest {
        fun choice(id: String) = WalletDemoKeyChoice(id, id, "Details for $id")
        val recommended = WalletDemoKeySetupOption("recommended", choice("new"), choice("hardware"), choice("biometric"))
        var submitted: String? = null
        setWalletContent {
            IdentitySetupScreen(WalletDemoIdentitySetup.Choose(listOf(recommended)), null,
                onChoose = { submitted = it }, onResume = {}, onCancel = {}, onRefresh = {})
        }
        onNodeWithTag("wallet.keySetupEdit.Recovery").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.keySetupEdit.Storage").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.keySetupEdit.Approval").performScrollTo().assertIsDisplayed()
        assertEquals(null, submitted)
        onNodeWithText("Create signing key").performClick()
        assertEquals("recommended", submitted)
    }

    fun keySetupGroupsChoicesAndConfirmsSelectedConfiguration() = runComposeUiTest {
        fun value(name: String) = WalletDemoKeyChoice(name, name, "Details for $name")
        fun option(recovery: String, storage: String, approval: String) = WalletDemoKeySetupOption(
            "$recovery-$storage-$approval", value(recovery), value(storage), value(approval),
        )
        val options = listOf(option("new", "hardware", "biometric"), option("new", "native", "biometric"),
            option("new", "native", "none"), option("backup", "native", "biometric"), option("backup", "database", "none"))
        var submitted: String? = null
        setWalletContent {
            IdentitySetupScreen(WalletDemoIdentitySetup.Choose(options), null,
                onChoose = { submitted = it }, onResume = {}, onCancel = {}, onRefresh = {})
        }
        onNodeWithTag("wallet.keySetupEdit.Recovery").performScrollTo().performClick()
        onAllNodesWithText("new").assertCountEquals(1)
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Recovery", 1)).performScrollTo().performClick().assertIsSelected()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onNodeWithTag("wallet.keySetupEdit.Storage").performScrollTo().performClick()
        onAllNodesWithText("hardware").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onNodeWithTag("wallet.keySetupEdit.Approval").performScrollTo().performClick()
        onAllNodesWithText("biometric").assertCountEquals(0)
        onAllNodesWithText("Refresh available options").assertCountEquals(0)
        onNodeWithText("Back").performClick()
        onNodeWithTag("wallet.keySetupEdit.Storage").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).assertIsSelected()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        assertEquals(null, submitted, "Customizing choices must not create or restore a key")
        onNodeWithTag("wallet.keySetupEdit.Approval").assertTextContains("none")
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        assertEquals("backup-database-none", submitted)
    }

    fun pinSetupDoesNotAskForSigningApproval() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        controller.handleApplicationForegrounded()
        setWalletContent { WalletDemoApp(controller) }
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.biometricSigningAvailability ==
                WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
        }

        onAllNodesWithTag(WalletUiTestTags.SigningProtectionBiometric).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.SigningProtectionNone).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PinSubmitButton).assertCountEquals(0)
    }

    fun pendingIssuanceIsReachableAndSavedDetailsDoNotResumeIt() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentials = listOf(sampleCredential), deferredCredentials = listOf(
            WalletDemoDeferredCredential("local", intervalSeconds = null, status = WalletDemoContinuationStatus.AwaitingLocalSave),
            WalletDemoDeferredCredential("uncertain", intervalSeconds = null, status = WalletDemoContinuationStatus.RemoteOutcomeUncertain),
        ))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithText("Pending · 2").performClick()
        onNodeWithTag("issuance-resume-local").performScrollTo().assertIsEnabled()
        onAllNodesWithTag("issuance-resume-uncertain").assertCountEquals(0)
        val reads = wallet.continuationReads
        onNodeWithTag("issuance-refresh").performClick()
        waitUntil { wallet.continuationReads > reads }
        assertEquals(emptyList(), wallet.resumedContinuations)
        onNodeWithTag("issuance-resume-local").performScrollTo().performClick()
        awaitTaggedNode("issuance-saved-cred-1")
        onNodeWithTag("issuance-saved-cred-1").performScrollTo().performClick()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag("issuance-done").performClick()
        onNodeWithText("Pending · 1").assertIsDisplayed()
        assertEquals(listOf("local"), wallet.resumedContinuations)
    }

    fun credentialsTabShowsCompactCardsAndNavigatesToDetails() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }

        unlockWithPin()

        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onAllNodesWithTag("wallet.tab.credentials").assertCountEquals(0)
        onAllNodesWithTag("wallet.tab.receive").assertCountEquals(0)
        onAllNodesWithTag("wallet.tab.present").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsButton).assertIsDisplayed()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithText("Example Credential").assertIsDisplayed()
        onNodeWithContentDescription("walt.id").assertIsDisplayed()

        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.SettingsButton).fetchSemanticsNodes().isEmpty()
        }
        onAllNodesWithText("walt.id Wallet").assertCountEquals(0)
        onNodeWithContentDescription("Close").assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").assertIsDisplayed()
        onNodeWithContentDescription("More").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.DetailsMenu).assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("credential-technical-details").fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText("Example Credential").assertCountEquals(1)
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithText("Street address").performScrollTo().assertIsDisplayed()
        onNodeWithText("Main Street 1").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("portrait")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Portrait").assertIsDisplayed()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(WalletUiTestTags.claimImage("portrait")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(WalletUiTestTags.claimImage("portrait"))
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("signature_usual_mark")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Signature or usual mark").assertIsDisplayed()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(WalletUiTestTags.claimImage("signature_usual_mark")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(WalletUiTestTags.claimImage("signature_usual_mark"))
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("verification_artifact")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Verification artifact").assertIsDisplayed()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(WalletUiTestTags.claimImage("verification_artifact")).fetchSemanticsNodes().isNotEmpty()
        }
        val artifactPath = "verification_artifact"
        awaitEnabledImage(artifactPath)
        onNodeWithTag(WalletUiTestTags.claimImage(artifactPath))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        onNodeWithTag(WalletUiTestTags.claimImageViewer(artifactPath)).assertIsDisplayed()
        onNodeWithContentDescription("Full-screen credential image").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImageViewerClose(artifactPath))
            .assertIsDisplayed()
            .performClick()
        onAllNodesWithTag(WalletUiTestTags.claimImageViewer(artifactPath)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage(artifactPath)).assertIsDisplayed()
        onNodeWithTag("credential-technical-details").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.claim("system.format")).assertCountEquals(0)
        onNodeWithTag("credential-technical-details").performClick()
        onNodeWithText("About this credential").performScrollTo().assertIsDisplayed()
        onNodeWithText("Example Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("system.format")).performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Raw credential data").assertCountEquals(0)
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").performClick()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.SettingsButton).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("walt.id Wallet").assertIsDisplayed()
        assertEquals(1, wallet.bootstrapCalls)
    }

    fun credentialsTabWaitsForCredentialRead() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val wallet = WalletUiTestWallet(credentials = listOf(sampleCredential), credentialsGate = gate)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        awaitTaggedNode(WalletUiTestTags.CredentialsLoading)
        onNodeWithTag(WalletUiTestTags.CredentialsEmpty).assertDoesNotExist()
        gate.complete(Unit)
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag(WalletUiTestTags.CredentialsLoading).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.CredentialsEmpty).assertDoesNotExist()
    }

    fun credentialsTabDoesNotShowEmptyOnLoadFailure() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val controller = WalletDemoController(WalletUiTestWallet(credentialsGate = gate), InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        awaitTaggedNode(WalletUiTestTags.CredentialsLoading)
        gate.completeExceptionally(IllegalStateException("Credential storage unavailable"))
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session is WalletSessionState.Failed &&
                onAllNodesWithTag(WalletUiTestTags.CredentialsLoading).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag(WalletUiTestTags.CredentialsLoading).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.CredentialsEmpty).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertDoesNotExist()
        onNodeWithTag("wallet.openingRetry").assertIsDisplayed().assertIsEnabled()
    }

    fun credentialsTabShowsEmptyStateAndUpdatesAfterReceive() = runComposeUiTest {
        val wallet = WalletUiTestWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentials.empty").assertIsDisplayed()
        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        wallet.credentials = listOf(sampleCredential)
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithText("Example Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithTag("issuance-identity-ExampleCredential").assertExists()
        onAllNodesWithText("vc+sd-jwt").assertCountEquals(0)
        assertIssuerDetailsCollapsedUntilRequested()
        mainClock.autoAdvance = false
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.receiveCompleted }
        mainClock.advanceTimeBy(500)
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithTag("issuance-done").assertIsDisplayed().performClick()
        mainClock.autoAdvance = true
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onAllNodesWithTag("wallet.receiveNewButton").assertCountEquals(0)
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        awaitTaggedNode(WalletUiTestTags.claim("given_name"))
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").performClick()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        runOnIdle { controller.startNewReceiveFlow(); controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onNodeWithTag("wallet.offerInput").assertTextContains("")
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
    }

    fun receiveTabCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        runOnIdle { controller.startNewReceiveFlow(); controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onNodeWithTag("wallet.offerInput").assertTextContains("")
        onNodeWithTag("wallet.receiveButton").assertIsNotEnabled()
    }

    fun receiveDetailsStayScopedToReceiveTabNavigationStack() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        awaitTaggedNode(WalletUiTestTags.claim("given_name"))
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()

        runOnIdle { controller.startNewReceiveFlow(); controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
    }

    fun receiveTabDisablesUrlControlsWhileReceiving() = runComposeUiTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = WalletUiTestWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            receiveGate = receiveGate,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferReview).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.operation is WalletOperationState.Receiving }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()

        receiveGate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
    }

    fun transactionCodeOfferCanBeDeclinedWithoutCode() = runComposeUiTest {
        val wallet = WalletUiTestWallet(transactionCodeRequired = true)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://example")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }

        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.OfferDeclineButton)
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview == null }
        onNodeWithTag("wallet.status").assertTextContains("Credential offer declined")
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferInput).assertDoesNotExist()
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals(null, wallet.receivedOfferUrl)
    }

    fun batchCopyControlsRequireSelectionAndRespectTheAdvertisedLimit() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val wallet = WalletUiTestWallet(batchSize = 3, receiveGate = gate)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://batch")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag("issuance-copies-ExampleCredential").performScrollTo().assertTextEquals("Copies: 1")
        onNodeWithTag("issuance-fewer-ExampleCredential").assertIsNotEnabled()
        repeat(2) { onNodeWithTag("issuance-more-ExampleCredential").performClick() }
        onNodeWithTag("issuance-copies-ExampleCredential").assertTextEquals("Copies: 3")
        onNodeWithTag("issuance-more-ExampleCredential").assertIsNotEnabled()
        onNodeWithTag("issuance-details-ExampleCredential").performScrollTo().performClick()
        onNodeWithText("Values have not been received yet.").assertIsDisplayed()
        onNodeWithText("The issuer has not supplied claim definitions.").assertIsDisplayed()
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag("issuance-copies-ExampleCredential").performScrollTo().assertTextEquals("Copies: 3")
        onNodeWithTag("issuance-select-ExampleCredential").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag("issuance-select-ExampleCredential").performClick()
        onNodeWithTag("issuance-more-ExampleCredential").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performClick()
        waitUntil(timeoutMillis = 5_000) { wallet.receivedSelections != null }
        assertEquals(WalletDemoCredentialHolders.NewKeys(2), wallet.receivedSelections?.single()?.holders)
        onNodeWithTag("issuance-more-ExampleCredential").assertIsNotEnabled()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.operation !is WalletOperationState.Receiving }
    }

    fun authorizationCodeOfferExplainsIssuerSignIn() = runComposeUiTest {
        val controller = WalletDemoController(
            WalletUiTestWallet(issuanceGrant = WalletDemoIssuanceGrant.AuthorizationCode),
            InMemoryDemoPinStore(),
        )

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://authorization-code")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }

        onNodeWithTag(WalletUiTestTags.OfferAuthorizationSection).performScrollTo().assertIsDisplayed()
        onNodeWithText("Issuer sign-in").performScrollTo().assertIsDisplayed()
        onNodeWithText("Continuing opens your browser to sign in with the issuer before the credential is issued.")
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton)
            .assertIsDisplayed()
        onNodeWithText("Continue to sign in").assertIsDisplayed()
        onAllNodesWithText("Accept").assertCountEquals(0)
    }

    fun offerClaimsUseSemanticGroupsAndInclusionLabels() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            offeredCredential = WalletDemoOfferedCredentialMetadata(
                configurationId = "org.iso.23220.photoid.1",
                format = "mso_mdoc",
                vct = null,
                doctype = "org.iso.23220.photoid.1",
                display = WalletDemoMetadataDisplay(
                    name = "Photo ID",
                    logoUri = null,
                    logoAltText = null,
                ),
                claims = listOf(
                    WalletDemoCredentialClaimMetadata(
                        path = listOf("org.iso.23220.1", "given_name"),
                        mandatory = true,
                        displayName = "Given name",
                    ),
                    WalletDemoCredentialClaimMetadata(
                        path = listOf("org.iso.23220.1", "age_over_18"),
                        mandatory = true,
                        displayName = null,
                    ),
                    WalletDemoCredentialClaimMetadata(
                        path = listOf("org.iso.23220.1", "age_over_65"),
                        mandatory = false,
                        displayName = null,
                    ),
                    WalletDemoCredentialClaimMetadata(
                        path = listOf("org.iso.23220.dtc.1", "dtc_dg1"),
                        mandatory = null,
                        displayName = null,
                    ),
                    WalletDemoCredentialClaimMetadata(
                        path = listOf("org.iso.23220.dtc.1", "dtc_sod"),
                        mandatory = true,
                        displayName = null,
                    ),
                ),
            )
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Receive) }
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://example")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }

        onNodeWithTag("issuance-identity-org.iso.23220.photoid.1").assertExists()
        onAllNodesWithTag(WalletUiTestTags.OfferSupportedClaims).assertCountEquals(0)
        onAllNodesWithText("mso_mdoc").assertCountEquals(0)
        onAllNodesWithText("18 or older").assertCountEquals(0)
        onNodeWithTag("issuance-details-org.iso.23220.photoid.1").performScrollTo().performClick()
        onNodeWithText("Values have not been received yet.").assertIsDisplayed()
        onNodeWithText("Given name").assertIsDisplayed()
        onNodeWithText("18 or older").performScrollTo().assertIsDisplayed()
        onNodeWithText("65 or older").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Always included").assertCountEquals(3)
        onAllNodesWithText("May be included").assertCountEquals(2)
        onNodeWithTag("wallet-detail-back").performClick()
        onNodeWithTag("issuance-select-org.iso.23220.photoid.1").assertIsOn()
    }

    fun walletHomeExposesUnifiedScanAndNearby() = runComposeUiTest {
        val controller = WalletDemoController(
            WalletUiTestWallet(credentials = listOf(sampleCredential)),
            InMemoryDemoPinStore(),
        )

        var nearbyStarted = false
        setWalletContent { WalletDemoApp(controller, onStartProximityPresentation = { nearbyStarted = true }) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.ScanButton).performClick()
        onNodeWithContentDescription("Enter a link").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.ScanInput).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.ScanContinue).assertCountEquals(0)
        onNodeWithTag("wallet.scanMode").performClick()
        onNodeWithTag(WalletUiTestTags.ScanInput).assertIsDisplayed()
        onNodeWithContentDescription("Paste link").assertIsDisplayed()
        onNodeWithTag("wallet.scanMode").performClick()
        onAllNodesWithTag(WalletUiTestTags.ScanInput).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.FlowBack).performClick()
        onNodeWithTag(WalletUiTestTags.ProximityStartButton).performClick()
        assertTrue(nearbyStarted)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
    }

    fun scannerRoutesOfferWithoutAcceptingAndBackDiscardsReview() = runComposeUiTest {
        val wallet = WalletUiTestWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.ScanButton).performClick()
        onNodeWithTag("wallet.scanMode").performClick()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextInput("openid-credential-offer://example")
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferReview).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.OfferInput).assertCountEquals(0)
        assertEquals(null, wallet.receivedOfferUrl)
        onNodeWithTag(WalletUiTestTags.FlowBack).performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        assertEquals(null, controller.state.value.offerPreview)
    }

    fun scannerBlocksUnsupportedCodesAndRecognizesInlineWebRequests() = runComposeUiTest {
        val controller = WalletDemoController(WalletUiTestWallet(presentationPreview = samplePresentationPreview), InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.ScanButton).performClick()
        onNodeWithTag("wallet.scanMode").performClick()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextInput("FIDO:/123456")
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsNotEnabled()
        onNodeWithText("This is a passkey sign-in code. Scan it with your device's system camera.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextReplacement("javascript:alert(1)")
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextReplacement("https://example.test/request")
        onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsEnabled()
        onNodeWithText("Receive credentials").assertDoesNotExist()
        onNodeWithText("Share credentials").assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.ScanInput).performTextReplacement("https://example.test/?client_id=demo&dcql_query=%7B%7D")
        onNodeWithTag(WalletUiTestTags.ScanContinue).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        onNodeWithTag(WalletUiTestTags.PresentationReview).assertIsDisplayed()
    }

    fun embeddedPresentationJourneyKeepsWalletChrome() = runComposeUiTest {
        val controller = WalletDemoController(
            WalletUiTestWallet(credentials = listOf(sampleCredential)),
            InMemoryDemoPinStore(),
        )

        setWalletContent {
            WalletDemoAppHost(
                controller = controller,
                presentationContent = { Text("Embedded in-person journey") },
            )
        }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithText("Embedded in-person journey").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.AppTitle).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsButton).assertIsDisplayed()
        // The modal host supplies task feedback; the collection retains its own readiness.
        onNodeWithTag(WalletUiTestTags.Status).assertTextContains("Wallet ready")
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
    }

    fun presentTabAllowsPreviewAndDeclineWithoutCredentials() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = emptyList(),
            presentationPreview = samplePresentationPreview.copy(credentialOptions = emptyList()),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")

        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithText("No credentials available").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.PresentationRejectButton).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.presentationPreview == null &&
                controller.state.value.requestDrafts.presentationRequestUrl.isEmpty()
        }
        assertEquals("openid4vp://example", wallet.rejectedRequestUrl)
        onNodeWithText("Presentation declined").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onNodeWithTag("wallet.presentationDone").performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
    }

    fun invalidPresentationCanBeDismissedLocallyOrReportedToVerifier() = runComposeUiTest {
        val error = WalletDemoPresentationError(
            previewHandle = samplePresentationPreview.previewHandle,
            verifierMetadata = samplePresentationPreview.verifierMetadata,
            clientId = samplePresentationPreview.clientId,
            responseEncryption = samplePresentationPreview.responseEncryption,
            errorCode = "invalid_transaction_data",
            message = "Unsupported transaction data type",
        )
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewResult = WalletDemoPresentationPreviewResult.Invalid(error),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://invalid")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationError == error }

        onNodeWithTag(WalletUiTestTags.PresentationError).performScrollTo().assertIsDisplayed()
        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        onNodeWithText("Unsupported transaction data type").performScrollTo().assertIsDisplayed()
        onNodeWithText("OpenID4VP error: invalid_transaction_data").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.PresentationErrorNotifyButton).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.PresentationErrorDismissButton)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationError == null }

        assertEquals(null, wallet.rejectedRequestUrl)
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled().performTextInput("openid4vp://invalid")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationError == error }
        onNodeWithTag(WalletUiTestTags.PresentationErrorNotifyButton).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.presentationError == null &&
                controller.state.value.requestDrafts.presentationRequestUrl.isEmpty()
        }

        assertEquals("openid4vp://invalid", wallet.rejectedRequestUrl)
        onNodeWithText("Verifier notified").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onNodeWithTag("wallet.presentationDone").performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
    }

    fun presentTabPreviewsCredentialsAndCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        assertPresentationActionsFollowReviewContent()
        onAllNodesWithTag("wallet.presentationInput").assertCountEquals(0)
        onNodeWithTag("wallet.presentationSubmitButton").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationVerifierSection).performScrollTo().assertIsDisplayed()
        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationResponseProtectionSection).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationTechnicalDetailsSection).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationCredential(samplePresentationCredentialOption.selection.id), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().assertIsDisplayed()

        onNodeWithText("Disclosure 7").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Family name").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag("wallet-detail-back").performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationClaimsDialog).assertCountEquals(0)

        onNodeWithTag("wallet.presentationSubmitButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.presentationResult").assertIsDisplayed()
        onNodeWithText("Presentation sent").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationReview).assertCountEquals(0)
        onNodeWithTag("wallet.presentationDone").performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        assertEquals(WalletDemoTab.Credentials, controller.state.value.selectedTab)
        assertEquals("openid4vp://example", wallet.previewedRequestUrl)
        assertEquals("openid4vp://example", wallet.submittedRequestUrl)
    }

    fun presentTabDeclineSendsProtocolRejection() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.FlowBack).performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        assertEquals(null, wallet.rejectedRequestUrl)
        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.PresentationRejectButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation declined" }

        assertEquals("openid4vp://example", wallet.rejectedRequestUrl)
        onNodeWithText("Presentation declined").assertIsDisplayed()
        onNodeWithTag("wallet.presentationResult").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onNodeWithTag("wallet.presentationDone").performClick()
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
    }

    fun presentTabShowsUnencryptedResponseState() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onAllNodesWithTag(WalletUiTestTags.PresentationResponseProtectionSection).assertCountEquals(0)
        onAllNodesWithText("Not requested").assertCountEquals(0)
        onAllNodesWithText("Key management algorithm").assertCountEquals(0)
        onAllNodesWithText("Verifier key thumbprint").assertCountEquals(0)
    }

    fun presentationDisclosureImagesRenderAsImages() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(pathOnlyPortraitDisclosureCredentialOption),
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        val portraitDisclosurePath = "disclosures[0].portrait"
        onNodeWithTag(WalletUiTestTags.claim(portraitDisclosurePath)).performScrollTo().assertIsDisplayed()
        awaitEnabledImage(portraitDisclosurePath)

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(pathOnlyPortraitDisclosureCredentialOption.selection.id)).performScrollTo().performClick()
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        onAllNodesWithText("$.portrait").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.claim("portrait")).performScrollTo().assertIsDisplayed()
        awaitTaggedNode(WalletUiTestTags.claimImage("portrait"))
        awaitEnabledImage("portrait")
        onNodeWithTag(WalletUiTestTags.claimImage("portrait"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        onNodeWithTag(WalletUiTestTags.claimImageViewer("portrait")).assertIsDisplayed()
        onNodeWithContentDescription("Full-screen credential image").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImageViewerClose("portrait"))
            .assertIsDisplayed()
            .performClick()
        onAllNodesWithTag(WalletUiTestTags.claimImageViewer("portrait")).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage("portrait")).assertIsDisplayed()
    }

    fun presentationWithoutVerifierDisplayKeepsClientIdInTechnicalDetails() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                verifierMetadata = null,
                clientId = sampleDidClientId,
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.PresentationVerifierSection).performScrollTo().assertIsDisplayed()
        onAllNodesWithText(sampleDidClientId).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performClick()
        onNodeWithText(sampleDidClientId).performScrollTo().assertIsDisplayed()
    }

    fun presentationDetailsResolveDuplicateCredentialOptionsIndependently() = runComposeUiTest {
        val identityDisclosure = WalletDemoPresentationDisclosureSelection("identity", "cred-1", "$.given_name")
        val ageDisclosure = WalletDemoPresentationDisclosureSelection("age", "cred-1", "$.age_over_18")
        val identityOption = samplePresentationCredentialOption.copy(
            queryId = "identity",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Identity disclosure",
                    path = identityDisclosure.path,
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = true, required = true, selectable = false,
                )
            ),
        )
        val ageOption = samplePresentationCredentialOption.copy(
            queryId = "age",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Age disclosure",
                    path = ageDisclosure.path,
                    valueJson = "\"Over 18\"",
                    displayValue = "Over 18",
                    selectivelyDisclosable = true, required = true, selectable = false,
                )
            ),
        )
        val ageWithStoredValue = ageOption.copy(credentialDataJson = """{"given_name":"Ada","age_over_18":true}""")
        val identityWithStoredValue = identityOption.copy(credentialDataJson = ageWithStoredValue.credentialDataJson)
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(identityWithStoredValue, ageWithStoredValue),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("identity", "age")))
                ),
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(identityDisclosure.id)).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(ageDisclosure.id)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(identityOption.selection.id)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsEnabled()

        onNodeWithText("Identity disclosure").performScrollTo().assertIsDisplayed()
        onNodeWithText("Age disclosure").performScrollTo().assertIsDisplayed()
        onNodeWithText("Over 18").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.presentationClaimsToggle(ageOption.selection.id)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(identityOption.selection.id)).performScrollTo().performClick()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Age over 18").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
    }

    fun presentDetailsStayScopedToPresentTabNavigationStack() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithText("Requested").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag("wallet-detail-back").performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationClaimsDialog).assertCountEquals(0)

        runOnIdle { controller.selectTab(WalletDemoTab.Credentials) }
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag(WalletUiTestTags.PresentationReview).assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id))
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun presentTabDisablesUrlControlsWhilePreviewing() = runComposeUiTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = compactPresentationPreview,
            previewGate = previewGate,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Resolving presentation..." }
        onNodeWithTag("wallet.status").assertTextContains("Resolving presentation...")
        onNodeWithTag("wallet.presentationInput").assertIsNotEnabled()
        onNodeWithTag("wallet.presentButton").assertIsNotEnabled()

        previewGate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        awaitTaggedNode(WalletUiTestTags.PresentationActions)
        onAllNodesWithTag("wallet.presentationInput").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationActions).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    fun deepLinksRouteToReceiveAndPresentTabs() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"), presentationPreview = samplePresentationPreview)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        // Cold external entry must survive the one-screen PIN setup before preparing anything.
        controller.handleDeepLink(offerUrl)
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag("wallet.external.flow").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.OfferInput).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.issuanceReceipt != null }
        onNodeWithTag("issuance-done").assertIsDisplayed().performClick()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        // Warm entry uses the same owner and automatically resolves the other protocol.
        controller.handleDeepLink(requestUrl)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onNodeWithTag("wallet.presentationSubmitButton").performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithText("Presentation sent").assertIsDisplayed()
        onNodeWithTag("wallet.presentationResult").assertIsDisplayed()
        assertEquals(offerUrl, wallet.receivedOfferUrl)
        assertEquals(requestUrl, wallet.previewedRequestUrl)
        assertEquals(requestUrl, wallet.submittedRequestUrl)
        onNodeWithTag("wallet.external.close").performClick()
        onAllNodesWithTag("wallet.external.flow").assertCountEquals(0)
    }

    fun openingExternalReviewInAppPreservesSelectionsWithoutReplayingTheRequest() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        controller.handleDeepLink("openid-credential-offer://example")
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        val original = controller.state.value.offerPreview
        val configuration = original!!.offeredCredentials.first().configurationId
        runOnIdle { controller.updateIssuanceCopies(configuration, 0) }
        val choices = controller.state.value.issuanceCopyCounts
        onNodeWithTag("wallet.external.openInApp").performClick()
        onAllNodesWithTag("wallet.review.sheet").assertCountEquals(0)
        onAllNodesWithTag("wallet.external.openInApp").assertCountEquals(0)
        assertEquals(original, controller.state.value.offerPreview)
        assertEquals(choices, controller.state.value.issuanceCopyCounts)
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag("wallet.external.close").performClick()
        onAllNodesWithTag("wallet.external.flow").assertCountEquals(0)
    }

    fun externalOfferFailureRemainsVisibleAndCanBeCorrected() = runComposeUiTest {
        val backing = WalletUiTestWallet(transactionCodeRequired = true, credentialsAfterReceive = listOf(sampleCredential))
        val wallet = object : DemoWallet by backing {
            override suspend fun continuePreAuthorizedIssuance(
                sessionId: String, transactionCode: String?, credentials: List<WalletDemoCredentialSelection>,
            ): WalletDemoIssuanceOutcome {
                check(transactionCode == "123456") { "The transaction code is incorrect" }
                return backing.continuePreAuthorizedIssuance(sessionId, transactionCode, credentials)
            }
        }
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        controller.handleDeepLink("openid-credential-offer://example")
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.TxCodeInput).performScrollTo().performTextInput("000000")
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.operation is WalletOperationState.Failed }
        onNodeWithTag(WalletUiTestTags.Status).assertIsDisplayed()
            .assertTextContains("The transaction code is incorrect", substring = true)
        onNodeWithTag(WalletUiTestTags.TxCodeInput).performScrollTo().performTextReplacement("123456")
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.issuanceReceipt != null }
        onNodeWithTag("issuance-done").assertIsDisplayed()
    }

    fun duplicateExternalLinksPreserveReviewUntilExplicitlyClosed() = runComposeUiTest {
        val url = "openid-credential-offer://example"
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential), presentationPreview = samplePresentationPreview)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        controller.handleDeepLink(url)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        val originalPreview = controller.state.value.offerPreview
        controller.handleDeepLink(url)
        waitForIdle()
        assertEquals(originalPreview, controller.state.value.offerPreview)
        onAllNodesWithTag(WalletUiTestTags.OfferInput).assertCountEquals(0)
        onNodeWithTag("wallet.external.close").performClick()
        onAllNodesWithTag("wallet.external.flow").assertCountEquals(0)
        controller.handleDeepLink("openid4vp://example")
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        val review = controller.state.value.presentationReview
        controller.handleDeepLink("openid4vp://example")
        waitForIdle()
        assertEquals(review, controller.state.value.presentationReview)
        onNodeWithTag(WalletUiTestTags.PresentationReview).assertIsDisplayed()
        onNodeWithTag("wallet.external.close").performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationReview).assertCountEquals(0)
    }

    fun credentialsPersistAcrossControllerRecreation() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentialsAfterReceive = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val firstController = WalletDemoController(wallet, pinStore)
        var activeController by mutableStateOf(firstController)

        setWalletContent { WalletDemoApp(activeController) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.session is WalletSessionState.Ready }

        firstController.handleDeepLink("openid-credential-offer://example")
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.issuanceReceipt != null }
        onNodeWithTag("issuance-done").performClick()
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        val recreatedController = WalletDemoController(wallet, pinStore)
        activeController = recreatedController
        waitForIdle()
        onNodeWithText("Enter your PIN").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinConfirmationInput").assertCountEquals(0)
        loginWithPin()
        waitUntil(timeoutMillis = 5_000) { recreatedController.state.value.session is WalletSessionState.Ready }

        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        assertEquals(2, wallet.bootstrapCalls)
    }

    fun customBrandingTitleAppearsInTheHeader() = runComposeUiTest {
        val wallet = WalletUiTestWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        val branding = WalletDemoBranding(appTitle = "Acme Wallet")

        setWalletContent { WalletDemoApp(controller, branding) }
        onNodeWithText("Acme Wallet").assertIsDisplayed()
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.AppTitle).assertTextEquals("Acme Wallet")
    }

    fun sharingApprovalPreferenceIsConsistentAndPersistsAcrossJourneys() = runComposeUiTest {
        val settings = InMemoryDemoSharingSettingsStore()
        val controller = WalletDemoController(WalletUiTestWallet(credentials = listOf(sampleCredential)),
            InMemoryDemoPinStore(), sharingSettings = settings)
        val backend = PreferenceProximityBackend()
        val proximity = WalletDemoProximityController(backend, approvalModeProvider = settings::proximityApprovalMode,
            profileProvider = settings::proximityTransportProfile, scope = CoroutineScope(Dispatchers.Unconfined), dispatcher = Dispatchers.Unconfined)
        val trust = DemoReaderTrustSettingsController(InMemoryDemoReaderTrustSettingsStore())
        setWalletContent { MobileWalletDemoApp(controller, proximity, trust) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.ProximityStartButton).assertIsDisplayed().assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { proximity.state.value.sessionState is ProximityState.EngagementReady }
        onNodeWithTag("proximity-show-Qr").performScrollTo().assertIsDisplayed().performClick()
        val prepared = onNodeWithTag("proximity-approval-prepare")
        prepared.performScrollTo().assertIsOff()
        val before = prepared.getUnclippedBoundsInRoot()
        prepared.performClick()
        waitUntil(timeoutMillis = 5_000) { proximity.state.value.refreshingEngagement }
        prepared.assertIsOn().assertIsNotEnabled()
        assertEquals(before, prepared.getUnclippedBoundsInRoot(), "Mode refresh must retain the prepared position")
        onAllNodesWithTag(WalletUiTestTags.ProximityQr).assertCountEquals(0)
        assertEquals(WalletDemoProximityApprovalMode.PrepareSharing, settings.proximityApprovalMode())
        backend.firstClose.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { !proximity.state.value.refreshingEngagement }
        prepared.assertIsOn().assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.ProximityQr).performScrollTo().assertIsDisplayed()

        // Connection options belong to this task and Back preserves its phase and approval choice.
        val closesBeforeOptions = backend.closedSessions
        onNodeWithText("Connection options").performScrollTo().performClick()
        onNodeWithText("Connection method").assertIsDisplayed()
        assertTrue(proximity.state.value.active)
        assertEquals(closesBeforeOptions, backend.closedSessions)
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.ProximityQr).performScrollTo().assertIsDisplayed()
        prepared.assertIsOn()
        // Close performs cleanup once and returns to the collection, with no manual-request detour.
        onNodeWithTag(WalletUiTestTags.ProximityCancel).performClick()
        waitUntil(timeoutMillis = 5_000) { !proximity.state.value.active }
        waitUntil { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationInput).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation).performScrollTo().performClick()
        prepared.performScrollTo().assertIsOn().performClick().assertIsOff()
        onNodeWithText("Connection method").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsProximityNfcV2Direct).performScrollTo().performClick()
        repeat(3) { onNodeWithTag(WalletUiTestTags.SettingsBack).performClick() }
        onNodeWithTag(WalletUiTestTags.ProximityStartButton).performClick()
        waitUntil(timeoutMillis = 5_000) {
            proximity.state.value.sessionState is ProximityState.EngagementReady &&
                proximity.state.value.engagementChoices == listOf(ProximityEngagementMethod.Nfc)
        }
        onAllNodesWithTag("proximity-show-Qr").assertCountEquals(0)
        prepared.assertIsOff().performClick()
        waitUntil { proximity.state.value.approvalMode == WalletDemoProximityApprovalMode.PrepareSharing && !proximity.state.value.refreshingEngagement }
        runOnIdle { backend.latestState.value = ProximityState.Completed(1, false) }
        onNodeWithTag(WalletUiTestTags.ProximityDone).performClick()
        waitUntil { !proximity.state.value.active && controller.state.value.selectedTab == WalletDemoTab.Credentials }
        assertEquals(WalletDemoProximityApprovalMode.PrepareSharing,
            WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore(), sharingSettings = settings).state.value.proximityApprovalMode)
    }

    fun settingsReplacesHeaderLockAndShowsDidAndKey() = runComposeUiTest {
        val wallet = WalletUiTestWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller, onStartProximityPresentation = {}) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onAllNodesWithText("Lock").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.SettingsButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsScreen).assertIsDisplayed()
        onNodeWithText("Technical details").performClick()
        onNodeWithTag(WalletUiTestTags.SettingsDid).assertTextContains("did:key:test")
        onNodeWithTag(WalletUiTestTags.SettingsKeyId).assertTextContains("key-1")
        val session = controller.state.value.session as WalletSessionState.Ready
        assertTrue(session.publicJwk.contains("OKP"), session.publicJwk)
        onNodeWithContentDescription("Show public key").performScrollTo().performClick()
        // iOS Compose text matching does not treat JSON fragments as substrings.
        onNodeWithTag(WalletUiTestTags.SettingsPublicJwk)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsCredentialSharing)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Digital Credentials API").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOn()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).assertIsOff()
        assertEquals(false, controller.state.value.showDcApiPresentationPreview)
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Signing key").performScrollTo().performClick()
        onAllNodesWithTag(WalletUiTestTags.SettingsCredentialSharing).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.SettingsReset).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Digital Credentials API").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).performScrollTo().assertIsOff()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation).performClick()
        onNodeWithText("Connection method").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsProximityNfcV2Direct)
            .performScrollTo()
            .performClick()
        assertEquals(
            WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct,
            controller.state.value.proximityTransportProfile,
        )
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsLock)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsReset)
            .performScrollTo()
            .assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.SettingsLock).performClick()
        onNodeWithText("Enter your PIN").assertIsDisplayed()
    }

    fun technicalCopyPreservesFullValueWithoutChangingExpansion() = runComposeUiTest {
        val original = """{"kty":"EC","crv":"P-256","x":"complete-public-key-coordinate","y":"another-complete-coordinate"}"""
        // The headless iOS Compose test owner supplies a no-op clipboard.
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val clipboard = object : Clipboard {
            var copied: ClipEntry? = null
            override suspend fun getClipEntry(): ClipEntry? = copied
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                writeStarted.complete(Unit)
                finishWrite.await()
                copied = clipEntry
            }
        }
        setWalletContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                Column {
                    SettingsCopyRow("Wallet DID", "did:jwk:example", "did-value", "did-copy", "Copy wallet DID", "Wallet DID copied")
                    SettingsCopyRow("Public key (JWK)", original, "jwk-value", "jwk-copy", "Copy public key as JWK", "Public key copied",
                        disclosureLabels = "Show public key" to "Hide public key", formatJson = true)
                }
            }
        }
        mainClock.autoAdvance = false
        val disclosure = onNodeWithContentDescription("Show public key")
        val originalBounds = disclosure.getUnclippedBoundsInRoot()
        onNodeWithTag("did-copy").performClick()
        waitUntil { writeStarted.isCompleted }
        assertTrue(!onNodeWithTag("did-copy").fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
        runOnIdle { finishWrite.complete(Unit) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals(originalBounds, disclosure.getUnclippedBoundsInRoot(), "Copy feedback must not move the next control")
        assertEquals("Wallet DID copied", onNodeWithTag("did-copy").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        mainClock.advanceTimeBy(2_100)
        waitForIdle()
        assertEquals(originalBounds, disclosure.getUnclippedBoundsInRoot(), "Expiring copy feedback must not move the next control")
        assertTrue(!onNodeWithTag("did-copy").fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
        mainClock.autoAdvance = true
        onAllNodesWithTag("jwk-value").assertCountEquals(0)
        onNodeWithTag("jwk-copy").assertHasClickAction().assertIsEnabled()
        assertTrue(!onNodeWithTag("jwk-copy").fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility))
        onNodeWithTag("jwk-copy").performClick()
        runOnIdle { assertEquals(original, clipboard.copied?.plainText()) }
        assertEquals("Public key copied", onNodeWithTag("jwk-copy").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        onAllNodesWithTag("jwk-value").assertCountEquals(0)
        onNodeWithContentDescription("Show public key").performClick()
        onNodeWithTag("jwk-value").assertTextContains("complete-public-key-coordinate", substring = true)
        onNodeWithTag("jwk-copy").performClick()
        runOnIdle { assertEquals(original, clipboard.copied?.plainText()) }
        onNodeWithTag("jwk-value").assertIsDisplayed()
        onNodeWithContentDescription("Hide public key").performClick()
        onAllNodesWithTag("jwk-value").assertCountEquals(0)
        onNodeWithTag("jwk-copy").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        onNodeWithText("Copy public key as JWK").assertIsDisplayed()
    }

    fun copyIsCancelledWhenRowLeavesComposition() = runComposeUiTest {
        var visible by mutableStateOf(true)
        val writeStarted = CompletableDeferred<Unit>()
        val writeCancelled = CompletableDeferred<Unit>()
        val clipboard = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = null
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                writeStarted.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    writeCancelled.complete(Unit)
                }
            }
        }
        setWalletContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                if (visible) SettingsCopyRow("Wallet DID", "did:jwk:example", "did-value", "did-copy", "Copy wallet DID", "Wallet DID copied")
            }
        }
        onNodeWithTag("did-copy").performClick()
        waitUntil { writeStarted.isCompleted }
        runOnIdle { visible = false }
        onAllNodesWithTag("did-copy").assertCountEquals(0)
        waitUntil { writeCancelled.isCompleted }
    }

    fun readerTrustSettingsReviewAndPersistPublicCa() = runComposeUiTest {
        val store = InMemoryDemoReaderTrustSettingsStore()
        val controller = DemoReaderTrustSettingsController(
            store = store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
        )

        setWalletContent { Column(Modifier.verticalScroll(rememberScrollState())) { DemoReaderTrustSettings(controller) } }

        val allowUntrusted = onNodeWithTag(
            WalletUiTestTags.SettingsReaderPolicyAllowUntrusted
        )
        val requireTrusted = onNodeWithTag(
            WalletUiTestTags.SettingsReaderPolicyRequireTrusted
        )
        allowUntrusted.assertHasClickAction()
        requireTrusted.assertHasClickAction()
        assertEquals(
            true,
            allowUntrusted.fetchSemanticsNode().config[SemanticsProperties.Selected],
        )
        assertEquals(
            false,
            requireTrusted.fetchSemanticsNode().config[SemanticsProperties.Selected],
        )

        requireTrusted.performClick()
        waitForIdle()
        assertEquals(
            ProximityReaderPolicy.RequireTrusted,
            store.load().readerPolicy,
        )
        assertEquals(
            true,
            requireTrusted.fetchSemanticsNode().config[SemanticsProperties.Selected],
        )

        runOnIdle {
            handleReaderTrustImportPickerResult(
                controller,
                ReaderTrustImportPickerResult.Selected(
                    ReaderTrustImportFile(
                        name = "wal-1349-local-reader-ca.der",
                        bytes = Base64.Default.decode(TestReaderCaDerBase64),
                    )
                ),
            )
        }
        waitUntil(timeoutMillis = 5_000) { controller.state.value.pendingImport != null }

        val preview = requireNotNull(controller.state.value.pendingImport)
        assertEquals("wal-1349-local-reader-ca.der", preview.sourceName)
        assertEquals(
            "CN=WAL-1349 Local Reader Test CA",
            preview.readerAuthorities.single().displayName,
        )
        assertEquals("CN=WAL-1349 Local Reader Test CA", preview.readerAuthorities.single().subject)
        assertEquals(TestReaderCaSha256, preview.readerAuthorities.single().sha256Fingerprint)
        assertTrue(store.load().trustAnchors.isEmpty())
        // The controller preview can be ready before the modal joins the UI tree on iOS.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.SettingsReaderTrustImportReview)
                .fetchSemanticsNodes().size == 1
        }
        waitForIdle()
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportReview).assertIsDisplayed()
        onNodeWithText("Review import").assertIsDisplayed()
        onNodeWithText("wal-1349-local-reader-ca.der").assertIsDisplayed()
        onAllNodesWithText("CN=WAL-1349 Local Reader Test CA", useUnmergedTree = true)[0].performScrollTo().assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportCancel).performClick()
        waitForIdle()
        assertEquals(null, controller.state.value.pendingImport)
        assertTrue(store.load().trustAnchors.isEmpty())
        assertEquals(
            ProximityReaderPolicy.RequireTrusted,
            store.load().readerPolicy,
        )

        val stateBeforePickerCancellation = controller.state.value
        runOnIdle {
            handleReaderTrustImportPickerResult(
                controller,
                ReaderTrustImportPickerResult.Cancelled,
            )
        }
        assertEquals(stateBeforePickerCancellation, controller.state.value)

        runOnIdle {
            handleReaderTrustImportPickerResult(
                controller,
                ReaderTrustImportPickerResult.Failed(
                    IllegalStateException("The selected file could not be read")
                ),
            )
        }
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustError)
            .assertTextContains("The selected file could not be read")
        assertEquals("The selected file could not be read", controller.state.value.error)
        assertTrue(store.load().trustAnchors.isEmpty())

        runOnIdle {
            handleReaderTrustImportPickerResult(
                controller,
                ReaderTrustImportPickerResult.Selected(
                    ReaderTrustImportFile(
                        name = "wal-1349-local-reader-ca.der",
                        bytes = Base64.Default.decode(TestReaderCaDerBase64),
                    )
                ),
            )
        }
        waitUntil(timeoutMillis = 5_000) { controller.state.value.pendingImport != null }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.SettingsReaderTrustImportReview)
                .fetchSemanticsNodes().size == 1
        }
        waitForIdle()
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportReview).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportConfirm).performClick()
        waitForIdle()

        assertEquals(null, controller.state.value.pendingImport)
        assertEquals(1, store.load().trustAnchors.size)
        assertEquals(
            ProximityReaderPolicy.RequireTrusted,
            store.load().readerPolicy,
        )
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustReset).performScrollTo().performClick()
        onNodeWithText("Cancel").performClick()
        assertEquals(1, store.load().trustAnchors.size)
        onNodeWithContentDescription("Remove CN=WAL-1349 Local Reader Test CA").performScrollTo().performClick()
        onNodeWithText("Cancel").performClick()
        assertEquals(1, store.load().trustAnchors.size)
        onNodeWithContentDescription("Remove CN=WAL-1349 Local Reader Test CA").performScrollTo().performClick()
        onNodeWithTag("reader-trust-remove-confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { !controller.state.value.importInProgress }
        assertTrue(store.load().trustAnchors.isEmpty())
        assertEquals(ProximityReaderPolicy.RequireTrusted, store.load().readerPolicy)
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustReset).performScrollTo().performClick()
        onNodeWithTag("reader-trust-reset-confirm").performClick()
        waitUntil(timeoutMillis = 5_000) { !controller.state.value.importInProgress }
        assertTrue(store.load().trustAnchors.isEmpty())
        assertEquals(ProximityReaderPolicy.AllowAnonymousOrUntrusted, store.load().readerPolicy)
    }

    fun newUnlockAttemptPromptsBiometricsOnceAfterLock() = runComposeUiTest {
        val pinStore = InMemoryDemoPinStore()
        val biometrics = RecordingDemoBiometricAuthenticator()
        val controller = WalletDemoController(WalletUiTestWallet(), pinStore, biometrics)

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        assertEquals(1, biometrics.authenticateCalls)

        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsLock)
            .performScrollTo()
            .performClick()
        waitForIdle()

        controller.handleApplicationForegrounded()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.auth is WalletAuthState.Unlocked
        }
        assertEquals(2, biometrics.authenticateCalls)
    }

    fun settingsConfirmsAndAppliesSigningProtectionChange() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentials = listOf(sampleCredential))
        val identityDetailsRequested = CompletableDeferred<Unit>()
        val identityDetailsResponse = CompletableDeferred<WalletDemoIdentityDetails?>()
        val delayedWallet = object : DemoWallet by wallet {
            override suspend fun identityDetails(): WalletDemoIdentityDetails? {
                identityDetailsRequested.complete(Unit)
                return identityDetailsResponse.await()
            }
        }
        val pinStore = InMemoryDemoPinStore()
        val controller = WalletDemoController(delayedWallet, pinStore)

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        waitUntil(timeoutMillis = 5_000) { identityDetailsRequested.isCompleted }
        onNodeWithTag(WalletUiTestTags.SettingsScreen).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.SigningProtectionNone).assertCountEquals(0)

        onNodeWithText("Signing key").performClick()
        identityDetailsResponse.complete(null)
        // Identity details load on the controller's dispatcher, outside Compose's idle tracking.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag(WalletUiTestTags.SigningProtectionNone) and isEnabled())
                .fetchSemanticsNodes().size == 1
        }
        onNodeWithTag(WalletUiTestTags.SigningProtectionNone)
            .performScrollTo()
            .performClick()
        onNodeWithText("Change signing protection?").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SigningProtectionConfirm).performClick()

        waitUntil(timeoutMillis = 5_000) {
            (controller.state.value.session as? WalletSessionState.Ready)?.signingProtection ==
                WalletDemoSigningProtection.None
        }
        assertEquals(1, wallet.deleteWalletCalls)
        assertTrue(pinStore.hasPin())
        assertEquals(WalletDemoSigningProtection.None, controller.state.value.selectedSigningProtection)
    }

    fun credentialDetailsCanCopyAndDelete() = runComposeUiTest {
        val wallet = WalletUiTestWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        val clipboard = RecordingClipboard()

        setWalletContent {
            CompositionLocalProvider(LocalClipboard provides clipboard) { WalletDemoApp(controller) }
        }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag(WalletUiTestTags.DetailsMenu).assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.CopyRawCredential).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(WalletUiTestTags.CopyRawCredential).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(sampleCredential.credentialDataJson, clipboard.entry?.plainText()) }
        onNodeWithTag(WalletUiTestTags.DetailsMenu).performClick()
        onNodeWithTag(WalletUiTestTags.DeleteCredential).performClick()
        onNodeWithTag(WalletUiTestTags.DeleteCredentialConfirm).performClick()
        waitUntil(timeoutMillis = 5_000) {
            (controller.state.value.session as? WalletSessionState.Ready)?.credentials.orEmpty().isEmpty()
        }
        assertEquals(listOf("cred-1"), wallet.deletedCredentialIds)
        waitUntil(timeoutMillis = 5_000) {
            (controller.state.value.session as? WalletSessionState.Ready)?.credentials.orEmpty().isEmpty()
        }
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
    }

    fun deleteFromCredentialsWhileAReviewIsActive() = runComposeUiTest {
        val wallet = WalletUiTestWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        runOnIdle { controller.selectTab(WalletDemoTab.Present) }
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        val selectionId = samplePresentationCredentialOption.selection.id
        assertTrue(selectionId != "cred-1")
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(selectionId)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        onNodeWithTag("wallet-detail-back").performClick()

        runOnIdle { controller.selectTab(WalletDemoTab.Credentials) }
        awaitTaggedNode(WalletUiTestTags.credentialCard("cred-1"))
        onNodeWithTag(WalletUiTestTags.credentialCard("cred-1")).performClick()
        awaitTaggedNode(WalletUiTestTags.DetailsMenu)
        onNodeWithTag(WalletUiTestTags.DetailsMenu).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.DeleteCredential).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(WalletUiTestTags.DeleteCredential).performClick()
        onNodeWithTag(WalletUiTestTags.DeleteCredentialConfirm).performClick()
        waitUntil(timeoutMillis = 5_000) {
            (controller.state.value.session as? WalletSessionState.Ready)?.credentials.orEmpty().isEmpty()
        }
        assertEquals(listOf("cred-1"), wallet.deletedCredentialIds)
        assertEquals(null, controller.state.value.presentationReview)
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
    }

    fun successStatusCanBeDismissedFromTheFooter() = runComposeUiTest {
        val wallet = WalletUiTestWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setWalletContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session is WalletSessionState.Ready &&
                controller.state.value.statusText == "Wallet ready" &&
                controller.state.value.isStatusVisible
        }
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithTag("wallet.status.feedback").assertIsDisplayed()
        onAllNodesWithTag("wallet.status.contextual").assertCountEquals(0)
        val headerTop = onNodeWithTag("wallet.screen.header").getUnclippedBoundsInRoot().top
        onNodeWithTag(WalletUiTestTags.StatusDismiss).performClick()
        onAllNodesWithTag("wallet.status").assertCountEquals(0)
        onAllNodesWithTag("wallet.footer").assertCountEquals(0)
        assertEquals(headerTop, onNodeWithTag("wallet.screen.header").getUnclippedBoundsInRoot().top)
    }

    private fun ComposeUiTest.setWalletContent(content: @Composable () -> Unit) {
        setContent { contentWrapper(content) }
    }

    fun proximityQrFitsWalletChromeWithoutScrolling(capture: ComposeUiTest.(String) -> Unit = {}) = runComposeUiTest {
        val controller = WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore())
        val proximity = mutableStateOf(WalletDemoProximityUiState(
            active = true,
            sessionState = ProximityState.EngagementReady(listOf(
                ProximityEngagement.Qr("mdoc:" + "A7v9kQ2_x-".repeat(30)),
                ProximityEngagement.Nfc,
            )),
            preferredEngagement = ProximityEngagementMethod.Qr,
        ))
        var cancelled = false
        var settingsOpened = false
        setWalletContent {
            WalletDemoAppHost(controller, onOpenSettings = {
                settingsOpened = true
                proximity.value = WalletDemoProximityUiState()
            }, presentationContent = if (proximity.value.active) ({
                WalletReviewHost(WalletReviewPresentation.Sheet, true, {
                    cancelled = true
                    proximity.value = WalletDemoProximityUiState()
                    controller.selectTab(WalletDemoTab.Credentials)
                }) {
                Column(Modifier.fillMaxSize().heightIn(max = 720.dp)) {
                    id.walt.walletdemo.compose.ui.components.WalletScreenHeader("Share nearby", leading = {
                        androidx.compose.material3.IconButton(onClick = {
                            cancelled = true
                            proximity.value = WalletDemoProximityUiState()
                            controller.selectTab(WalletDemoTab.Credentials)
                        }, modifier = Modifier.testTag(WalletUiTestTags.ProximityCancel)) {
                            id.walt.walletdemo.compose.ui.components.WalletIcon(id.walt.walletdemo.compose.ui.components.WalletSymbol.Decline, "Close nearby sharing")
                        }
                    })
                Box(Modifier.weight(1f)) {
                WalletDemoProximityScreen(proximity.value, emptyMap(),
                    WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                    onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> },
                    onContinueAfterResponseChange = {}, onApprove = {}, onDecline = {}, onRetry = {},
                    onRemediate = { _, _ -> }, onCancel = { cancelled = true }, onDismiss = {}, onRestart = {},
                    onApprovalModeChange = { proximity.value = proximity.value.copy(approvalMode = it) },
                    onShowEngagement = { proximity.value = proximity.value.copy(preferredEngagement = it) },
                    headerOwnsClose = true)
                }
                }
                }
            }) else null)
        }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        runOnIdle { controller.dismissStatus(); controller.selectTab(WalletDemoTab.Present) }
        fun assertWholeQrVisible() {
            val qr = onNodeWithTag(WalletUiTestTags.ProximityQr).getUnclippedBoundsInRoot()
            val screen = onNodeWithTag(WalletUiTestTags.ProximityScreen).getUnclippedBoundsInRoot()
            assertTrue(qr.top >= screen.top && qr.bottom <= screen.bottom, "QR must fit inside the nearby sheet: $qr in $screen")
            assertTrue(qr.left >= screen.left && qr.right <= screen.right)
            assertEquals(qr.right - qr.left, qr.bottom - qr.top)
            val landscape = screen.right - screen.left >= Dp(600f)
            assertTrue(qr.right - qr.left >= Dp(if (landscape) 100f else 200f))
        }
        assertWholeQrVisible()
        onNodeWithTag("proximity-approval-prepare").assertIsOff().assertIsDisplayed()
        capture("qr-ask")
        onNodeWithTag("proximity-approval-prepare").performClick().assertIsOn()
        assertEquals(WalletDemoProximityApprovalMode.PrepareSharing, proximity.value.approvalMode)
        assertWholeQrVisible()
        capture("qr-prepare")
        onNodeWithTag(WalletUiTestTags.ProximityCancel).assertIsDisplayed().performClick()
        assertTrue(cancelled)
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsScreen).assertIsDisplayed()
        assertTrue(settingsOpened)
        assertTrue(!proximity.value.active)
        onAllNodesWithTag(WalletUiTestTags.ProximityQr).assertCountEquals(0)
    }

    private fun ComposeUiTest.awaitEnabledImage(path: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasTestTag(WalletUiTestTags.claimImage(path)) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.awaitTaggedNode(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.beginPinConfirmation() {
        onNodeWithTag("wallet.pinInput").performScrollTo().performTextInput("1234")
        waitForIdle()
    }

    private fun ComposeUiTest.confirmNewPin() {
        beginPinConfirmation()
        onNodeWithTag("wallet.pinConfirmationInput").performScrollTo().performTextInput("1234")
        waitForIdle()
    }

    private fun ComposeUiTest.unlockWithPin() { confirmNewPin() }

    private fun ComposeUiTest.loginWithPin() {
        onNodeWithTag("wallet.pinInput").performClick().performTextInput("1234")
        waitForIdle()
    }

    private fun ComposeUiTest.assertPresentationActionsFollowReviewContent() {
        val expectedCredentialTag = WalletUiTestTags.presentationCredential(samplePresentationCredentialOption.selection.id)
        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        onNodeWithTag(expectedCredentialTag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.presentationActions").assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.PresentationResponseProtectionSection).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationTechnicalDetailsSection).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationReaderTrustSection).assertCountEquals(0)
    }

    private fun ComposeUiTest.assertVerifierTechnicalDetailsCollapsedUntilRequested() {
        onAllNodesWithText("Client ID").assertCountEquals(0)
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.verifierTechnicalDetailsToggle").performClick()
        onNodeWithText("Client ID").performScrollTo().assertIsDisplayed()
        onNodeWithText("https://verifier.example/response").performScrollTo().assertIsDisplayed()
        onNodeWithText("state-123").performScrollTo().assertIsDisplayed()
        onNodeWithText("nonce-456").performScrollTo().assertIsDisplayed()
    }

    private fun ComposeUiTest.assertIssuerDetailsCollapsedUntilRequested() {
        onAllNodesWithText("Credential Issuer").assertCountEquals(0)
        onAllNodesWithText("https://issuer.example").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.OfferIssuerDetailsToggle)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        onNodeWithTag(WalletUiTestTags.OfferIssuerDetails).performScrollTo().assertIsDisplayed()
        onNodeWithText("Credential Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithText("https://issuer.example").performScrollTo().assertIsDisplayed().assertHasClickAction()
        onNodeWithTag(WalletUiTestTags.OfferIssuerDetailsToggle).performScrollTo().performClick()
        onAllNodesWithText("Credential Issuer").assertCountEquals(0)
        onAllNodesWithText("https://issuer.example").assertCountEquals(0)
    }

    private fun ComposeUiTest.assertRequesterDetailsCollapsedUntilRequested() {
        onAllNodesWithText("https://verifier.example").assertCountEquals(0)
        onAllNodesWithText("https://verifier.example/privacy").assertCountEquals(0)
        onAllNodesWithText("https://verifier.example/terms").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetails).performScrollTo().assertIsDisplayed()
        onNodeWithText("https://verifier.example").performScrollTo().assertIsDisplayed().assertHasClickAction()
        onNodeWithText("https://verifier.example/privacy").performScrollTo().assertIsDisplayed().assertHasClickAction()
        onNodeWithText("https://verifier.example/terms").performScrollTo().assertIsDisplayed().assertHasClickAction()
        onNodeWithTag(WalletUiTestTags.PresentationRequesterDetailsToggle).performScrollTo().performClick()
        onAllNodesWithText("https://verifier.example").assertCountEquals(0)
        onAllNodesWithText("https://verifier.example/privacy").assertCountEquals(0)
        onAllNodesWithText("https://verifier.example/terms").assertCountEquals(0)
    }

    companion object {
        // Public certificate generated and owned by walt.id for WAL-1349 qualification tests.
        // No private key or third-party fixture material is embedded here.
        private const val TestReaderCaDerBase64 =
            "MIIB5jCCAYygAwIBAgIIQAAAAAAAAAIwCgYIKoZIzj0EAwIwKDEmMCQGA1UEAwwdV0FMLTEzNDkgTG9jYWwgUmVhZGVyIFRlc3QgQ0EwHhcNMjYwOTAxMDgxNTIyWhcNMzYwODI5MDgxNTIyWjAoMSYwJAYDVQQDDB1XQUwtMTM0OSBMb2NhbCBSZWFkZXIgVGVzdCBDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABLkoxDaSw3orgCt+rU6tkUzMqbvwbGSW79yUDGFF7/RACZJuY33ELFPTTZnx6vYGuVFZ4DiMI8a7YfPwQRY4mVajgZ8wgZwwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFI7/672ZcKzVj4pzE9lFgmc6kpFvMFcGA1UdIwRQME6AFI7/672ZcKzVj4pzE9lFgmc6kpFvoSykKjAoMSYwJAYDVQQDDB1XQUwtMTM0OSBMb2NhbCBSZWFkZXIgVGVzdCBDQYIIQAAAAAAAAAIwCgYIKoZIzj0EAwIDSAAwRQIhAKrZrpvBEYeWpezCh6b48gvPzaHLXUbGfmOApayRI9MVAiBds/mL9fhhsBWtlFj2LSaMGsuPYVVIbT2d3YeWSVrJxg=="

        private const val TestReaderCaSha256 =
            "6C:5B:A7:9B:60:AF:AE:DE:74:4C:DF:E6:7F:EB:A1:51:" +
                "DE:5D:89:D7:D2:5B:20:1E:8E:94:CC:CB:AE:78:52:09"

        private val samplePortraitDisclosureValueJson by lazy {
            SyntheticCredentialImageFixtures.portraitByteArrayJson
        }


        val sampleCredential by lazy {
            WalletDemoCredential(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = "Example Issuer",
                label = "Example Credential",
                addedAt = "2026-07-09",
                credentialDataJson = """
                    {
                      "vct": "https://issuer.example/credential-types/mobile-driving-licence",
                      "given_name": "Ada",
                      "family_name": "Lovelace",
                      "valid_to": 1781654400,
                      "resident_address": {
                        "street_address": "Main Street 1",
                        "locality": "Vienna"
                      },
                      "portrait": "${SyntheticCredentialImageFixtures.portraitDataUrl}",
                      "signature_usual_mark": "${SyntheticCredentialImageFixtures.signatureDataUrl}",
                      "verification_artifact": "${SyntheticCredentialImageFixtures.verificationDocumentDataUrl}"
                    }
                """.trimIndent(),
            )
        }

        val samplePresentationPreview by lazy {
            WalletDemoPresentationPreview(
                previewHandle = WalletDemoPresentationPreviewHandle("sample-presentation-preview"),
                verifierMetadata = WalletDemoVerifierMetadata(
                    display = WalletDemoMetadataDisplay(
                        name = "Example Verifier",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = "https://verifier.example",
                    policyUri = "https://verifier.example/privacy",
                    termsOfServiceUri = "https://verifier.example/terms",
                ),
                clientId = "https://verifier.example/client",
                responseUri = "https://verifier.example/response",
                state = "state-123",
                nonce = "nonce-456",
                responseEncryption = WalletDemoResponseEncryption.Required(
                    keyManagementAlgorithm = "ECDH-ES",
                    contentEncryptionAlgorithm = "A256GCM",
                    verifierKeyId = "verifier-key-1",
                    verifierKeyThumbprint = "thumbprint-1",
                ),
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = "cred-1",
                        label = "Example Credential",
                        issuer = "Example Issuer",
                        format = "jwt_vc_json",
                        credentialDataJson = checkNotNull(sampleCredential.credentialDataJson),
                        disclosures = (1..7).map { index ->
                            WalletDemoPresentationDisclosure(
                                label = "Disclosure $index",
                                path = "$.disclosure_$index",
                                valueJson = "\"Value $index\"",
                                displayValue = "Value $index",
                                selectivelyDisclosable = true,
                            )
                        } + WalletDemoPresentationDisclosure(
                            label = "Portrait",
                            path = "$.portrait",
                            valueJson = samplePortraitDisclosureValueJson,
                            displayValue = null,
                            selectivelyDisclosable = true,
                            required = true, selectable = false,
                        ),
                    )
                ),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
                ),
            )
        }

        val compactPresentationPreview by lazy {
            samplePresentationPreview.copy(
                credentialOptions = listOf(
                    samplePresentationPreview.credentialOptions.single().copy(disclosures = emptyList()),
                ),
            )
        }

        val pathOnlyPortraitDisclosureCredentialOption by lazy {
            WalletDemoPresentationCredentialOption(
                queryId = "pid",
                credentialId = "cred-1",
                label = "Example Credential",
                issuer = "Example Issuer",
                format = "jwt_vc_json",
                credentialDataJson = checkNotNull(sampleCredential.credentialDataJson),
                disclosures = listOf(
                    WalletDemoPresentationDisclosure(
                        label = "Portrait",
                        path = "$.portrait",
                        valueJson = samplePortraitDisclosureValueJson,
                        displayValue = null,
                        selectivelyDisclosable = true,
                        required = true, selectable = false,
                    )
                ),
            )
        }

        const val sampleDidClientId = "decentralized_identifier:did:jwk:abc"
        private val samplePresentationCredentialOption: WalletDemoPresentationCredentialOption
            get() = samplePresentationPreview.credentialOptions.single()
    }
}

private class RecordingDemoBiometricAuthenticator(
    var available: Boolean = true,
) : DemoBiometricAuthenticator {
    var authenticateCalls = 0

    override fun availability() = if (available) DemoBiometricAvailability.Available else DemoBiometricAvailability.Unavailable

    override suspend fun authenticate(reason: String): DemoBiometricResult {
        authenticateCalls += 1
        return DemoBiometricResult.Succeeded
    }
}

private class RecoverableDemoPinStore : DemoPinStore {
    var isAvailable = false

    override fun hasPin(): Boolean {
        check(isAvailable) { "PIN storage is unavailable" }
        return true
    }

    override suspend fun setPin(pin: String) = Unit

    override suspend fun verifyPin(pin: String): Boolean = true

    override fun isBiometricUnlockEnabled(): Boolean = false

    override fun setBiometricUnlockEnabled(enabled: Boolean) = Unit

    override fun isBiometricSetupPending(): Boolean = false

    override fun setBiometricSetupPending(pending: Boolean) = Unit

    override fun clear() = Unit
}

internal class WalletUiTestWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    var deferredCredentials: List<WalletDemoDeferredCredential> = emptyList(),
    private val credentialsAfterReceive: List<WalletDemoCredential>? = null,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success("Presentation sent"),
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoAppTestScenarios.samplePresentationPreview,
    private val presentationPreviewResult: WalletDemoPresentationPreviewResult? = null,
    private val credentialsGate: CompletableDeferred<Unit>? = null,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val previewGate: CompletableDeferred<Unit>? = null,
    private val transactionCodeRequired: Boolean = false,
    private val batchSize: Int? = null,
    private val issuanceGrant: WalletDemoIssuanceGrant = WalletDemoIssuanceGrant.PreAuthorizedCode,
    private val offeredCredential: WalletDemoOfferedCredentialMetadata = WalletDemoOfferedCredentialMetadata(
        configurationId = "ExampleCredential",
        format = "vc+sd-jwt",
        vct = "ExampleCredential",
        doctype = null,
        display = WalletDemoMetadataDisplay(
            name = "Example Credential",
            logoUri = null,
            logoAltText = null,
        ),
        claims = emptyList(),
    ),
    var signingProtectionAvailability: WalletDemoSigningProtectionAvailability =
        WalletDemoSigningProtectionAvailability.Available,
) : DemoWallet {
    var bootstrapCalls = 0
    var receivedOfferUrl: String? = null
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var submittedRequestUrl: String? = null
    var rejectedRequestUrl: String? = null
    val deletedCredentialIds = mutableListOf<String>()
    var deleteWalletCalls = 0
    private val issuanceSources = mutableMapOf<String, String>()
    private val presentationSources = mutableMapOf<WalletDemoPresentationPreviewHandle, String>()

    override suspend fun bootstrap(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(
            keyId = "key-1",
            did = "did:key:test",
            publicJwk = """{"kty":"OKP","crv":"Ed25519","x":"test"}""",
            signingProtection = signingProtection,
        )
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability = signingProtectionAvailability

    override suspend fun listCredentials(): List<WalletDemoCredential> {
        credentialsGate?.await()
        return credentials
    }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession {
        val sessionId = "fake-issuance-session-${issuanceSources.size}"
        issuanceSources[sessionId] = offerUrl
        return WalletDemoIssuanceSession(
            id = sessionId,
            grant = issuanceGrant,
            preview = WalletDemoOfferPreview(
            issuer = WalletDemoIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                display = WalletDemoMetadataDisplay(
                    name = "Example Issuer",
                    logoUri = null,
                    logoAltText = null,
                ),
            ),
            offeredCredentials = listOf(offeredCredential),
            transactionCode = transactionCodeRequired.takeIf { it }?.let {
                WalletDemoTransactionCodeRequirement(
                    inputMode = WalletDemoTransactionCodeInputMode.Numeric,
                    length = 6,
                    description = "Enter the six-digit code",
                )
            },
            requiresIssuerAuthentication = issuanceGrant == WalletDemoIssuanceGrant.AuthorizationCode,
            batchSize = batchSize,
            ),
        )
    }

    override suspend fun beginAuthorizationIssuance(sessionId: String, credentials: List<WalletDemoCredentialSelection>): WalletDemoIssuanceAuthorization =
        WalletDemoIssuanceAuthorization("https://issuer.example/authorize")

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceOutcome {
        receivedOfferUrl = issuanceSources[sessionId]
        receivedSelections = credentials
        receiveGate?.await()
        credentialsAfterReceive?.let { this.credentials = it }
        return WalletDemoIssuanceOutcome.Stored(receivedCredentialIds)
    }

    var receivedSelections: List<WalletDemoCredentialSelection>? = null

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Failed("Authorization code is not configured")

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome {
        issuanceSources.remove(sessionId)
        return WalletDemoIssuanceOutcome.Cancelled
    }

    var continuationReads = 0
    val resumedContinuations = mutableListOf<String>()
    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> {
        continuationReads++
        return deferredCredentials
    }

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome {
        resumedContinuations += deferredCredentialId
        deferredCredentials = deferredCredentials.filterNot { it.id == deferredCredentialId }
        return WalletDemoIssuanceOutcome.Stored(receivedCredentialIds)
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
        previewedRequestUrl = requestUrl
        previewGate?.await()
        presentationSources[presentationPreview.previewHandle] = requestUrl
        return presentationPreviewResult ?: WalletDemoPresentationPreviewResult.Ready(presentationPreview)
    }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ): WalletDemoOperationResult {
        submittedRequestUrl = presentationSources[previewHandle]
        return presentationResult
    }

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult {
        rejectedRequestUrl = presentationSources[previewHandle]
        return WalletDemoOperationResult.Success("Presentation declined")
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) {
        presentationSources.remove(previewHandle)
    }

    override suspend fun deleteCredential(credentialId: String): Boolean {
        deletedCredentialIds += credentialId
        val remaining = credentials.filterNot { it.id == credentialId }
        val removed = remaining.size != credentials.size
        credentials = remaining
        return removed
    }

    override suspend fun deleteWallet() {
        deleteWalletCalls += 1
        credentials = emptyList()
    }
}

private class PreferenceProximityBackend : ProximityPresentationBackend {
    val firstClose = CompletableDeferred<Unit>()
    lateinit var latestState: MutableStateFlow<ProximityState>
    private var starts = 0
    var closedSessions = 0
        private set

    override suspend fun proximityPresentationCapabilities(configuration: ProximityConfiguration): ProximityCapabilities {
        val available = ProximityTransportCapability(implemented = true, profilePermitted = true, selected = true, runtime = ProximityRuntimeObservation.Available)
        val nfcOnly = configuration.session is ProximitySessionConfiguration.ProvisionalNfcV2
        return ProximityCapabilities(profile = configuration.profile, session = configuration.session,
            qrEngagement = available.copy(selected = !nfcOnly), nfcEngagement = available,
            bluetoothLowEnergy = available.copy(selected = !nfcOnly),
            nfcRetrieval = available.copy(selected = !nfcOnly), nfcV2Retrieval = available.copy(selected = nfcOnly),
            wifiAwareRetrieval = available.copy(selected = !nfcOnly))
    }

    override suspend fun startProximityPresentation(configuration: ProximityConfiguration): ProximitySession {
        val first = starts++ == 0
        return object : ProximitySession {
            override val state = MutableStateFlow<ProximityState>(ProximityState.EngagementReady(
                if (configuration.session is ProximitySessionConfiguration.ProvisionalNfcV2) listOf(ProximityEngagement.Nfc)
                else listOf(ProximityEngagement.Qr("mdoc:preference-$starts"), ProximityEngagement.Nfc)))
                .also { latestState = it }
            override suspend fun dispatch(action: ProximityAction): ProximityActionResult = ProximityActionResult.Accepted
            override suspend fun close() {
                if (first) firstClose.await()
                closedSessions += 1
            }
        }
    }
}
