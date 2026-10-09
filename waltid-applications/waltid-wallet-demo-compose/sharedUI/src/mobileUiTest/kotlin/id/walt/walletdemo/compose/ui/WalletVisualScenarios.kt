package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.DemoBiometricAvailability
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceProblem
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletFooter
import id.walt.walletdemo.compose.ui.components.WalletIcon
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.logic.WalletDemoProximityUiState
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityState
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.OfferedCredentialDetails
import id.walt.walletdemo.compose.ui.screens.ReceiveTab
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import id.walt.walletdemo.compose.ui.screens.WalletHeader
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.screens.WalletScanScreen
import id.walt.walletdemo.compose.ui.screens.CredentialsTab
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.logic.WalletOperationState

/** Content and readiness are shared; the platform adapter owns the renderer and baseline path. */
@OptIn(ExperimentalTestApi::class)
internal class WalletVisualScenarios(
    private val test: ComposeUiTest,
    private val captureImage: (String) -> Unit,
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    private fun capture(id: String) {
        // Capture the settled Compose state after interactions and navigation.
        // Advancing virtual time keeps this independent of host rendering speed.
        test.mainClock.advanceTimeBy(1_000)
        test.waitForIdle()
        captureImage(id)
    }

    private fun content(body: @Composable () -> Unit) = test.setContent {
        platformTheme {
            WalletDemoTheme {
                Surface(modifier = Modifier.fillMaxSize(), content = body)
            }
        }
    }

    fun controls(rtl: Boolean = false) = with(test) {
        content {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
                if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                id.walt.walletdemo.compose.ui.components.WalletControlsPreview()
            }
        }
        onNodeWithTag("preview.copies.less").assertIsDisplayed().assertIsNotEnabled()
        onNodeWithTag("preview.copies.more").assertIsDisplayed().assertIsEnabled()
        capture(if (rtl) "components.controls.rtl" else "components.controls.default")
        onNodeWithTag("preview.copies.more").performClick()
        onNodeWithText("Copies: 2").assertIsDisplayed()
    }

    fun externalReceiving(unavailable: Boolean = false) = with(test) {
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), id.walt.walletdemo.compose.logic.InMemoryDemoPinStore())
        val flow = if (unavailable) id.walt.walletdemo.compose.logic.WalletExternalFlow.UnavailableCallback("openid://callback")
            else id.walt.walletdemo.compose.logic.WalletExternalFlow.Active("openid-credential-offer://fixture", id.walt.walletdemo.compose.logic.WalletExternalFlow.Kind.Offer)
        val state = WalletDemoUiState(access = id.walt.walletdemo.compose.logic.WalletAccessState(auth = id.walt.walletdemo.compose.logic.WalletAuthState.Unlocked),
            session = WalletVisualFixtures.partialResult.session, selectedTab = WalletDemoTab.Receive, externalFlow = flow,
            offerPreview = if (unavailable) null else WalletVisualFixtures.offer, issuanceCopyCounts = WalletVisualFixtures.copies)
        content {
            WalletReviewHost(WalletReviewPresentation.Sheet, true, {}) {
                id.walt.walletdemo.compose.ui.screens.WalletExternalFlowScreen(controller, state, {}, onOpenInApp = {})
            }
        }
        onNodeWithTag("wallet.external.close").assertIsDisplayed().assertIsEnabled()
        onNodeWithTag("wallet.external.openInApp").assertIsDisplayed().assertIsEnabled()
        onAllNodesWithTag(WalletUiTestTags.OfferInput).assertCountEquals(0)
        if (unavailable) onNodeWithTag("wallet.external.unavailable").assertIsDisplayed()
        else onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed().assertIsEnabled()
        capture(if (unavailable) "external.callback.unavailable" else "external.receiving.review")
    }

    fun pin(state: String) = with(test) {
        val gate = kotlinx.coroutines.CompletableDeferred<id.walt.walletdemo.compose.logic.DemoBiometricResult>()
        val biometrics = object : id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator {
            override fun availability() = if (state in setOf("confirmation", "biometric_prompt", "compact_dark_large_text")) DemoBiometricAvailability.Available else DemoBiometricAvailability.Unavailable
            override suspend fun authenticate(reason: String) = gate.await()
        }
        val memory = id.walt.walletdemo.compose.logic.InMemoryDemoPinStore()
        val store = if (state == "unlock") object : id.walt.walletdemo.compose.logic.DemoPinStore by memory {
            override fun hasPin() = true
        } else memory
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), store, biometrics)
        if (state == "unlock") controller.updatePin("12")
        else if (state != "setup") {
            controller.updatePin("1234")
            controller.submitPin()
            if (state == "mismatch") controller.updatePinConfirmation("4321")
            if (state == "compact_dark_large_text") controller.updatePinConfirmation("123")
            if (state == "biometric_prompt") controller.updatePinConfirmation("1234")
        }
        if (state == "biometric_prompt") {
            waitUntil { controller.state.value.auth is id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup }
            content { id.walt.walletdemo.compose.ui.screens.BiometricSetupScreen(
                controller.state.value.auth as id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup,
                busy = true, availability = DemoBiometricAvailability.Available, onRetry = {}, onContinue = {}) }
            onNodeWithTag(WalletUiTestTags.BiometricSetupRetry).assertIsNotEnabled()
            onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).assertIsNotEnabled()
            capture("onboarding.pin.$state")
            gate.complete(id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed)
            return@with
        }
        val access = controller.state.value.access
        content {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (state == "rtl") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                LocalWalletVisualPreferences provides WalletVisualPreferences(reduceMotion = state == "rtl"),
            ) { id.walt.walletdemo.compose.ui.screens.PinScreen(controller, access) }
        }
        onAllNodesWithTag("wallet.pinBiometricToggle").assertCountEquals(0)
        val confirming = state !in setOf("setup", "unlock")
        onNodeWithTag(if (confirming) WalletUiTestTags.PinConfirmationInput else WalletUiTestTags.PinInput).assertIsDisplayed()
        onAllNodesWithTag(if (confirming) WalletUiTestTags.PinInput else WalletUiTestTags.PinConfirmationInput).assertCountEquals(0)
        when (state) {
            "unlock" -> onNodeWithTag(WalletUiTestTags.PinClearButton).assertIsEnabled()
            "mismatch" -> onNodeWithText("PIN confirmation does not match").assertIsDisplayed()
        }
        onAllNodesWithTag(WalletUiTestTags.PinSubmitButton).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PinClearButton).assertIsDisplayed()
        capture("onboarding.pin.$state")
        gate.complete(id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed)
    }

    fun walletAccess(state: String) = with(test) {
        val controller = id.walt.walletdemo.compose.logic.WalletDemoController(WalletUiTestWallet(), id.walt.walletdemo.compose.logic.InMemoryDemoPinStore())
        val entry = id.walt.walletdemo.compose.logic.WalletAuthState.Login(
            error = if (state == "rejected") "Wrong PIN" else null,
            biometricPromptConsumed = true,
            biometricOutcome = when (state) {
                "biometric_fallback" -> id.walt.walletdemo.compose.logic.DemoBiometricResult.Failed
                "biometric_lockout" -> id.walt.walletdemo.compose.logic.DemoBiometricResult.LockedOut
                else -> null
            })
        val unlocking = state in setOf("rejected", "biometric_fallback", "biometric_lockout", "biometric_unavailable")
        val access = id.walt.walletdemo.compose.logic.WalletAccessState(
            auth = if (unlocking) entry else id.walt.walletdemo.compose.logic.WalletAuthState.Unlocked,
            biometricAvailability = when (state) {
                "biometric_fallback" -> DemoBiometricAvailability.Available
                "biometric_lockout" -> DemoBiometricAvailability.LockedOut
                else -> DemoBiometricAvailability.Unavailable
            }, biometricKind = if (state == "biometric_unavailable") id.walt.walletdemo.compose.logic.DemoBiometricKind.FaceId else id.walt.walletdemo.compose.logic.DemoBiometricKind.Generic,
            biometricEnabled = state.startsWith("biometric"),
            pinChange = when (state) {
                "current_pin" -> id.walt.walletdemo.compose.logic.WalletPinChange.Current()
                "new_pin" -> id.walt.walletdemo.compose.logic.WalletPinChange.NewPin()
                "confirmation", "save_failure" -> id.walt.walletdemo.compose.logic.WalletPinChange.NewPin(
                    id.walt.walletdemo.compose.logic.WalletAuthState.Setup(pin = "5678", confirmation = if (state == "save_failure") "5678" else "",
                        step = id.walt.walletdemo.compose.logic.PinSetupStep.Confirm))
                else -> null
            },
            operation = if (state == "save_failure") id.walt.walletdemo.compose.logic.WalletAccessOperation.RetryPin("PIN could not be saved. Try again.")
                else id.walt.walletdemo.compose.logic.WalletAccessOperation.Idle,
            settingsNotice = if (state == "pin_changed") id.walt.walletdemo.compose.logic.WalletAccessNotice("PIN changed",
                id.walt.walletdemo.compose.logic.WalletAccessNotice.Kind.Success) else null)
        content {
            if (unlocking) id.walt.walletdemo.compose.ui.screens.PinScreen(controller, access)
            else Column {
                if (access.pinChange == null) WalletScreenHeader("Wallet access")
                id.walt.walletdemo.compose.ui.screens.WalletAccessSettingsScreen(controller, access)
            }
        }
        when (state) {
            "default", "pin_changed" -> onNodeWithTag(WalletUiTestTags.SettingsChangePin).assertIsDisplayed()
            "confirmation", "save_failure" -> onNodeWithTag(WalletUiTestTags.PinConfirmationInput).assertIsDisplayed()
            else -> onNodeWithTag(WalletUiTestTags.PinInput).assertIsDisplayed()
        }
        if (state == "save_failure") onNodeWithTag(WalletUiTestTags.PinSubmitButton).assertIsDisplayed().assertIsEnabled()
        if (state == "biometric_fallback") onNodeWithTag(WalletUiTestTags.PinBiometricButton).assertIsDisplayed()
        if (state == "biometric_unavailable") onNodeWithTag("wallet.biometricOpenSettings").assertIsDisplayed()
        capture(if (unlocking) "access.unlock.$state" else "settings.access.$state")
    }

    fun keySetup(page: String) = with(test) {
        val unavailable = page == "approval_unavailable"
        val setup = WalletVisualFixtures.keySetup
        val approval = setup.options.first().approval
        content {
            id.walt.walletdemo.compose.ui.screens.IdentitySetupScreen(if (unavailable) setup.copy(options = setup.options.filter { it.approval.id != approval.id }, preferredApproval = approval) else setup, null,
                onChoose = {}, onResume = {}, onCancel = {}, onRefresh = {},
                biometricAvailability = if (unavailable) DemoBiometricAvailability.Unavailable else DemoBiometricAvailability.Available,
                biometricKind = id.walt.walletdemo.compose.logic.DemoBiometricKind.FaceId)
        }
        if (page != "summary" && !unavailable) {
            onNodeWithTag("wallet.keySetupEdit.${page.replaceFirstChar { it.uppercase() }}").performClick()
            onNodeWithText("Done").assertIsDisplayed()
        } else {
            onNodeWithText("Create signing key").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Recovery").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Storage").assertIsDisplayed()
            onNodeWithTag("wallet.keySetupEdit.Approval").assertIsDisplayed()
        }
        if (unavailable) {
            onNodeWithTag(WalletUiTestTags.KeySetupContinue).assertIsNotEnabled()
            onNodeWithTag("wallet.biometricOpenSettings").assertIsDisplayed()
        }
        capture("onboarding.key.$page")
    }

    fun biometricSetup(unavailable: Boolean = false) = with(test) {
        content {
            id.walt.walletdemo.compose.ui.screens.BiometricSetupScreen(
                id.walt.walletdemo.compose.logic.WalletAuthState.BiometricSetup(
                    if (unavailable) id.walt.walletdemo.compose.logic.DemoBiometricResult.Unavailable
                    else id.walt.walletdemo.compose.logic.DemoBiometricResult.Cancelled),
                busy = false, availability = if (unavailable) DemoBiometricAvailability.Unavailable else DemoBiometricAvailability.Available, onRetry = {}, onContinue = {})
        }
        onNodeWithTag(WalletUiTestTags.BiometricSetupContinue).assertIsDisplayed().assertIsEnabled()
        if (unavailable) onAllNodesWithTag(WalletUiTestTags.BiometricSetupRetry).assertCountEquals(0)
        else onNodeWithTag(WalletUiTestTags.BiometricSetupRetry).assertIsDisplayed().assertIsEnabled()
        capture("onboarding.biometric.${if (unavailable) "unavailable" else "cancelled"}")
    }

    fun account(state: String) = with(test) {
        content {
            id.walt.walletdemo.compose.ui.screens.AccountAuthScreen(
                isBusy = state == "busy", error = if (state == "expired") "Your session has expired. Sign in again to continue." else null,
                onLogin = { _, _ -> }, onRegister = { _, _ -> })
        }
        onNodeWithTag(WalletUiTestTags.AccountEmailInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.AccountPasswordInput).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.AccountLoginButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.AccountRegisterButton).assertIsNotEnabled()
        if (state == "expired") onNodeWithTag(WalletUiTestTags.AccountAuthError).assertIsDisplayed()
        capture("account.$state")
    }

    fun readerTrustImport() = with(test) {
        content { ReaderTrustImportReview(WalletVisualFixtures.readerTrustImport, {}, {}, WalletReviewPresentation.FullScreen) }
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportConfirm).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.SettingsReaderTrustImportCancel).assertIsDisplayed()
        onNodeWithText("Example Reader CA", substring = false).assertIsDisplayed()
        capture("settings.reader.import_review")
    }

    fun settingsRoot(destination: String = "root", reviewEnabled: Boolean = true) = with(test) {
        val trust = id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController(
            id.walt.walletdemo.compose.logic.InMemoryDemoReaderTrustSettingsStore(
                id.walt.wallet2.mobile.ProximityReaderTrustSettings(if (destination == "reader_required")
                    id.walt.wallet2.mobile.ProximityReaderPolicy.RequireTrusted else id.walt.wallet2.mobile.ProximityReaderPolicy.AllowAnonymousOrUntrusted)),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            workerDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        content {
            SettingsScreen(
                state = WalletDemoUiState(showDcApiPresentationPreview = reviewEnabled),
                onShowDcApiPresentationPreviewChange = {}, onProximityTransportProfileChange = {},
                onBack = {}, onIdentityAction = {}, onRefreshIdentityDetails = {}, onLock = {}, onResetWallet = {},
                onRequestSigningProtectionChange = {}, onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {}, onProximityApprovalModeChange = {},
                readerTrustSettingsContent = { DemoReaderTrustSettings(trust) },
            )
        }
        onNodeWithTag(WalletUiTestTags.SettingsSigningKey).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsDigitalCredentialsApi).assertIsDisplayed()
        when (destination) {
            "dc_api" -> {
                onNodeWithTag(WalletUiTestTags.SettingsDigitalCredentialsApi).performClick()
                val toggle = onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview)
                if (reviewEnabled) toggle.assertIsOn() else toggle.assertIsOff()
            }
            "nearby", "connection", "reader", "reader_required" -> {
                onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation).performClick()
                if (destination == "connection") onNodeWithTag(WalletUiTestTags.SettingsConnectionMethod).performClick()
                if (destination.startsWith("reader")) onNodeWithTag(WalletUiTestTags.SettingsReaderAuthentication).performClick()
            }
            "technical" -> onNodeWithTag(WalletUiTestTags.SettingsTechnicalDetails).performClick()
        }
        val stateId = when (destination) {
            "dc_api" -> if (reviewEnabled) "enabled" else "disabled"
            "technical" -> "unavailable"
            else -> "default"
        }
        capture("settings.$destination.$stateId")
    }

    fun walletHome(empty: Boolean = false) = with(test) {
        val ready = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val state = WalletVisualFixtures.partialResult.copy(
            selectedTab = WalletDemoTab.Credentials, operation = WalletOperationState.Idle,
            session = ready.copy(credentials = if (empty) emptyList() else ready.credentials),
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = {}, onScan = {}, onShareNearby = {})
                CredentialsTab(state.session, modifier = Modifier.weight(1f))
                WalletFooter(feedback = { StatusCard(state, {}, {}) })
            }
        }
        if (empty) onNodeWithTag(WalletUiTestTags.CredentialsEmpty).assertIsDisplayed()
        else {
            waitUntil { onAllNodesWithTag(WalletUiTestTags.credentialCard(ready.credentials.single().id)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(WalletUiTestTags.credentialCard(ready.credentials.single().id)).assertIsDisplayed()
        }
        onNodeWithTag(WalletUiTestTags.ScanButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityStartButton).assertIsDisplayed()
        capture(if (empty) "wallet.home.empty" else "wallet.home.credential")
    }

    fun scanner(state: String) = with(test) {
        val input = when (state) { "link" -> "https://example.test/request"; "unsupported" -> "FIDO:/0123456789"; else -> "" }
        content { WalletScanScreen(onBack = {}, onOpen = { _, _ -> }, initialInput = input) }
        if (state == "link") {
            onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Receive credentials").assertDoesNotExist()
            onNodeWithText("Share credentials").assertDoesNotExist()
        } else if (state == "empty") {
            onAllNodesWithTag(WalletUiTestTags.ScanInput).assertCountEquals(0)
            onAllNodesWithTag(WalletUiTestTags.ScanContinue).assertCountEquals(0)
            onNodeWithTag("wallet.scanMode").assertIsDisplayed().assertIsEnabled()
        } else onNodeWithTag(WalletUiTestTags.ScanContinue).assertIsDisplayed().assertIsNotEnabled()
        capture("wallet.scan.$state")
    }

    fun singleOffer() = with(test) {
        val credential = WalletVisualFixtures.offer.offeredCredentials.first()
        val state = WalletDemoUiState(
            offerPreview = WalletVisualFixtures.offer.copy(offeredCredentials = listOf(credential)),
            issuanceCopyCounts = mapOf(credential.configurationId to 1))
        content {
            ReceiveTab(state, state.requestDrafts, onTxCodeChange = {},
                onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {},
                onDeclineOffer = {}, onResumeDeferred = {}, onDone = {}, onRefresh = {})
        }
        onNodeWithTag("issuance-select-${credential.configurationId}").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed().assertIsEnabled()
        capture("batch.offer.single_full_art")
    }

    fun credentialDetails() = with(test) {
        val details = WalletVisualFixtures.credentialDetails
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CredentialDetailsContent(details)
            }
        }
        onNodeWithTag(WalletUiTestTags.credentialDetails(details.summary.id)).assertExists()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        capture("credential.details.identity")
        onNodeWithText("Vienna").performScrollTo().assertIsDisplayed()
        capture("credential.details.nested")
    }

    fun localizedCredentialDetails() = with(test) {
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CredentialDetailsContent(WalletVisualFixtures.localizedCredentialDetails)
            }
        }
        onNodeWithText("Familienname").assertIsDisplayed()
        onNodeWithText("Vorname").assertIsDisplayed()
        onNodeWithText("Name im Namensraum").assertIsDisplayed()
        onNodeWithText("Straße").assertIsDisplayed()
        capture("credential.details.localized_metadata")
    }

    fun batchOffer(noneSelected: Boolean = false, compact: Boolean = false) = with(test) {
        val copies = WalletVisualFixtures.copies.mapValues { (_, count) -> if (noneSelected) 0 else count }
        val state = WalletDemoUiState(offerPreview = WalletVisualFixtures.offer, issuanceCopyCounts = copies)
        content {
            ReceiveTab(
                state = state, requestDrafts = state.requestDrafts,
                onTxCodeChange = {}, onCopiesChange = { _, _ -> },
                onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {}, onResumeDeferred = {}, onDone = {}, onRefresh = {},
            )
        }
        onNodeWithTag(WalletUiTestTags.OfferIssuerSection).assertIsDisplayed()
        val action = onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsDisplayed()
        val primaryBounds = action.getUnclippedBoundsInRoot()
        val secondaryBounds = onNodeWithTag(WalletUiTestTags.OfferDeclineButton).getUnclippedBoundsInRoot()
        val rootBounds = onRoot().getUnclippedBoundsInRoot()
        val availableWidth = rootBounds.right - rootBounds.left - 40.dp
        val requiredWidth = primaryBounds.right - primaryBounds.left + secondaryBounds.right - secondaryBounds.left + 8.dp
        if (requiredWidth <= availableWidth) {
            kotlin.test.assertEquals(secondaryBounds.top, primaryBounds.top,
                "Actions that fit must share one row, including at large text sizes")
        } else {
            kotlin.test.assertTrue(primaryBounds.top >= secondaryBounds.bottom,
                "Actions that need more space must remain separately reachable")
        }
        if (noneSelected) action.assertIsNotEnabled() else action.assertIsEnabled()
        if (compact) {
            onNodeWithTag("issuance-select-resident-card").assertIsDisplayed()
            capture("batch.offer.compact_dark_large_text")
            onNodeWithText("Selected: 2 · Copies: 3").performScrollTo().assertIsDisplayed()
            onNodeWithTag("issuance-more-library-card").assertIsDisplayed()
            action.assertIsDisplayed().assertIsEnabled()
            capture("batch.offer.compact_dark_large_text.last_target")
            return@with
        }
        val scenario = if (noneSelected) "none_selected" else "two_targets_three_copies"
        onNodeWithTag("issuance-select-library-card").assertIsDisplayed()
        if (!noneSelected) onNodeWithText("Copies: 1").assertIsDisplayed()
        capture("batch.offer.$scenario")
    }

    fun offerDefinitions() = with(test) {
        val offer = WalletVisualFixtures.offer
        content {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                OfferedCredentialDetails(offer.offeredCredentials.first(), offer.issuer.display!!.name!!, offer.issuer.credentialIssuer)
            }
        }
        onNodeWithText("Values have not been received yet.").assertIsDisplayed()
        onNodeWithText("Given name").assertIsDisplayed()
        onNodeWithText("Family name").assertIsDisplayed()
        onNodeWithText("Ada").assertDoesNotExist()
        capture("batch.offer.definitions")
    }

    fun credentialImages() = with(test) {
        val images = WalletVisualImages()
        val details = WalletVisualFixtures.credentialWithImages
        images.install()
        try {
            content {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    CredentialDetailsContent(details)
                }
            }
            listOf("portrait" to "portrait", "signature_usual_mark" to "signature").forEach { (path, image) ->
                waitUntil { onAllNodesWithTag(WalletUiTestTags.claimImage(path)).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag(WalletUiTestTags.claimImage(path)).performScrollTo().assertIsDisplayed()
                waitUntil { images.isReady(image) }
                // Both thumbnails are visible together; neither may be captured while still loading.
                waitUntil {
                    listOf("portrait", "signature_usual_mark").all { visiblePath ->
                        onAllNodes(hasTestTag(WalletUiTestTags.claimImage(visiblePath)) and isEnabled())
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                }
                waitForIdle()
                capture("credential.media.$image")
            }
        } finally {
            images.close()
        }
    }

    fun partialBatchResult(status: WalletDemoContinuationStatus = WalletDemoContinuationStatus.AwaitingIssuer, failure: Boolean = false) = with(test) {
        val base = WalletVisualFixtures.partialResult
        val state = base.copy(
            deferredCredentials = if (failure) emptyList() else base.deferredCredentials.map { it.copy(status = status) },
            issuanceReceipt = base.issuanceReceipt?.let { if (failure) it.copy(pendingIds = emptySet(),
                problem = WalletDemoIssuanceProblem("The issuer could not finish this request.", failedTargetCount = 1, notAttemptedTargetCount = 2)) else it },
            operation = if (failure) WalletOperationState.Failed("The issuer could not finish this request.", WalletDemoTab.Receive) else base.operation,
        )
        content {
            Column(Modifier.fillMaxSize()) {
                WalletHeader(state, onSettings = null, onClose = {}, title = "Receiving result")
                ReceiveTab(state, state.requestDrafts, onTxCodeChange = {},
                    onCopiesChange = { _, _ -> }, onPreviewOffer = {}, onAcceptOffer = {}, onDeclineOffer = {},
                    onResumeDeferred = {}, onDone = {}, onRefresh = {}, modifier = Modifier.weight(1f),
                    feedback = { StatusCard(state, {}, {}) })
            }
        }
        if (failure) {
            onNodeWithText("Failed targets: 1").performScrollTo().assertIsDisplayed()
            onNodeWithText("Not attempted: 2").assertIsDisplayed()
        } else if (status.canResume) {
            onNodeWithText(if (status == WalletDemoContinuationStatus.AwaitingLocalSave) "Finish saving" else "Check with issuer")
                .performScrollTo().assertIsDisplayed()
        } else {
            onNodeWithTag("issuance-resume-visual-deferred-library").assertDoesNotExist()
            onNodeWithTag("issuance-refresh").assertIsDisplayed()
        }
        onNodeWithTag(WalletUiTestTags.OfferInput).assertDoesNotExist()
        onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertExists()
        val id = if (failure) "partial_failure" else when (status) {
            WalletDemoContinuationStatus.AwaitingLocalSave -> "local_save_pending"
            WalletDemoContinuationStatus.RemoteOutcomeUncertain -> "remote_uncertain"
            WalletDemoContinuationStatus.StorageOutcomeUncertain -> "storage_uncertain"
            else -> "saved_and_deferred"
        }
        capture("batch.result.$id")
    }

    fun nearbyState(kind: String) = with(test) {
        val fixtures = WalletVisualProximityFixtures
        val state = fixtures.state(kind)
        content { nearbyVisualHost {
            if (kind == "receipt") ReviewScaffold(actions = {
                ProximityOutcomeActions(state, { it }, {}, {}, {}, {})
            }) {
                ProximityTerminalContent("Presentation complete", "The approved credential data was sent to the reader.") {
                    ProximitySharingReceiptContent(fixtures.review, fixtures.submission,
                        id.walt.wallet2.mobile.ProximityApprovalTiming.BeforeConnection, fixtures.completedAt, fixtures.details)
                }
            } else WalletDemoProximityScreen(state, fixtures.details,
                WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> }, onContinueAfterResponseChange = {},
                onApprove = {}, onDecline = {}, onRetry = {}, onRemediate = { _, _ -> },
                onCancel = {}, onDismiss = {}, onRestart = {}, headerOwnsClose = true)
        } }
        when (kind) {
            "permission" -> onNode(hasText("Allow Bluetooth access") and hasClickAction()).assertIsDisplayed()
            "expired" -> { onNodeWithTag(WalletUiTestTags.ProximityRetry).assertIsDisplayed(); onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed() }
            "receipt" -> { onNodeWithTag(WalletUiTestTags.ProximityDone).assertIsDisplayed(); onNodeWithText("City service desk").assertExists() }
            "review" -> onNodeWithTag(WalletUiTestTags.ProximityReview).assertExists()
        }
        capture("nearby.$kind")
        if (kind == "review") {
            onNodeWithTag(WalletUiTestTags.proximityElement(0, "org.iso.18013.5.1", "given_name")).performScrollTo().assertIsDisplayed()
            onNodeWithText("Reader intends to retain this data").performScrollTo().assertIsDisplayed()
            onNodeWithText("Alex").assertIsDisplayed()
            capture("nearby.review.disclosures")
        }
    }

    fun nearbyReady() = with(test) {
        content { nearbyVisualHost {
            WalletDemoProximityScreen(
                state = WalletDemoProximityUiState(active = true, sessionState = ProximityState.EngagementReady(
                    listOf(ProximityEngagement.Qr(WalletVisualFixtures.nearbyQrPayload)))),
                credentialDetailsById = emptyMap(),
                hostActions = WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
                onSelectCredential = { _, _ -> }, onToggleElement = { _, _ -> }, onContinueAfterResponseChange = {},
                onApprove = {}, onDecline = {}, onRetry = {}, onRemediate = { _, _ -> },
                onCancel = {}, onDismiss = {}, onRestart = {}, headerOwnsClose = true,
            )
        } }
        onNodeWithTag(WalletUiTestTags.ProximityQr).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.ProximityCancel).assertIsDisplayed()
        capture("nearby.ready.qr")
    }

    @Composable
    private fun nearbyVisualHost(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            WalletScreenHeader("Share nearby", leading = {
                IconButton({}, modifier = Modifier.testTag(WalletUiTestTags.ProximityCancel)) {
                    WalletIcon(WalletSymbol.Decline, "Close nearby sharing")
                }
            })
            Box(Modifier.weight(1f)) { content() }
        }
    }

    fun providerSharingReview(compact: Boolean = false) = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.providerReview, title = "Share documents",
                onSubmit = {}, onCancel = {}, onBackAtRoot = {}, presentation = WalletReviewPresentation.Sheet)
        }
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationVerifierSection).performScrollTo().assertIsDisplayed()
        capture(if (compact) "sharing.provider.compact_dark_large_text" else "sharing.provider.review")
    }

    fun sharingInformation(alternative: Boolean = false, multiple: Boolean = false) = with(test) {
        val options = WalletVisualFixtures.informationCredentials.take(if (multiple) 3 else 2)
        val review = id.walt.walletdemo.compose.logic.WalletDemoSharingReview(
            id.walt.walletdemo.compose.logic.WalletDemoSharingRequest(
                id.walt.walletdemo.compose.logic.WalletDemoSharingRequester(
                    fallbackName = "https://verifier.example", verifiedOrigin = "https://verifier.example")), options,
            options.map { it.queryId }.distinct().map {
                id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement(listOf(listOf(it)))
            })
        content {
            WalletDemoSharingReviewScreen(review = review, title = "Share credentials", compact = false,
                onSubmit = {}, onCancel = {}, onBackAtRoot = {}, presentation = WalletReviewPresentation.Sheet)
        }
        if (alternative) onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(options[1].selection.id))
            .performScrollTo().performClick()
        onNodeWithText(if (multiple) "MEM-1234" else if (alternative) "Lovelace" else "Ada").performScrollTo().assertIsDisplayed()
        if (alternative) onNodeWithText("Always included by this credential").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        capture("sharing.information." + if (multiple) "multiple" else if (alternative) "alternative" else "selected")
    }

    fun providerOfferReview() = with(test) {
        content {
            WalletDemoOfferCreateScreen(WalletDemoOfferCreateUiState.Review(WalletVisualFixtures.offer),
                onAccept = { _, _ -> }, onDecline = {}, onDismiss = {}, onCancelAuthorization = {},
                presentation = WalletReviewPresentation.Sheet)
        }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsEnabled().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.OfferCredentialsSection).performScrollTo().assertIsDisplayed()
        capture("receiving.provider.review")
    }

    fun providerReceivingState(kind: String) = with(test) {
        val base = WalletVisualFixtures.partialResult
        val state = when (kind) {
            "preparing" -> WalletDemoOfferCreateUiState.Loading
            "authorization" -> WalletDemoOfferCreateUiState.WaitingForAuthorization()
            "failure" -> WalletDemoOfferCreateUiState.Failure("The credential offer could not be verified.")
            "partial_result" -> WalletDemoOfferCreateUiState.Receipt(
                receipt = requireNotNull(base.issuanceReceipt).copy(problem = WalletDemoIssuanceProblem(
                    "The issuer could not finish this request.", failedTargetCount = 1, notAttemptedTargetCount = 2)),
                saved = (base.session as WalletSessionState.Ready).credentials, pending = base.deferredCredentials)
            else -> error("Unknown receiving state: $kind")
        }
        content {
            WalletDemoOfferCreateScreen(state, onAccept = { _, _ -> }, onDecline = {}, onDismiss = {},
                onCancelAuthorization = {}, presentation = WalletReviewPresentation.Sheet)
        }
        when (state) {
            is WalletDemoOfferCreateUiState.Receipt -> {
                onNodeWithTag("wallet.provider.done").assertIsDisplayed().assertIsEnabled()
                onNodeWithTag("issuance-saved-${WalletVisualFixtures.credentialSummary.id}").assertIsDisplayed()
                onNodeWithText("Check with issuer").assertIsDisplayed()
                onNodeWithText("Not attempted: 2").performScrollTo().assertIsDisplayed()
                onNodeWithTag("wallet.provider.done").assertIsDisplayed()
            }
            is WalletDemoOfferCreateUiState.Failure -> onNodeWithContentDescription("Close request").assertIsDisplayed()
            else -> onNodeWithText("Cancel").assertIsDisplayed()
        }
        capture("receiving.provider.$kind")
    }

    fun providerSharingStatus(failure: Boolean = false) = with(test) {
        content {
            WalletProviderStatusScreen(title = if (failure) "Unable to share" else "Preparing request…",
                message = if (failure) "The request could not be verified." else null, onClose = {}, onDismiss = {})
        }
        onNodeWithContentDescription("Close request").assertIsDisplayed()
        capture(if (failure) "sharing.provider.failure" else "sharing.provider.preparing")
    }

    fun paymentState(blocked: Boolean) = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.paymentReview, title = "Payment",
                onSubmit = {}, onCancel = {}, compact = false, preparePaymentConsent = {
                    if (blocked) error("Required issuer payment labels are missing.")
                    else kotlinx.coroutines.awaitCancellation()
                })
        }
        onNodeWithTag(if (blocked) "payment-consent-blocked" else "payment-consent-loading").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsNotEnabled().assertIsDisplayed()
        capture(if (blocked) "payment.blocked" else "payment.loading")
    }

    fun localizedPayment() = with(test) {
        content {
            WalletDemoSharingReviewScreen(review = WalletVisualFixtures.paymentReview, title = "Payment",
                onSubmit = {}, onCancel = {}, compact = false,
                preparePaymentConsent = { WalletVisualFixtures.localizedPayment })
        }
        onNodeWithText("11.56 EUR").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag("payment-unsigned-warning").assertDoesNotExist()
        capture("payment.localized.compact_large_text")
    }

    fun paymentReview(sheet: Boolean = false) = with(test) {
        val consent = WalletVisualFixtures.payment
        content {
            WalletDemoSharingReviewScreen(
                review = WalletVisualFixtures.paymentReview,
                title = "Payment", compact = false, onSubmit = {}, onCancel = {}, onBackAtRoot = {},
                presentation = if (sheet) WalletReviewPresentation.Sheet else WalletReviewPresentation.FullScreen,
                preparePaymentConsent = { consent },
            )
        }
        onNodeWithText("Confirm payment").performScrollTo().assertIsDisplayed()
        onNodeWithText("11.56 EUR").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsDisplayed()
        onNodeWithText("bound-but-hidden").assertDoesNotExist()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.main")
        onNodeWithTag("payment-details-toggle").performScrollTo().performClick()
        onNodeWithText("example-transaction-001").performScrollTo().assertIsDisplayed()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.details")
        onNodeWithTag("review-information-to-share").performScrollTo().assertIsDisplayed()
        onNodeWithText("Pay €11.56").assertIsEnabled().assertIsDisplayed()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.requested_data")
        onNodeWithText("Pay €11.56").performClick()
        onNodeWithTag("payment-unsigned-confirm").assertIsDisplayed()
        capture("${if (sheet) "payment.sheet" else "payment.mixed_credentials"}.unsigned_confirmation")
    }
}
