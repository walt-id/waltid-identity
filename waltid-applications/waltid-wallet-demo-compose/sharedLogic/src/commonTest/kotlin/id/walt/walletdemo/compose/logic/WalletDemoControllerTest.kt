package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoControllerTest {

    @Test
    fun pinStorageReadFailureStaysLockedUntilRetrySucceeds() = runTest {
        val pinStore = RecoverableDemoPinStore()
        val wallet = FakeDemoWallet()
        val controller = controllerWith(wallet, this, pinStore)

        assertTrue(controller.state.value.auth is WalletAuthState.StorageUnavailable)

        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.StorageUnavailable)
        assertEquals(0, pinStore.setPinCalls)
        assertEquals(0, wallet.bootstrapCalls)

        pinStore.isAvailable = true
        controller.retryPinStorage()

        assertTrue(controller.state.value.auth is WalletAuthState.Login)
    }

    @Test
    fun invalidPresentationPreviewCanBeDismissedOrReturnedToVerifier() = runTest {
        val error = WalletDemoPresentationError(
            verifier = VerifierDetails(name = "Example Verifier", clientId = "https://verifier.example"),
            errorCode = "invalid_transaction_data",
            message = "Unsupported transaction_data type",
        )
        val wallet = FakeDemoWallet(presentationError = error)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://invalid")
        controller.previewPresentation()
        runCurrent()

        assertEquals(error, controller.state.value.presentationError)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(WalletDisplayText.ReviewPresentationError, controller.state.value.statusText)
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationReviewEnabled)

        controller.rejectPresentation()
        runCurrent()

        assertEquals("openid4vp://invalid", wallet.rejectedRequestUrl)
        assertEquals(null, controller.state.value.presentationError)
        assertEquals(WalletDisplayText.VerifierNotified, controller.state.value.statusText)

        controller.updatePresentationRequestUrl("openid4vp://invalid-again")
        controller.previewPresentation()
        runCurrent()
        controller.startNewPresentationFlow()

        assertEquals(null, controller.state.value.presentationError)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(null, wallet.rejectedRequestUrl.takeIf { it == "openid4vp://invalid-again" })
    }

    @Test
    fun setupPinRejectsInvalidLengthAndNonDigits() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("12a4")
        controller.updatePinConfirmation("12a4")
        controller.submitPin()

        val auth = controller.state.value.auth as WalletAuthState.Setup
        assertEquals("PIN must contain 4 to 8 digits", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun setupPinRejectsMismatchedConfirmation() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)

        controller.updatePin("1234")
        controller.updatePinConfirmation("4321")
        controller.submitPin()

        val auth = controller.state.value.auth as WalletAuthState.Setup
        assertEquals("PIN confirmation does not match", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun setupPinUnlocksAndBootstrapsWallet() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val pinStore = InMemoryDemoPinStore()
        val controller = controllerWith(wallet, this, pinStore)

        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        runCurrent()

        val state = controller.state.value
        assertTrue(state.auth is WalletAuthState.Unlocked)
        val session = state.session as WalletSessionState.Ready
        assertEquals("Wallet ready", state.statusText)
        assertEquals("did:key:test", session.did)
        assertEquals(listOf(sampleCredential), session.credentials)
        assertEquals(1, wallet.bootstrapCalls)
        assertTrue(pinStore.hasPin())
    }

    @Test
    fun configuredPinStartsRecreatedControllerInLoginAndUnlocksWithOriginalPin() = runTest {
        val pinStore = InMemoryDemoPinStore()
        val firstController = controllerWith(FakeDemoWallet(), this, pinStore)
        firstController.updatePin("1234")
        firstController.updatePinConfirmation("1234")
        firstController.submitPin()
        runCurrent()

        val recreatedWallet = FakeDemoWallet()
        val recreatedController = controllerWith(recreatedWallet, this, pinStore)

        assertTrue(recreatedController.state.value.auth is WalletAuthState.Login)
        recreatedController.updatePin("1234")
        recreatedController.submitPin()
        runCurrent()

        assertTrue(recreatedController.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(recreatedController.state.value.session is WalletSessionState.Ready)
        assertEquals(1, recreatedWallet.bootstrapCalls)
    }

    @Test
    fun recreatedControllerRejectsWrongPin() = runTest {
        val pinStore = InMemoryDemoPinStore().also { it.setPin("1234") }
        val controller = controllerWith(FakeDemoWallet(), this, pinStore)

        controller.updatePin("9999")
        controller.submitPin()
        runCurrent()

        val auth = controller.state.value.auth as WalletAuthState.Login
        assertEquals("Wrong PIN", auth.error)
        assertFalse(controller.state.value.isAuthenticating)
        assertTrue(controller.state.value.session is WalletSessionState.NotBootstrapped)
    }

    @Test
    fun wrongLoginPinKeepsWalletLocked() = runTest {
        val controller = unlockedControllerWith(FakeDemoWallet(), this)

        controller.lock()
        controller.updatePin("9999")
        controller.submitPin()
        runCurrent()

        val auth = controller.state.value.auth as WalletAuthState.Login
        assertEquals("Wrong PIN", auth.error)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
    }

    @Test
    fun correctLoginPinUnlocksWithoutRepeatingBootstrapWhenReady() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)
        assertEquals(1, wallet.bootstrapCalls)

        controller.lock()
        controller.updatePin("1234")
        controller.submitPin()
        runCurrent()

        assertTrue(controller.state.value.auth is WalletAuthState.Unlocked)
        assertTrue(controller.state.value.session is WalletSessionState.Ready)
        assertEquals(1, wallet.bootstrapCalls)
    }

    @Test
    fun receiveRefreshesCredentialsAndStatus() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1", "cred-2"))
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(sampleCredential)
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals("openid-credential-offer://example", wallet.resolvedOfferUrl)
        assertEquals("openid-credential-offer://example", wallet.receivedOfferUrl)
        assertEquals(1, wallet.receiveCalls)
        assertEquals(
            WalletOperationState.Succeeded("Received 1 credential(s)", WalletDemoTab.Receive),
            controller.state.value.operation,
        )
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        val session = controller.state.value.session as WalletSessionState.Ready
        assertEquals(listOf(sampleCredential), session.credentials)
    }

    @Test
    fun receiveIgnoresBlankOfferUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("   ")
        controller.previewOffer()
        runCurrent()

        assertEquals(null, wallet.receivedOfferUrl)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveRequiresNonBlankTransactionCodeAndIssuesOnce() = runTest {
        val wallet = FakeDemoWallet(
            offerResolution = WalletDemoOfferPreview(transactionCodeRequired = true, credentialIssuer = "https://issuer.example", offeredCredentials = listOf("ExampleCredential")),
            receivedCredentialIds = listOf("cred-1"),
        )
        val controller = unlockedControllerWith(wallet, this)
        val offerUrl = "openid-credential-offer://example"

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl(offerUrl)
        controller.previewOffer()
        runCurrent()

        assertEquals(offerUrl, wallet.resolvedOfferUrl)
        assertEquals(0, wallet.receiveCalls)
        assertTrue(controller.state.value.requestDrafts.transactionCodeRequired)
        assertFalse(controller.state.value.acceptOfferEnabled)
        assertEquals(WalletOperationState.OfferPreview, controller.state.value.operation)

        controller.acceptOffer()
        runCurrent()
        assertEquals(0, wallet.receiveCalls)

        controller.updateTxCode(" abc-123 ")
        assertTrue(controller.state.value.acceptOfferEnabled)
        wallet.credentials = listOf(sampleCredential)
        controller.acceptOffer()
        runCurrent()

        assertEquals(1, wallet.receiveCalls)
        assertEquals(offerUrl, wallet.receivedOfferUrl)
        assertEquals("abc-123", wallet.receivedTxCode)
        assertTrue(controller.state.value.receiveCompleted)
        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertFalse(controller.state.value.requestDrafts.transactionCodeRequired)
    }

    @Test
    fun changingOfferResetsTransactionCodeState() = runTest {
        val wallet = FakeDemoWallet(offerResolution = WalletDemoOfferPreview(transactionCodeRequired = true, credentialIssuer = "https://issuer.example", offeredCredentials = listOf("ExampleCredential")))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://first")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("1234")

        controller.updateOfferUrl("openid-credential-offer://second")

        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertFalse(controller.state.value.requestDrafts.transactionCodeRequired)
    }

    @Test
    fun receiveIsSingleFlight() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(resolveOfferGate = resolutionGate)
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        controller.previewOffer()
        runCurrent()

        assertEquals(1, wallet.resolveOfferCalls)
        assertEquals(WalletOperationState.ResolvingOffer, controller.state.value.operation)

        wallet.credentials = listOf(sampleCredential)
        resolutionGate.complete(Unit)
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals(1, wallet.receiveCalls)
        assertTrue(controller.state.value.receiveCompleted)
    }

    @Test
    fun staleOfferResolutionCannotOverwriteIncomingDeepLink() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = WalletDemoOfferPreview(transactionCodeRequired = true, credentialIssuer = "https://issuer.example", offeredCredentials = listOf("ExampleCredential")),
            resolveOfferGate = resolutionGate,
            ignoreResolveCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)
        val replacementOffer = "openid-credential-offer://replacement"

        controller.updateOfferUrl("openid-credential-offer://original")
        controller.previewOffer()
        runCurrent()
        controller.handleDeepLink(replacementOffer)
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertFalse(state.requestDrafts.transactionCodeRequired)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(0, wallet.receiveCalls)
    }

    @Test
    fun staleOfferResolutionFailureCannotOverwriteIncomingDeepLink() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            resolveOfferGate = resolutionGate,
            ignoreResolveCancellation = true,
            resolveOfferError = IllegalStateException("stale failure"),
        )
        val controller = unlockedControllerWith(wallet, this)
        val replacementOffer = "openid-credential-offer://replacement"

        controller.updateOfferUrl("openid-credential-offer://original")
        controller.previewOffer()
        runCurrent()
        controller.handleDeepLink(replacementOffer)
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertFalse(state.isError)
    }

    @Test
    fun lockCancelsOfferResolutionAndInvalidatesReceiveFlow() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            resolveOfferGate = resolutionGate,
            ignoreResolveCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        val resetKeyBeforeLock = controller.state.value.receiveNavigationResetKey

        controller.lock()
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertTrue(state.auth is WalletAuthState.Login)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(resetKeyBeforeLock + 1, state.receiveNavigationResetKey)
        assertEquals(0, wallet.receiveCalls)
        assertFalse(state.receiveCompleted)
    }

    @Test
    fun lockCancelsIssuanceAndClearsTransactionCode() = runTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = WalletDemoOfferPreview(transactionCodeRequired = true, credentialIssuer = "https://issuer.example", offeredCredentials = listOf("ExampleCredential")),
            receiveGate = receiveGate,
            ignoreReceiveCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("123456")
        controller.acceptOffer()
        runCurrent()
        assertEquals(1, wallet.receiveCalls)

        controller.lock()
        wallet.credentials = listOf(sampleCredential)
        receiveGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertTrue(state.auth is WalletAuthState.Login)
        assertEquals("", state.requestDrafts.txCode)
        assertEquals(WalletOperationState.Idle, state.operation)
        assertEquals(emptyList(), state.lastReceivedCredentialIds)
        assertFalse(state.receiveCompleted)
    }

    @Test
    fun presentUpdatesStatusOnSuccess() = runTest {
        val wallet = FakeDemoWallet(presentationResult = WalletDemoOperationResult.Success("Presentation sent"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.present()
        runCurrent()

        assertEquals("openid4vp://example", wallet.presentedRequestUrl)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals("Presentation sent", controller.state.value.statusText)
    }

    @Test
    fun presentationPreviewApproveAndRejectUseStepwiseWalletApi() = runTest {
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    credentialDataJson = "{}",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        )
                    ),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(preview, controller.state.value.presentationPreview)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Review presentation request", controller.state.value.statusText)
        assertEquals(setOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), controller.state.value.selectedPresentationCredentialOptions)

        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Succeeded("Presentation sent", WalletDemoTab.Present),
            controller.state.value.operation,
        )

        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals("openid4vp://example", wallet.rejectedRequestUrl)
        assertEquals(
            WalletOperationState.Succeeded("Presentation declined", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
    }

    @Test
    fun presentationDisclosureSelectionDefaultsOffAndSubmitsSelectedPaths() = runTest {
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "PID",
                    issuer = "Example Issuer",
                    format = "vc+sd-jwt",
                    credentialDataJson = "{}",
                    disclosures = listOf(
                        WalletDemoPresentationDisclosure(
                            label = "Given name",
                            path = "$.given_name",
                            valueJson = "\"Ada\"",
                            displayValue = "Ada",
                            selectivelyDisclosable = true,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Family name",
                            path = "$.family_name",
                            valueJson = "\"Lovelace\"",
                            displayValue = "Lovelace",
                            selectivelyDisclosable = true,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Age over 18",
                            path = "$.age_over_18",
                            valueJson = "true",
                            displayValue = "true",
                            selectivelyDisclosable = true,
                            required = true,
                            selectable = false,
                        ),
                        WalletDemoPresentationDisclosure(
                            label = "Credential type",
                            path = "$.vct",
                            valueJson = "\"PID\"",
                            displayValue = "PID",
                            selectivelyDisclosable = false,
                        ),
                    ),
                )
            ),
            credentialRequirements = listOf(WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        val givenName = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.given_name")
        val familyName = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.family_name")
        val ageOver18 = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.age_over_18")
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(givenName in controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(familyName in controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(ageOver18 in controller.state.value.selectedPresentationDisclosureOptions)

        controller.updatePresentationRequestUrl("openid4vp://other")

        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        controller.togglePresentationDisclosure(familyName)
        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(WalletDemoPresentationCredentialSelection("pid", "cred-1")), wallet.submittedCredentialOptions)
        assertEquals(listOf(familyName), wallet.submittedDisclosureOptions)
    }

    @Test
    fun presentationCredentialOptionsWithSameCredentialIdToggleIndependently() = runTest {
        val first = WalletDemoPresentationCredentialOption(
            queryId = "identity",
            credentialId = "cred-1",
            label = "PID identity",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val second = first.copy(queryId = "age", label = "PID age")
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("identity", "age")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(first.selection)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())
        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )

        controller.togglePresentationCredential(first.selection)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())
        controller.submitPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), wallet.submittedCredentialOptions?.toSet())
    }

    @Test
    fun presentationPreviewSelectsOneCredentialOptionPerQuery() = runTest {
        val first = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "PID one",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val second = first.copy(credentialId = "cred-2", label = "PID two")
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(second.selection)

        assertEquals(setOf(second.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(listOf(second.selection), wallet.submittedCredentialOptions)
    }

    @Test
    fun presentationPreviewCanSelectMultipleCredentialsForOneQueryWhenAllowed() = runTest {
        val firstDisclosure = WalletDemoPresentationDisclosureSelection("pid", "cred-1", "$.given_name")
        val secondDisclosure = WalletDemoPresentationDisclosureSelection("pid", "cred-2", "$.given_name")
        val first = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            multiple = true,
            label = "PID one",
            issuer = "Example Issuer",
            format = "vc+sd-jwt",
            credentialDataJson = "{}",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Given name",
                    path = firstDisclosure.path,
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val second = first.copy(
            credentialId = "cred-2",
            label = "PID two",
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Given name",
                    path = secondDisclosure.path,
                    valueJson = "\"Grace\"",
                    displayValue = "Grace",
                    selectivelyDisclosable = true,
                )
            ),
        )
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(first, second),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(first.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)

        controller.togglePresentationDisclosure(firstDisclosure)

        assertEquals(setOf(firstDisclosure), controller.state.value.selectedPresentationDisclosureOptions)

        controller.togglePresentationCredential(second.selection)

        assertEquals(
            setOf(first.selection, second.selection),
            controller.state.value.selectedPresentationCredentialOptions,
        )
        assertEquals(setOf(firstDisclosure), controller.state.value.selectedPresentationDisclosureOptions)
        assertFalse(secondDisclosure in controller.state.value.selectedPresentationDisclosureOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationDisclosure(secondDisclosure)
        controller.submitPresentation()
        runCurrent()

        assertEquals(setOf(first.selection, second.selection), wallet.submittedCredentialOptions?.toSet())
        assertEquals(setOf(firstDisclosure, secondDisclosure), wallet.submittedDisclosureOptions?.toSet())
    }

    @Test
    fun presentationPreviewSelectsFirstSatisfiableRequirementAlternativeOnly() = runTest {
        val mdl = WalletDemoPresentationCredentialOption(
            queryId = "mdl-id",
            credentialId = "cred-1",
            label = "mDL",
            issuer = "Example Issuer",
            format = "mso_mdoc",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val photoId = mdl.copy(queryId = "photo-id", credentialId = "cred-2", label = "Photo ID")
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(mdl, photoId),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("mdl-id"), listOf("photo-id")))
            ),
        )
        val controller = unlockedControllerWith(FakeDemoWallet(presentationPreview = preview), this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(mdl.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())
    }

    @Test
    fun presentationSelectionRequiresNonEmptySelectionWhenRequirementsAreEmpty() = runTest {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "optional-address",
            credentialId = "cred-1",
            label = "Address",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(option),
            credentialRequirements = emptyList(),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(option.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertTrue(controller.state.value.presentationCredentialSelectionComplete())

        controller.togglePresentationCredential(option.selection)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentationCredentialSelectionRequiresQueriesWithoutVisibleOptions() = runTest {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "identity",
            credentialId = "cred-1",
            label = "PID identity",
            issuer = "Example Issuer",
            format = "jwt_vc_json",
            credentialDataJson = "{}",
            disclosures = emptyList(),
        )
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(option),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(
                    options = listOf(listOf("identity", "age"))
                )
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        assertEquals(setOf(option.selection), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCredentialSelectionComplete())

        controller.submitPresentation()
        runCurrent()

        assertEquals(null, wallet.submittedCredentialOptions)
        assertEquals(
            WalletOperationState.Failed(
                "Present failed: select a credential for every requested credential",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentIgnoresBlankRequestUrl() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("   ")
        controller.present()
        runCurrent()

        assertEquals(null, wallet.presentedRequestUrl)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun handleDeepLinkRoutesCredentialOffersAndPresentationRequests() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        controller.handleDeepLink(presentationUrl)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
    }

    @Test
    fun handleDeepLinkResetsCompletedReceiveAndPresentationState() = runTest {
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = WalletDemoPresentationPreview(
                verifierName = "Example Verifier",
                clientId = "https://verifier.example",
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = "cred-1",
                        label = "Example Credential",
                        issuer = "Example Issuer",
                        format = "jwt_vc_json",
                        credentialDataJson = "{}",
                        disclosures = emptyList(),
                    )
                ),
                credentialRequirements = listOf(
                    WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
                ),
            )
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl(offerUrl)
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()
        assertTrue(controller.state.value.receiveCompleted)

        controller.updatePresentationRequestUrl(presentationUrl)
        controller.previewPresentation()
        runCurrent()
        controller.submitPresentation()
        runCurrent()
        assertTrue(controller.state.value.presentationCompleted)

        controller.handleDeepLink(offerUrl)

        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(1, controller.state.value.receiveNavigationResetKey)
        assertEquals(0, controller.state.value.presentationNavigationResetKey)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertTrue(controller.state.value.receiveActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.handleDeepLink(presentationUrl)

        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(1, controller.state.value.receiveNavigationResetKey)
        assertEquals(1, controller.state.value.presentationNavigationResetKey)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationPreviewActionEnabled)
        assertEquals("Wallet ready", controller.state.value.statusText)

        controller.handleDeepLink(presentationUrl)

        assertEquals(2, controller.state.value.presentationNavigationResetKey)
    }

    @Test
    fun receiveCompletionTracksReceivedCredentialsAndCanStartNewFlow() = runTest {
        val wallet = FakeDemoWallet(receivedCredentialIds = listOf("cred-1"))
        val controller = unlockedControllerWith(wallet, this)

        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        assertTrue(controller.state.value.receiveActionEnabled)

        wallet.credentials = listOf(sampleCredential)
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertTrue(controller.state.value.receiveCompleted)
        assertFalse(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)
        assertEquals(listOf("cred-1"), controller.state.value.lastReceivedCredentialIds)
        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)

        val resetKeyBeforeNewFlow = controller.state.value.receiveNavigationResetKey
        controller.startNewReceiveFlow()

        assertEquals("", controller.state.value.requestDrafts.offerUrl)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertTrue(!controller.state.value.receiveCompleted)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertFalse(controller.state.value.receiveActionEnabled)
        assertEquals(resetKeyBeforeNewFlow + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveCompletionDerivesNewCredentialsWhenWalletReturnsNoIds() = runTest {
        val existingCredential = sampleCredential.copy(id = "old-cred", label = "Existing Credential")
        val newCredential = sampleCredential.copy(id = "new-cred", label = "New Credential")
        val wallet = FakeDemoWallet(credentials = listOf(existingCredential), receivedCredentialIds = emptyList())
        val controller = unlockedControllerWith(wallet, this)

        wallet.credentials = listOf(existingCredential, newCredential)
        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertTrue(controller.state.value.receiveCompleted)
        assertEquals(listOf("new-cred"), controller.state.value.lastReceivedCredentialIds)
        assertEquals("Received 1 credential(s)", controller.state.value.statusText)
        assertEquals(listOf(newCredential), controller.state.value.receivedCredentials())
    }

    @Test
    fun receiveDoesNotCompleteWhenNoDisplayableCredentialIsAvailable() = runTest {
        val wallet = FakeDemoWallet(credentials = emptyList(), receivedCredentialIds = listOf("missing-cred"))
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Receive)
        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertTrue(controller.state.value.receiveUrlEntryEnabled)
        assertEquals(
            WalletOperationState.Failed(
                "Receive failed: received credentials are not available locally",
                WalletDemoTab.Receive,
            ),
            controller.state.value.operation,
        )
        assertEquals(
            "Receive failed: received credentials are not available locally",
            controller.state.value.statusText,
        )
    }

    @Test
    fun presentationCompletionCanStartNewFlow() = runTest {
        val preview = WalletDemoPresentationPreview(
            verifierName = "Example Verifier",
            clientId = "https://verifier.example",
            credentialOptions = listOf(
                WalletDemoPresentationCredentialOption(
                    queryId = "pid",
                    credentialId = "cred-1",
                    label = "Example Credential",
                    issuer = "Example Issuer",
                    format = "jwt_vc_json",
                    credentialDataJson = "{}",
                    disclosures = emptyList(),
                )
            ),
            credentialRequirements = listOf(
                WalletDemoPresentationCredentialRequirement(options = listOf(listOf("pid")))
            ),
        )
        val wallet = FakeDemoWallet(presentationPreview = preview)
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertTrue(controller.state.value.presentationPreviewActionEnabled)

        controller.previewPresentation()
        runCurrent()
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)

        controller.submitPresentation()
        runCurrent()

        assertTrue(controller.state.value.presentationCompleted)
        assertEquals(preview, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertFalse(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)

        val resetKeyBeforeNewFlow = controller.state.value.presentationNavigationResetKey
        controller.startNewPresentationFlow()

        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
        assertTrue(!controller.state.value.presentationCompleted)
        assertTrue(controller.state.value.presentationUrlEntryEnabled)
        assertFalse(controller.state.value.presentationPreviewActionEnabled)
        assertEquals(resetKeyBeforeNewFlow + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun rejectionSurfacesVerifierContinuationExactlyOnce() = runTest {
        val continuationUrl = "wallet-demo://presentation-complete"
        val wallet = FakeDemoWallet(
            rejectionResult = WalletDemoOperationResult.Success(
                WalletDisplayText.PresentationDeclined,
                WalletDemoPresentationContinuation.Url(continuationUrl),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(
            WalletDemoPresentationContinuation.Url(continuationUrl),
            controller.state.value.pendingPresentationContinuation?.continuation,
        )
        assertFalse(controller.state.value.presentationCompleted)
        assertTrue(controller.state.value.operation is WalletOperationState.DecliningPresentation)

        controller.completePresentationContinuation()

        assertEquals(null, controller.state.value.pendingPresentationContinuation)
        assertTrue(controller.state.value.presentationCompleted)
        assertEquals(
            WalletOperationState.Succeeded(WalletDisplayText.PresentationDeclined, WalletDemoTab.Present),
            controller.state.value.operation,
        )
    }

    @Test
    fun formPostRejectionRemainsPendingUntilDeliveryAndSurfacesFailure() = runTest {
        val html = "<form method=\"post\" action=\"https://verifier.example/response\"></form>"
        val wallet = FakeDemoWallet(
            rejectionResult = WalletDemoOperationResult.Success(
                WalletDisplayText.PresentationDeclined,
                WalletDemoPresentationContinuation.FormPostHtml(html),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.selectTab(WalletDemoTab.Present)
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(
            WalletDemoPresentationContinuation.FormPostHtml(html),
            controller.state.value.pendingPresentationContinuation?.continuation,
        )
        assertFalse(controller.state.value.presentationCompleted)

        controller.failPresentationContinuation("network unavailable")

        assertEquals(null, controller.state.value.pendingPresentationContinuation)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(
            WalletOperationState.Failed(
                "Could not deliver the verifier response: network unavailable",
                WalletDemoTab.Present,
            ),
            controller.state.value.operation,
        )
    }

    private fun controllerWith(
        wallet: DemoWallet,
        scope: TestScope,
        pinStore: DemoPinStore = InMemoryDemoPinStore(),
    ): WalletDemoController =
        WalletDemoController(
            wallet = wallet,
            pinStore = pinStore,
            scope = scope.backgroundScope,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
        )

    private fun unlockedControllerWith(wallet: DemoWallet, scope: TestScope): WalletDemoController {
        val controller = controllerWith(wallet, scope)
        controller.updatePin("1234")
        controller.updatePinConfirmation("1234")
        controller.submitPin()
        scope.runCurrent()
        return controller
    }

    private companion object {
        val sampleCredential = WalletDemoCredential(
            id = "cred-1",
            format = "jwt_vc_json",
            issuer = "Example Issuer",
            subject = "did:key:holder",
            label = "Example Credential",
            addedAt = "2026-06-17",
            credentialDataJson = WalletDemoSampleCredentialData.credentialDataJsonWithPortrait,
        )
    }
}

private class RecoverableDemoPinStore : DemoPinStore {
    var isAvailable = false
    var setPinCalls = 0

    override fun hasPin(): Boolean {
        check(isAvailable) { "PIN storage is unavailable" }
        return true
    }

    override suspend fun setPin(pin: String) {
        setPinCalls += 1
    }

    override suspend fun verifyPin(pin: String): Boolean = true
}

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val offerResolution: WalletDemoOfferPreview = WalletDemoOfferPreview(transactionCodeRequired = false, credentialIssuer = "https://issuer.example", offeredCredentials = listOf("ExampleCredential")),
    private val resolveOfferGate: CompletableDeferred<Unit>? = null,
    private val ignoreResolveCancellation: Boolean = false,
    private val resolveOfferError: Throwable? = null,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val ignoreReceiveCancellation: Boolean = false,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent),
    private val rejectionResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationDeclined),
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoPresentationPreview(
        verifierName = null,
        clientId = null,
        credentialOptions = emptyList(),
    ),
    private val presentationError: WalletDemoPresentationError? = null,
) : DemoWallet {
    var bootstrapCalls = 0
    var resolveOfferCalls = 0
    var resolvedOfferUrl: String? = null
    var receivedOfferUrl: String? = null
    var receivedTxCode: String? = null
    var receiveCalls = 0
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var submittedRequestUrl: String? = null
    var rejectedRequestUrl: String? = null
    var submittedCredentialOptions: List<WalletDemoPresentationCredentialSelection>? = null
    var submittedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>? = null

    override suspend fun bootstrap(): WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(keyId = "key-1", did = "did:key:test")
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

    override suspend fun resolveOffer(offerUrl: String): WalletDemoOfferPreview {
        resolveOfferCalls += 1
        resolvedOfferUrl = offerUrl
        if (ignoreResolveCancellation) {
            withContext(NonCancellable) { resolveOfferGate?.await() }
        } else {
            resolveOfferGate?.await()
        }
        resolveOfferError?.let { throw it }
        return offerResolution
    }

    override suspend fun receive(offerUrl: String, txCode: String?): List<String> {
        receiveCalls += 1
        receivedOfferUrl = offerUrl
        receivedTxCode = txCode
        if (ignoreReceiveCancellation) {
            withContext(NonCancellable) { receiveGate?.await() }
        } else {
            receiveGate?.await()
        }
        return receivedCredentialIds
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
        previewedRequestUrl = requestUrl
        return presentationError?.let(WalletDemoPresentationPreviewResult::Invalid)
            ?: WalletDemoPresentationPreviewResult.Ready(presentationPreview)
    }

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult {
        submittedRequestUrl = requestUrl
        submittedCredentialOptions = selectedCredentialOptions
        submittedDisclosureOptions = selectedDisclosureOptions
        return WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent)
    }

    override suspend fun rejectPresentation(requestUrl: String): WalletDemoOperationResult {
        rejectedRequestUrl = requestUrl
        return rejectionResult
    }
}
