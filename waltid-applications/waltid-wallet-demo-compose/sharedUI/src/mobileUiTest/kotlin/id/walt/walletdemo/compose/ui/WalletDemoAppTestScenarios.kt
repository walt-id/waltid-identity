package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import id.walt.walletdemo.compose.logic.DemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.DemoBiometricResult
import id.walt.walletdemo.compose.logic.DemoPinStore
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.WalletDemoBootstrapResult
import id.walt.walletdemo.compose.logic.WalletAuthState
import id.walt.walletdemo.compose.logic.WalletDemoController
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WalletDemoAppTestScenarios {

    fun pinStorageFailureStaysLockedUntilRetrySucceeds() = runComposeUiTest {
        val pinStore = RecoverableDemoPinStore()
        val controller = WalletDemoController(FakeDemoWallet(), pinStore)

        setContent { WalletDemoApp(controller) }

        onNodeWithText("PIN storage unavailable").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinInput").assertCountEquals(0)

        pinStore.isAvailable = true
        onNodeWithTag("wallet.pinStorageRetryButton").performClick()
        waitForIdle()

        onNodeWithText("Enter your PIN").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinConfirmationInput").assertCountEquals(0)
    }

    fun pinSetupShowsDisabledBiometricToggleWhenUnavailable() = runComposeUiTest {
        val controller = WalletDemoController(FakeDemoWallet(), InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }

        onNodeWithText("Create a PIN").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PinBiometricToggle)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Biometrics are not available on this device.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun pinScreenRefreshesBiometricAvailabilityWhenItBecomesAvailable() = runComposeUiTest {
        val biometrics = RecordingDemoBiometricAuthenticator(available = false)
        val controller = WalletDemoController(FakeDemoWallet(), InMemoryDemoPinStore(), biometrics)

        setContent { WalletDemoApp(controller) }

        onNodeWithText("Create a PIN").assertIsDisplayed()
        onNodeWithText("Biometrics are not available on this device.")
            .performScrollTo()
            .assertIsDisplayed()

        biometrics.available = true
        controller.refreshBiometricUnlockAvailability()
        waitForIdle()

        onNodeWithText("Use Face ID or fingerprint instead of typing the PIN. The PIN remains a fallback.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun pinSetupKeepsSubmitReachableWhenScrolled() = runComposeUiTest {
        val controller = WalletDemoController(FakeDemoWallet(), InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }

        onNodeWithTag(WalletUiTestTags.PinSubmitButton)
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun unavailableBiometricSigningIsDisabledButNoneRemainsAvailable() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            signingProtectionAvailability = WalletDemoSigningProtectionAvailability.BiometricNotEnrolled,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        controller.handleApplicationForegrounded()
        setContent { WalletDemoApp(controller) }
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.biometricSigningAvailability ==
                WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
        }

        onNodeWithTag(WalletUiTestTags.SigningProtectionBiometric)
            .performScrollTo()
            .assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.SigningProtectionNone)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        onNodeWithTag(WalletUiTestTags.PinSubmitButton)
            .performScrollTo()
            .assertIsEnabled()
    }

    fun credentialsTabShowsCompactCardsAndNavigatesToDetails() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }

        unlockWithPin()

        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag("wallet.tab.credentials").assertIsDisplayed()
        onNodeWithTag("wallet.tab.receive").assertIsDisplayed()
        onNodeWithTag("wallet.tab.present").assertIsDisplayed()
        onNodeWithContentDescription("Credentials tab").assertIsDisplayed()
        onNodeWithContentDescription("Receive tab").assertIsDisplayed()
        onNodeWithContentDescription("Present tab").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithText("Example Credential").assertIsDisplayed()

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
            onAllNodesWithTag(WalletUiTestTags.claimGroup("About this credential")).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText("Example Credential").assertCountEquals(1)
        onNodeWithTag(WalletUiTestTags.claimGroup("About this credential"))
            .performScrollTo()
            .assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.claim("system.format")).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.claimGroup("About this credential")).performClick()
        onNodeWithText("Example Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claim("system.format")).performScrollTo().assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithText("Street address").performScrollTo().assertIsDisplayed()
        onNodeWithText("Main Street 1").performScrollTo().assertIsDisplayed()
        onNodeWithText("Portrait").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage("portrait.elementValue"))
            .performScrollTo()
            .assertIsDisplayed()
        onAllNodesWithText("image/png").assertCountEquals(2)
        onNodeWithText("QR code").performScrollTo().assertIsDisplayed()
        val qrDataPath = "qr_data"
        onNodeWithTag(WalletUiTestTags.claimImage(qrDataPath))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        onNodeWithTag(WalletUiTestTags.claimImageViewer(qrDataPath)).assertIsDisplayed()
        onNodeWithContentDescription("Full-screen credential image").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImageViewerClose(qrDataPath))
            .assertIsDisplayed()
            .performClick()
        onAllNodesWithTag(WalletUiTestTags.claimImageViewer(qrDataPath)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage(qrDataPath)).assertIsDisplayed()
        onAllNodesWithText("Raw credential data").assertCountEquals(0)
        onNodeWithTag("wallet.detailsBack").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.SettingsButton).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("walt.id Wallet").assertIsDisplayed()
        assertEquals(1, wallet.bootstrapCalls)
    }

    fun credentialsTabShowsEmptyStateAndUpdatesAfterReceive() = runComposeUiTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentials.empty").assertIsDisplayed()
        onNodeWithTag("wallet.tab.receive").performClick()
        wallet.credentials = listOf(sampleCredential)
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithText("Example Issuer").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.ExampleCredential").assertExists()
        onAllNodesWithText("vc+sd-jwt").assertCountEquals(0)
        assertIssuerDetailsCollapsedUntilRequested()
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onAllNodesWithTag("wallet.receiveNewButton").assertCountEquals(0)
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()
        onNodeWithText("Ada").performScrollTo().assertIsDisplayed()
        onNodeWithTag("wallet.detailsBack").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onNodeWithTag("wallet.offerInput").assertTextContains("")
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
    }

    fun receiveTabCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onNodeWithTag("wallet.offerInput").assertTextContains("")
        onNodeWithTag("wallet.receiveButton").assertIsNotEnabled()
    }

    fun receiveDetailsStayScopedToReceiveTabNavigationStack() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").performTextInput("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag("wallet.credentialDetailsScreen").assertIsDisplayed()
        onNodeWithText("Given name").performScrollTo().assertIsDisplayed()

        onNodeWithTag("wallet.tab.receive").performClick()
        onNodeWithTag("wallet.offerInput").assertIsEnabled()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
    }

    fun receiveTabDisablesUrlControlsWhileReceiving() = runComposeUiTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            receiveGate = receiveGate,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.receive").performClick()
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
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
    }

    fun transactionCodeOfferCanBeDeclinedWithoutCode() = runComposeUiTest {
        val wallet = FakeDemoWallet(transactionCodeRequired = true)
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.ReceiveTab).performClick()
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://example")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }

        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).assertIsNotEnabled()
        onNodeWithTag(WalletUiTestTags.OfferDeclineButton)
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview == null }
        onNodeWithTag("wallet.status").assertTextContains("Credential offer declined")
        onNodeWithTag(WalletUiTestTags.OfferInput).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.OfferInput).assertTextContains("")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).assertIsNotEnabled()
        assertEquals(null, wallet.receivedOfferUrl)
    }

    fun authorizationCodeOfferExplainsIssuerSignIn() = runComposeUiTest {
        val controller = WalletDemoController(
            FakeDemoWallet(issuanceGrant = WalletDemoIssuanceGrant.AuthorizationCode),
            InMemoryDemoPinStore(),
        )

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.ReceiveTab).performClick()
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
        val wallet = FakeDemoWallet(
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

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.ReceiveTab).performClick()
        onNodeWithTag(WalletUiTestTags.OfferInput).performTextInput("openid-credential-offer://example")
        onNodeWithTag(WalletUiTestTags.ReceiveButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }

        onNodeWithTag("wallet.credentialCard.org.iso.23220.photoid.1").assertExists()
        onAllNodesWithTag(WalletUiTestTags.OfferSupportedClaims).assertCountEquals(0)
        onAllNodesWithText("mso_mdoc").assertCountEquals(0)
        onAllNodesWithText("18 or older").assertCountEquals(0)
    }

    fun receiveAndPresentTabsExposeQrScanActions() = runComposeUiTest {
        val controller = WalletDemoController(
            FakeDemoWallet(credentials = listOf(sampleCredential)),
            InMemoryDemoPinStore(),
        )

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.ReceiveTab).performClick()
        onNodeWithTag(WalletUiTestTags.OfferScanButton).assertIsDisplayed().assertIsEnabled()

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationScanButton).assertIsDisplayed().assertIsEnabled()
    }

    fun presentTabAllowsPreviewAndDeclineWithoutCredentials() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = emptyList(),
            presentationPreview = samplePresentationPreview.copy(credentialOptions = emptyList()),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
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
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsNotEnabled()
        onAllNodesWithTag(WalletUiTestTags.PresentationNewButton).assertCountEquals(0)
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
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewResult = WalletDemoPresentationPreviewResult.Invalid(error),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
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
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled().performTextReplacement("openid4vp://invalid")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationError == error }
        onNodeWithTag(WalletUiTestTags.PresentationErrorNotifyButton).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.presentationError == null &&
                controller.state.value.requestDrafts.presentationRequestUrl.isEmpty()
        }

        assertEquals("openid4vp://invalid", wallet.rejectedRequestUrl)
        onNodeWithTag(WalletUiTestTags.Status).assertTextContains("Verifier notified")
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsNotEnabled()
        onAllNodesWithTag(WalletUiTestTags.PresentationNewButton).assertCountEquals(0)
    }

    fun presentTabPreviewsCredentialsAndCanStartNewFlowAfterSuccess() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
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
        onNodeWithTag(WalletUiTestTags.presentationCredential(samplePresentationCredentialOption.selection.id)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithText("Disclosure 7").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationClaimsDialog).assertCountEquals(0)

        onNodeWithTag("wallet.presentationSubmitButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        onNodeWithTag("wallet.presentationInput").assertIsEnabled()
        onNodeWithTag("wallet.presentationInput").assertTextContains("")
        onAllNodesWithTag(WalletUiTestTags.PresentationNewButton).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationReview).assertCountEquals(0)
        onAllNodesWithTag("wallet.presentationSubmitButton").assertCountEquals(0)
        onAllNodesWithTag("wallet.presentationRejectButton").assertCountEquals(0)
        onNodeWithTag("wallet.presentButton").assertIsNotEnabled()
        assertEquals("openid4vp://example", wallet.previewedRequestUrl)
        assertEquals("openid4vp://example", wallet.submittedRequestUrl)
    }

    fun presentTabDeclineSendsProtocolRejection() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.PresentationCancelButton).performClick()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.presentationPreview == null &&
                controller.state.value.requestDrafts.presentationRequestUrl.isEmpty()
        }
        onNodeWithTag(WalletUiTestTags.Status).assertTextContains("Presentation review cancelled")
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsNotEnabled()
        onAllNodesWithTag(WalletUiTestTags.PresentationNewButton).assertCountEquals(0)

        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.PresentationRejectButton).performClick()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation declined" }

        assertEquals("openid4vp://example", wallet.rejectedRequestUrl)
        onNodeWithTag(WalletUiTestTags.Status).assertTextContains("Presentation declined")
        onAllNodesWithTag(WalletUiTestTags.PresentationReview).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.PresentationNewButton).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertIsEnabled()
        onNodeWithTag(WalletUiTestTags.PresentationInput).assertTextContains("")
        onNodeWithTag(WalletUiTestTags.PresentButton).assertIsNotEnabled()
    }

    fun presentTabShowsUnencryptedResponseState() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onAllNodesWithTag(WalletUiTestTags.PresentationResponseProtectionSection).assertCountEquals(0)
        onAllNodesWithText("Not requested").assertCountEquals(0)
        onAllNodesWithText("Key management algorithm").assertCountEquals(0)
        onAllNodesWithText("Verifier key thumbprint").assertCountEquals(0)
    }

    fun presentationDisclosureImagesRenderAsImages() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(pathOnlyPortraitDisclosureCredentialOption),
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        val portraitDisclosurePath = "disclosures[0].portrait"
        onAllNodesWithTag(WalletUiTestTags.claim(portraitDisclosurePath)).assertCountEquals(0)

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(pathOnlyPortraitDisclosureCredentialOption.selection.id)).performScrollTo().performClick()
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        onAllNodesWithText("$.portrait").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.claim(portraitDisclosurePath)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage(portraitDisclosurePath))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        onNodeWithTag(WalletUiTestTags.claimImageViewer(portraitDisclosurePath)).assertIsDisplayed()
        onNodeWithContentDescription("Full-screen credential image").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImageViewerClose(portraitDisclosurePath))
            .assertIsDisplayed()
            .performClick()
        onAllNodesWithTag(WalletUiTestTags.claimImageViewer(portraitDisclosurePath)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.claimImage(portraitDisclosurePath)).assertIsDisplayed()
    }

    fun presentationWithoutVerifierDisplayKeepsClientIdInTechnicalDetails() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                verifierMetadata = null,
                clientId = sampleDidClientId,
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
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
                    selectivelyDisclosable = true,
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
                    selectivelyDisclosable = true,
                )
            ),
        )
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview.copy(
                credentialOptions = listOf(identityOption, ageOption),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("identity", "age")))
                ),
            ),
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.PresentTab).performClick()
        onNodeWithTag(WalletUiTestTags.PresentationInput).performTextInput("openid4vp://example")
        onNodeWithTag(WalletUiTestTags.PresentButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(identityDisclosure.id)).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.presentationDisclosureToggle(ageDisclosure.id)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationCredentialToggle(identityOption.selection.id)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsEnabled()

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(ageOption.selection.id)).performScrollTo().performClick()
        onNodeWithText("Age disclosure").performScrollTo().assertIsDisplayed()
        onNodeWithText("Over 18").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Identity disclosure").assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
    }

    fun presentDetailsStayScopedToPresentTabNavigationStack() = runComposeUiTest {
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onNodeWithText("Requested disclosures").performScrollTo().assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()
        onAllNodesWithTag(WalletUiTestTags.PresentationClaimsDialog).assertCountEquals(0)

        onNodeWithTag("wallet.tab.credentials").performClick()
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag(WalletUiTestTags.PresentationReview).assertIsDisplayed()
        onAllNodesWithTag("wallet.credentialDetailsScreen").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(samplePresentationCredentialOption.selection.id))
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun presentTabDisablesUrlControlsWhilePreviewing() = runComposeUiTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = compactPresentationPreview,
            previewGate = previewGate,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)

        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Resolving presentation..." }
        onNodeWithTag("wallet.status").assertTextContains("Resolving presentation...")
        onNodeWithTag("wallet.presentationInput").assertIsNotEnabled()
        onNodeWithTag("wallet.presentButton").assertIsNotEnabled()

        previewGate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onAllNodesWithTag("wallet.presentationInput").assertCountEquals(0)
        awaitTaggedNode(WalletUiTestTags.PresentationActions)
        onNodeWithTag(WalletUiTestTags.PresentationActions).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.PresentationSubmitButton, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    fun deepLinksRouteToReceiveAndPresentTabs() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationResult = WalletDemoOperationResult.Success("Presentation sent"),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.receiveTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.offerInput").assertTextContains(offerUrl)

        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag("wallet.status").assertTextContains("Received 1 credential(s)")
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.presentationInput").assertTextContains(requestUrl)

        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithTag("wallet.presentationSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.statusText == "Presentation sent" }
        onNodeWithTag("wallet.status").assertTextContains("Presentation sent")
        assertEquals(offerUrl, wallet.receivedOfferUrl)
        assertEquals(requestUrl, wallet.previewedRequestUrl)
        assertEquals(requestUrl, wallet.submittedRequestUrl)
    }

    fun deepLinksResetReceiveAndPresentDetailStacksEvenWhenUrlIsUnchanged() = runComposeUiTest {
        val offerUrl = "openid-credential-offer://example"
        val requestUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentialsAfterReceive = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        controller.handleDeepLink(offerUrl)
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.selectedTab == WalletDemoTab.Credentials }
        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()

        controller.handleDeepLink(offerUrl)
        waitForIdle()
        onNodeWithTag("wallet.receiveTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.offerInput").assertTextContains(offerUrl)
        onNodeWithTag("wallet.receiveButton").assertIsEnabled()

        controller.handleDeepLink(requestUrl)
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }
        onNodeWithTag(WalletUiTestTags.PresentationReview).assertIsDisplayed()

        controller.handleDeepLink(requestUrl)
        waitForIdle()
        onNodeWithTag("wallet.presentTabContent").assertIsDisplayed()
        onNodeWithTag("wallet.presentationInput").assertTextContains(requestUrl)
        onNodeWithTag("wallet.presentButton").assertIsEnabled()
    }

    fun credentialsPersistAcrossControllerRecreation() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentialsAfterReceive = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val firstController = WalletDemoController(wallet, pinStore)
        var activeController by mutableStateOf(firstController)

        setContent { WalletDemoApp(activeController) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.session is WalletSessionState.Ready }

        firstController.handleDeepLink("openid-credential-offer://example")
        onNodeWithTag("wallet.receiveButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.offerPreview != null }
        onNodeWithTag(WalletUiTestTags.OfferAcceptButton).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { firstController.state.value.statusText.startsWith("Received") }
        onNodeWithTag("wallet.credentialCard.cred-1").performScrollTo().assertIsDisplayed()

        val recreatedController = WalletDemoController(wallet, pinStore)
        activeController = recreatedController
        waitForIdle()
        onNodeWithText("Enter your PIN").assertIsDisplayed()
        onAllNodesWithTag("wallet.pinConfirmationInput").assertCountEquals(0)
        loginWithPin()
        waitUntil(timeoutMillis = 5_000) { recreatedController.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentialCard.cred-1").assertIsDisplayed()
        assertEquals(2, wallet.bootstrapCalls)
    }

    fun customBrandingTitleAppearsInTheHeader() = runComposeUiTest {
        val wallet = FakeDemoWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())
        val branding = WalletDemoBranding(appTitle = "Acme Wallet")

        setContent { WalletDemoApp(controller, branding) }
        onNodeWithText("Acme Wallet").assertIsDisplayed()
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        onNodeWithTag(WalletUiTestTags.AppTitle).assertTextEquals("Acme Wallet")
    }

    fun settingsReplacesHeaderLockAndShowsDidAndKey() = runComposeUiTest {
        val wallet = FakeDemoWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onAllNodesWithText("Lock").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.SettingsButton).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsScreen).assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsDid).assertTextContains("did:key:test")
        onNodeWithTag(WalletUiTestTags.SettingsKeyId).assertTextContains("key-1")
        val session = controller.state.value.session as WalletSessionState.Ready
        assertTrue(session.publicJwk.contains("OKP"), session.publicJwk)
        // iOS Compose text matching does not treat JSON fragments as substrings.
        onNodeWithTag(WalletUiTestTags.SettingsPublicJwk)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsCredentialSharing)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOn()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).assertIsOff()
        assertEquals(false, controller.state.value.showDcApiPresentationPreview)
        onNodeWithTag(WalletUiTestTags.SettingsLock)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsReset)
            .performScrollTo()
            .assertIsDisplayed()

        onNodeWithTag(WalletUiTestTags.SettingsLock).performClick()
        onNodeWithText("Enter your PIN").assertIsDisplayed()
    }

    fun lockDoesNotAutoPromptBiometrics() = runComposeUiTest {
        val pinStore = InMemoryDemoPinStore()
        val biometrics = RecordingDemoBiometricAuthenticator()
        val controller = WalletDemoController(FakeDemoWallet(), pinStore, biometrics)

        setContent { WalletDemoApp(controller) }
        controller.updateUseBiometrics(true)
        waitUntil(timeoutMillis = 5_000) {
            (controller.state.value.auth as? WalletAuthState.Setup)?.useBiometrics == true
        }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }
        assertEquals(1, biometrics.authenticateCalls)

        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsLock)
            .performScrollTo()
            .performClick()
        waitForIdle()

        onNodeWithText("Enter your PIN").assertIsDisplayed()
        assertEquals(1, biometrics.authenticateCalls)
        val login = controller.state.value.auth as WalletAuthState.Login
        assertTrue(login.biometricPromptConsumed)

        onNodeWithTag(WalletUiTestTags.PinBiometricButton).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.auth is WalletAuthState.Unlocked
        }
        assertEquals(2, biometrics.authenticateCalls)
    }

    fun settingsConfirmsAndAppliesSigningProtectionChange() = runComposeUiTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val controller = WalletDemoController(wallet, pinStore)

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
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
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.credentialCard.cred-1").performClick()
        onNodeWithTag(WalletUiTestTags.DetailsMenu).assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(WalletUiTestTags.CopyRawCredential).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(WalletUiTestTags.CopyRawCredential).assertIsDisplayed()
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
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = samplePresentationPreview,
        )
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) { controller.state.value.session is WalletSessionState.Ready }

        onNodeWithTag("wallet.tab.present").performClick()
        onNodeWithTag("wallet.presentationInput").performTextInput("openid4vp://example")
        onNodeWithTag("wallet.presentButton").performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = 5_000) { controller.state.value.presentationPreview != null }

        val selectionId = samplePresentationCredentialOption.selection.id
        assertTrue(selectionId != "cred-1")
        onNodeWithTag(WalletUiTestTags.presentationClaimsToggle(selectionId)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.PresentationClaimsDialog).assertIsDisplayed()
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.PresentationClaimsClose).performClick()

        onNodeWithTag(WalletUiTestTags.CredentialsTab).performClick()
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

    fun successStatusCanBeDismissedFromTheHeader() = runComposeUiTest {
        val wallet = FakeDemoWallet()
        val controller = WalletDemoController(wallet, InMemoryDemoPinStore())

        setContent { WalletDemoApp(controller) }
        unlockWithPin()
        waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session is WalletSessionState.Ready &&
                controller.state.value.statusText == "Wallet ready" &&
                controller.state.value.isStatusVisible
        }
        onNodeWithTag("wallet.status").assertTextContains("Wallet ready")
        onNodeWithTag(WalletUiTestTags.StatusDismiss).performClick()
        onAllNodesWithTag("wallet.status").assertCountEquals(0)
    }

    private fun ComposeUiTest.awaitTaggedNode(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.unlockWithPin() {
        onNodeWithTag("wallet.pinInput").performClick().performTextInput("1234")
        onNodeWithTag("wallet.pinConfirmationInput").performClick().performTextInput("1234")
        waitForIdle()
        onNodeWithTag("wallet.pinSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeUiTest.loginWithPin() {
        onNodeWithTag("wallet.pinInput").performClick().performTextInput("1234")
        onNodeWithTag("wallet.pinSubmitButton").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeUiTest.assertPresentationActionsFollowReviewContent() {
        val expectedCredentialTag = WalletUiTestTags.presentationCredential(samplePresentationCredentialOption.selection.id)
        onNodeWithText("Example Verifier").performScrollTo().assertIsDisplayed()
        onNodeWithTag(expectedCredentialTag).performScrollTo().assertIsDisplayed()
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
        val sampleCredential = WalletDemoCredential(
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
                  "portrait": {
                    "elementValue": [-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]
                  },
                  "qr_data": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
                }
            """.trimIndent(),
        )

        val samplePresentationPreview = WalletDemoPresentationPreview(
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
                    ),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )

        val compactPresentationPreview = samplePresentationPreview.copy(
            credentialOptions = listOf(
                samplePresentationPreview.credentialOptions.single().copy(disclosures = emptyList()),
            ),
        )

        val pathOnlyPortraitDisclosureCredentialOption = WalletDemoPresentationCredentialOption(
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
                )
            ),
        )

        const val sampleDidClientId = "decentralized_identifier:did:jwk:abc"
        private const val samplePortraitDisclosureValueJson =
            "[-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 4, 0, 0, 0, -75, 28, 12, 2, 0, 0, 0, 11, 73, 68, 65, 84, 120, -38, 99, -4, -1, 31, 0, 3, 3, 2, 0, -17, -65, -89, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126]"

        private val samplePresentationCredentialOption: WalletDemoPresentationCredentialOption
            get() = samplePresentationPreview.credentialOptions.single()
    }
}

private class RecordingDemoBiometricAuthenticator(
    var available: Boolean = true,
) : DemoBiometricAuthenticator {
    var authenticateCalls = 0

    override fun isAvailable(): Boolean = available

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

    override fun clear() = Unit
}

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val credentialsAfterReceive: List<WalletDemoCredential>? = null,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success("Presentation sent"),
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoAppTestScenarios.samplePresentationPreview,
    private val presentationPreviewResult: WalletDemoPresentationPreviewResult? = null,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val previewGate: CompletableDeferred<Unit>? = null,
    private val transactionCodeRequired: Boolean = false,
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

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

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
            ),
        )
    }

    override suspend fun beginAuthorizationIssuance(sessionId: String): WalletDemoIssuanceAuthorization =
        WalletDemoIssuanceAuthorization("https://issuer.example/authorize")

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome {
        receivedOfferUrl = issuanceSources[sessionId]
        receiveGate?.await()
        credentialsAfterReceive?.let { credentials = it }
        return WalletDemoIssuanceOutcome.Stored(receivedCredentialIds)
    }

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Failed("Authorization code is not configured")

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome {
        issuanceSources.remove(sessionId)
        return WalletDemoIssuanceOutcome.Cancelled
    }

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        WalletDemoIssuanceOutcome.Failed("Deferred issuance is not configured")

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
