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
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val issuancePreviewHandle = WalletDemoIssuancePreviewHandle("issuance-preview")
private val presentationPreviewHandle = WalletDemoPresentationPreviewHandle("presentation-preview")

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
            previewHandle = presentationPreviewHandle,
            verifierMetadata = verifierMetadata("Example Verifier"),
            clientId = "https://verifier.example",
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
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

        assertEquals(listOf(presentationPreviewHandle), wallet.rejectedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.presentationError)
        assertEquals(WalletDisplayText.VerifierNotified, controller.state.value.statusText)

        controller.updatePresentationRequestUrl("openid4vp://invalid-again")
        controller.previewPresentation()
        runCurrent()
        controller.startNewPresentationFlow()
        runCurrent()

        assertEquals(null, controller.state.value.presentationError)
        assertEquals("", controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
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
        assertEquals(issuancePreviewHandle, wallet.receivedPreviewHandle)
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

        assertEquals(null, wallet.receivedPreviewHandle)
        assertEquals("Wallet ready", controller.state.value.statusText)
    }

    @Test
    fun receiveRequiresNonBlankTransactionCodeAndIssuesOnce() = runTest {
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
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
        assertEquals(textTransactionCode(), controller.state.value.offerPreview?.transactionCode)
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
        assertEquals(issuancePreviewHandle, wallet.receivedPreviewHandle)
        assertEquals("abc-123", wallet.receivedTxCode)
        assertTrue(controller.state.value.receiveCompleted)
        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun changingOfferResetsTransactionCodeState() = runTest {
        val wallet = FakeDemoWallet(offerResolution = offerPreview(transactionCode = textTransactionCode()))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://first")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("1234")

        controller.updateOfferUrl("openid-credential-offer://second")

        assertEquals("", controller.state.value.requestDrafts.txCode)
        assertEquals(null, controller.state.value.offerPreview)
    }

    @Test
    fun numericTransactionCodeIsFilteredCappedAndValidated() = runTest {
        val requirement = WalletDemoTransactionCodeRequirement(
            inputMode = WalletDemoTransactionCodeInputMode.Numeric,
            length = 6,
            description = null,
        )
        val controller = unlockedControllerWith(
            FakeDemoWallet(offerResolution = offerPreview(transactionCode = requirement)),
            this,
        )

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updateTxCode("12a34")

        assertEquals("1234", controller.state.value.requestDrafts.txCode)
        assertFalse(controller.state.value.acceptOfferEnabled)

        controller.updateTxCode("12a345678")

        assertEquals("123456", controller.state.value.requestDrafts.txCode)
        assertTrue(controller.state.value.acceptOfferEnabled)
    }

    @Test
    fun decliningOfferDiscardsSelectedIssuancePreview() = runTest {
        val wallet = FakeDemoWallet()
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.declineOffer()
        runCurrent()

        assertEquals(listOf(issuancePreviewHandle), wallet.discardedIssuancePreviewHandles)
        assertEquals(null, controller.state.value.offerPreview)
        val outcome = assertIs<WalletInteractionState.Success>(controller.state.value.interaction)
        assertEquals(WalletInteractionSuccessOutcome.OfferDeclined, outcome.outcome)
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
        controller.previewOffer()
        runCurrent()
        controller.acceptOffer()
        runCurrent()

        assertEquals(1, wallet.resolveOfferCalls)
        assertEquals(1, wallet.receiveCalls)
        assertTrue(controller.state.value.receiveCompleted)
    }

    @Test
    fun replacementConfirmationPreventsStaleOfferResolutionFromWinning() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
            resolveOfferGate = resolutionGate,
            ignoreResolveCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)
        val replacementOffer = "openid-credential-offer://replacement"

        controller.updateOfferUrl("openid-credential-offer://original")
        controller.previewOffer()
        runCurrent()
        controller.handleDeepLink(replacementOffer)
        assertEquals(
            WalletIncomingRequest(WalletInteractionKind.Receive, replacementOffer, WalletRequestSource.DeepLink),
            controller.state.value.replacementRequest,
        )
        controller.replaceCurrentRequest()
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertEquals(issuancePreviewHandle, state.offerPreview?.previewHandle)
        assertEquals(WalletOperationState.OfferPreview, state.operation)
        assertEquals(2, wallet.resolveOfferCalls)
        assertEquals(0, wallet.receiveCalls)
    }

    @Test
    fun replacementFailureBelongsToNewestIncomingDeepLink() = runTest {
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
        controller.replaceCurrentRequest()
        resolutionGate.complete(Unit)
        runCurrent()

        val state = controller.state.value
        assertEquals(replacementOffer, state.requestDrafts.offerUrl)
        assertTrue(state.operation is WalletOperationState.Failed)
        assertTrue(state.interaction is WalletInteractionState.Failure)
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
        assertEquals(listOf(issuancePreviewHandle), wallet.discardedIssuancePreviewHandles)
    }

    @Test
    fun lockCancelsIssuanceAndClearsTransactionCode() = runTest {
        val receiveGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            offerResolution = offerPreview(transactionCode = textTransactionCode()),
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
    fun lockDiscardsActiveIssuanceAndPresentationPreviews() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://example")
        controller.previewOffer()
        runCurrent()
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()

        controller.lock()
        runCurrent()

        assertEquals(listOf(issuancePreviewHandle), wallet.discardedIssuancePreviewHandles)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals(null, controller.state.value.presentationPreview)
    }

    @Test
    fun presentationDeepLinkRequiresExplicitReplacementOfActiveOffer() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.updateOfferUrl("openid-credential-offer://issuer.example")
        controller.previewOffer()
        runCurrent()
        val receiveResetKey = controller.state.value.receiveNavigationResetKey

        controller.handleDeepLink("openid4vp://verifier.example")
        runCurrent()

        assertEquals(emptyList(), wallet.discardedIssuancePreviewHandles)
        assertEquals(issuancePreviewHandle, controller.state.value.offerPreview?.previewHandle)
        assertEquals(WalletInteractionKind.Present, controller.state.value.replacementRequest?.kind)

        controller.replaceCurrentRequest()
        runCurrent()

        assertEquals(listOf(issuancePreviewHandle), wallet.discardedIssuancePreviewHandles)
        assertEquals(null, controller.state.value.offerPreview)
        assertEquals(receiveResetKey + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
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
    fun presentationPreviewSubmitRejectAndDismissUseSelectedHandle() = runTest {
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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

        controller.startNewPresentationFlow()
        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.cancelPresentationReview()
        runCurrent()

        assertEquals(
            WalletOperationState.Succeeded("Presentation review cancelled", WalletDemoTab.Present),
            controller.state.value.operation,
        )
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(emptySet(), controller.state.value.selectedPresentationCredentialOptions)
        assertEquals(emptySet(), controller.state.value.selectedPresentationDisclosureOptions)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)

        controller.previewPresentation()
        runCurrent()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(listOf(presentationPreviewHandle), wallet.rejectedPresentationPreviewHandles)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(
            WalletOperationState.Succeeded("Presentation rejected", WalletDemoTab.Present),
            controller.state.value.operation,
        )
    }

    @Test
    fun presentationPreviewIsSingleFlight() = runTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewGate = previewGate,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        controller.previewPresentation()
        runCurrent()

        assertEquals(1, wallet.previewPresentationCalls)
        assertEquals(WalletOperationState.ResolvingPresentation, controller.state.value.operation)

        previewGate.complete(Unit)
        runCurrent()
        controller.previewPresentation()
        runCurrent()

        assertEquals(1, wallet.previewPresentationCalls)
        assertEquals(presentationPreviewHandle, controller.state.value.presentationPreview?.previewHandle)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
    }

    @Test
    fun presentationActionsAreSingleFlightAndCannotOverwriteLock() = runTest {
        val submitGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationSubmitGate = submitGate,
            ignorePresentationSubmitCancellation = true,
            presentationPreview = WalletDemoPresentationPreview(
                previewHandle = presentationPreviewHandle,
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
                verifierMetadata = null,
                clientId = null,
                credentialOptions = listOf(
                    WalletDemoPresentationCredentialOption(
                        queryId = "pid",
                        credentialId = sampleCredential.id,
                        label = sampleCredential.label,
                        issuer = sampleCredential.issuer.orEmpty(),
                        format = sampleCredential.format,
                        credentialDataJson = sampleCredential.credentialDataJson.orEmpty(),
                        disclosures = emptyList(),
                    )
                ),
            ),
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        controller.submitPresentation()
        controller.submitPresentation()
        controller.rejectPresentation()
        runCurrent()

        assertEquals(1, wallet.submitPresentationCalls)
        assertEquals(emptyList(), wallet.rejectedPresentationPreviewHandles)
        assertEquals(WalletOperationState.Presenting, controller.state.value.operation)

        controller.lock()
        submitGate.complete(Unit)
        runCurrent()

        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(1, wallet.submitPresentationCalls)
    }

    @Test
    fun lockDiscardsPresentationPreviewResolvedAfterCancellation() = runTest {
        val previewGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreviewGate = previewGate,
            ignorePresentationPreviewCancellation = true,
        )
        val controller = unlockedControllerWith(wallet, this)

        controller.updatePresentationRequestUrl("openid4vp://example")
        controller.previewPresentation()
        runCurrent()
        val resetKeyBeforeLock = controller.state.value.presentationNavigationResetKey

        controller.lock()
        previewGate.complete(Unit)
        runCurrent()

        assertEquals(resetKeyBeforeLock + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(null, controller.state.value.presentationPreview)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertEquals(listOf(presentationPreviewHandle), wallet.discardedPresentationPreviewHandles)
    }

    @Test
    fun presentationDisclosureSelectionDefaultsOffAndSubmitsSelectedPaths() = runTest {
        val preview = WalletDemoPresentationPreview(
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
    fun handleDeepLinkQueuesLatestRequestWhileWalletIsLocked() = runTest {
        val controller = controllerWith(FakeDemoWallet(), this)
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"

        controller.handleDeepLink(offerUrl)
        assertEquals(WalletInteractionKind.Receive, controller.state.value.pendingIncomingRequest?.kind)
        controller.handleDeepLink(presentationUrl)
        assertEquals(WalletInteractionKind.Present, controller.state.value.pendingIncomingRequest?.kind)
        controller.handleDeepLink("https://example.com/ignored")

        assertEquals(presentationUrl, controller.state.value.pendingIncomingRequest?.value)
    }

    @Test
    fun handleDeepLinkResetsCompletedReceiveAndPresentationState() = runTest {
        val offerUrl = "openid-credential-offer://example"
        val presentationUrl = "openid4vp://example"
        val wallet = FakeDemoWallet(
            credentials = listOf(sampleCredential),
            presentationPreview = WalletDemoPresentationPreview(
                previewHandle = presentationPreviewHandle,
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
                verifierMetadata = verifierMetadata("Example Verifier"),
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

        val presentationResetKeyBeforeOfferLink = controller.state.value.presentationNavigationResetKey
        controller.finishInteraction()
        controller.handleDeepLink(offerUrl)
        runCurrent()

        assertEquals(WalletDemoTab.Receive, controller.state.value.selectedTab)
        assertEquals(offerUrl, controller.state.value.requestDrafts.offerUrl)
        assertEquals(emptyList(), controller.state.value.lastReceivedCredentialIds)
        assertFalse(controller.state.value.receiveCompleted)
        assertEquals(WalletOperationState.OfferPreview, controller.state.value.operation)
        assertTrue(controller.state.value.interaction is WalletInteractionState.ReviewingOffer)

        val presentationResetKeyBeforePresentationLink = controller.state.value.presentationNavigationResetKey
        val receiveResetKeyBeforePresentationLink = controller.state.value.receiveNavigationResetKey
        controller.handleDeepLink(presentationUrl)
        assertEquals(WalletInteractionKind.Present, controller.state.value.replacementRequest?.kind)
        controller.replaceCurrentRequest()
        runCurrent()

        assertEquals(WalletDemoTab.Present, controller.state.value.selectedTab)
        assertEquals(presentationUrl, controller.state.value.requestDrafts.presentationRequestUrl)
        assertEquals(receiveResetKeyBeforePresentationLink + 1, controller.state.value.receiveNavigationResetKey)
        assertEquals(presentationResetKeyBeforePresentationLink + 1, controller.state.value.presentationNavigationResetKey)
        assertEquals(presentationPreviewHandle, controller.state.value.presentationPreview?.previewHandle)
        assertFalse(controller.state.value.presentationCompleted)
        assertEquals(WalletOperationState.Idle, controller.state.value.operation)
        assertTrue(controller.state.value.interaction is WalletInteractionState.ReviewingPresentation)

        controller.handleDeepLink(presentationUrl)
        assertEquals(presentationUrl, controller.state.value.replacementRequest?.value)
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
            previewHandle = presentationPreviewHandle,
            responseEncryption = WalletDemoResponseEncryption.NotRequired,
            verifierMetadata = verifierMetadata("Example Verifier"),
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
        assertEquals(null, controller.state.value.presentationPreview)
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
                WalletDisplayText.PresentationRejected,
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
            WalletOperationState.Succeeded(WalletDisplayText.PresentationRejected, WalletDemoTab.Present),
            controller.state.value.operation,
        )
    }

    @Test
    fun formPostRejectionRemainsPendingUntilDeliveryAndSurfacesFailure() = runTest {
        val html = "<form method=\"post\" action=\"https://verifier.example/response\"></form>"
        val wallet = FakeDemoWallet(
            rejectionResult = WalletDemoOperationResult.Success(
                WalletDisplayText.PresentationRejected,
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

    @Test
    fun captureValidatesContentAndOffersSafeFlowSwitch() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)

        controller.startReceiveCapture()
        assertEquals(
            WalletInteractionState.Capturing(WalletInteractionKind.Receive),
            controller.state.value.interaction,
        )

        controller.submitCapturedRequest("not a wallet request")
        val invalidCapture = controller.state.value.interaction as WalletInteractionState.Capturing
        assertTrue(invalidCapture.error?.contains("not a supported") == true)

        controller.showManualEntry()
        controller.submitCapturedRequest("openid4vp://verifier.example", WalletRequestSource.Manual)
        assertTrue(controller.state.value.interaction is WalletInteractionState.WrongRequestType)

        controller.switchToDetectedRequest()
        runCurrent()

        assertEquals(1, wallet.previewPresentationCalls)
        assertTrue(controller.state.value.interaction is WalletInteractionState.ReviewingPresentation)
    }

    @Test
    fun duplicateScannerCallbacksStartOneResolution() = runTest {
        val resolutionGate = CompletableDeferred<Unit>()
        val wallet = FakeDemoWallet(resolveOfferGate = resolutionGate)
        val controller = unlockedControllerWith(wallet, this)
        val offer = "openid-credential-offer://issuer.example"

        controller.startReceiveCapture()
        controller.submitCapturedRequest(offer)
        controller.submitCapturedRequest(offer)
        runCurrent()

        assertEquals(1, wallet.resolveOfferCalls)
        resolutionGate.complete(Unit)
        runCurrent()
        assertTrue(controller.state.value.interaction is WalletInteractionState.ReviewingOffer)
    }

    @Test
    fun incomingRequestSurvivesLockAndResolvesAfterUnlock() = runTest {
        val wallet = FakeDemoWallet(credentials = listOf(sampleCredential))
        val controller = unlockedControllerWith(wallet, this)
        val request = "openid4vp://verifier.example"

        controller.lock()
        controller.handleDeepLink(request)
        assertEquals(request, controller.state.value.pendingIncomingRequest?.value)

        controller.updatePin("1234")
        controller.submitPin()
        runCurrent()

        assertEquals(null, controller.state.value.pendingIncomingRequest)
        assertEquals(request, controller.state.value.requestDrafts.presentationRequestUrl)
        assertTrue(controller.state.value.interaction is WalletInteractionState.ReviewingPresentation)
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

private fun offerPreview(
    transactionCode: WalletDemoTransactionCodeRequirement? = null,
): WalletDemoOfferPreview = WalletDemoOfferPreview(
    previewHandle = issuancePreviewHandle,
    issuer = WalletDemoIssuerMetadata(
        credentialIssuer = "https://issuer.example",
        display = WalletDemoMetadataDisplay(
            name = "Example Issuer",
            logoUri = null,
            logoAltText = null,
        ),
    ),
    offeredCredentials = listOf(
        WalletDemoOfferedCredentialMetadata(
            configurationId = "ExampleCredential",
            format = "vc+sd-jwt",
            scope = "example_credential",
            vct = "ExampleCredential",
            doctype = null,
            display = null,
            claims = emptyList(),
        )
    ),
    transactionCode = transactionCode,
)

private fun textTransactionCode(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = WalletDemoTransactionCodeInputMode.Text,
        length = null,
        description = "Enter the code from the issuer",
    )

private fun verifierMetadata(name: String): WalletDemoVerifierMetadata =
    WalletDemoVerifierMetadata(
        display = WalletDemoMetadataDisplay(
            name = name,
            logoUri = null,
            logoAltText = null,
        ),
        clientUri = "https://verifier.example",
        policyUri = null,
        termsOfServiceUri = null,
    )

private class FakeDemoWallet(
    var credentials: List<WalletDemoCredential> = emptyList(),
    private val receivedCredentialIds: List<String> = listOf("cred-1"),
    private val offerResolution: WalletDemoOfferPreview = offerPreview(),
    private val resolveOfferGate: CompletableDeferred<Unit>? = null,
    private val ignoreResolveCancellation: Boolean = false,
    private val resolveOfferError: Throwable? = null,
    private val receiveGate: CompletableDeferred<Unit>? = null,
    private val ignoreReceiveCancellation: Boolean = false,
    private val presentationResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationSent),
    private val rejectionResult: WalletDemoOperationResult = WalletDemoOperationResult.Success(WalletDisplayText.PresentationRejected),
    private val presentationPreviewGate: CompletableDeferred<Unit>? = null,
    private val ignorePresentationPreviewCancellation: Boolean = false,
    private val presentationSubmitGate: CompletableDeferred<Unit>? = null,
    private val ignorePresentationSubmitCancellation: Boolean = false,
    private val presentationPreview: WalletDemoPresentationPreview = WalletDemoPresentationPreview(
        previewHandle = presentationPreviewHandle,
        responseEncryption = WalletDemoResponseEncryption.NotRequired,
        verifierMetadata = null,
        clientId = null,
        credentialOptions = emptyList(),
    ),
    private val presentationError: WalletDemoPresentationError? = null,
) : DemoWallet {
    var bootstrapCalls = 0
    var resolveOfferCalls = 0
    var resolvedOfferUrl: String? = null
    var receivedPreviewHandle: WalletDemoIssuancePreviewHandle? = null
    var receivedTxCode: String? = null
    var receiveCalls = 0
    var presentedRequestUrl: String? = null
    var previewedRequestUrl: String? = null
    var previewPresentationCalls = 0
    var submitPresentationCalls = 0
    var submittedPreviewHandle: WalletDemoPresentationPreviewHandle? = null
    var submittedCredentialOptions: List<WalletDemoPresentationCredentialSelection>? = null
    var submittedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>? = null
    val discardedIssuancePreviewHandles = mutableListOf<WalletDemoIssuancePreviewHandle>()
    val discardedPresentationPreviewHandles = mutableListOf<WalletDemoPresentationPreviewHandle>()
    val rejectedPresentationPreviewHandles = mutableListOf<WalletDemoPresentationPreviewHandle>()

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

    override suspend fun receive(
        previewHandle: WalletDemoIssuancePreviewHandle,
        txCode: String?,
    ): List<String> {
        receiveCalls += 1
        receivedPreviewHandle = previewHandle
        receivedTxCode = txCode
        if (ignoreReceiveCancellation) {
            withContext(NonCancellable) { receiveGate?.await() }
        } else {
            receiveGate?.await()
        }
        return receivedCredentialIds
    }

    override suspend fun discardIssuancePreview(previewHandle: WalletDemoIssuancePreviewHandle) {
        discardedIssuancePreviewHandles += previewHandle
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult {
        presentedRequestUrl = requestUrl
        return presentationResult
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
        previewPresentationCalls += 1
        previewedRequestUrl = requestUrl
        if (ignorePresentationPreviewCancellation) {
            withContext(NonCancellable) { presentationPreviewGate?.await() }
        } else {
            presentationPreviewGate?.await()
        }
        return presentationError?.let(WalletDemoPresentationPreviewResult::Invalid)
            ?: WalletDemoPresentationPreviewResult.Ready(presentationPreview)
    }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult {
        submitPresentationCalls += 1
        submittedPreviewHandle = previewHandle
        submittedCredentialOptions = selectedCredentialOptions
        submittedDisclosureOptions = selectedDisclosureOptions
        if (ignorePresentationSubmitCancellation) {
            withContext(NonCancellable) { presentationSubmitGate?.await() }
        } else {
            presentationSubmitGate?.await()
        }
        return presentationResult
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) {
        discardedPresentationPreviewHandles += previewHandle
    }

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult {
        rejectedPresentationPreviewHandles += previewHandle
        return rejectionResult
    }
}
