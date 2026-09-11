package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityNfcRetrievalConfiguration
import id.walt.wallet2.mobile.ProximityRetrievalOptions
import id.walt.wallet2.mobile.ProximityReviewId
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityRuntimeObservation
import id.walt.wallet2.mobile.ProximityAction
import id.walt.wallet2.mobile.ProximityActionResult
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityConfiguration
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityErrorCategory
import id.walt.wallet2.mobile.ProximitySessionConfiguration
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityNfcHandover
import id.walt.wallet2.mobile.ProximityProfile
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.wallet2.mobile.ProximityRequestedElement
import id.walt.wallet2.mobile.ProximityReview
import id.walt.wallet2.mobile.ProximitySession
import id.walt.wallet2.mobile.ProximityState
import id.walt.wallet2.mobile.ProximityTransportCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoProximityControllerTest {
    @Test
    fun `selected runtime permission is resolved before the SDK session starts`() = runTest {
        var capabilities = blockedCapabilities
        val session = FakeSession(
            ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session, capabilities = { capabilities })
        val controller = controller(backend)
        val performed = mutableListOf<ProximityRemediationAction>()

        controller.start()
        advanceUntilIdle()

        assertEquals(1, backend.capabilityCalls)
        assertEquals(0, backend.startCalls)
        assertEquals(
            ProximityState.CheckingPrerequisites(blockedCapabilities),
            controller.state.value.sessionState,
        )

        controller.remediate(
            ProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { action ->
                performed += action
                capabilities = readyCapabilities
                ProximityHostActionResult.Completed
            },
        )
        advanceUntilIdle()

        assertEquals(listOf(ProximityRemediationAction.RequestBluetoothPermission), performed)
        assertEquals(2, backend.capabilityCalls)
        assertEquals(1, backend.startCalls)
        assertEquals(session.state.value, controller.state.value.sessionState)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `optional BLE permission does not block a viable selected NFC route`() = runTest {
        assertTrue(fallbackCapabilities.mayStart)
        val session = FakeSession(
            ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session, capabilities = { fallbackCapabilities })
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        assertEquals(1, backend.capabilityCalls)
        assertEquals(1, backend.startCalls)
        assertEquals(session.state.value, controller.state.value.sessionState)
        assertNull(controller.state.value.automaticPermissionAction)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `start observes the SDK session without copying protocol state`() = runTest {
        val session = FakeSession(ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026))
        val backend = FakeBackend(session = session)
        val controller = controller(backend)

        controller.start()
        advanceUntilIdle()

        assertEquals(1, backend.startCalls)
        assertEquals(session.state.value, controller.state.value.sessionState)
        assertTrue(controller.state.value.active)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `review defaults are complete and approval contains only the current holder choices`() = runTest {
        val session = FakeSession(ProximityState.ReviewRequired(review()))
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        assertTrue(controller.state.value.canApprove)
        assertEquals(2, controller.state.value.selections.size)
        assertEquals(
            "credential-a",
            controller.state.value.selections.single { it.requestIndex == 0 }.credentialId,
        )
        assertEquals(
            setOf(familyName, portrait),
            controller.state.value.selections.single { it.requestIndex == 0 }.disclosedElements,
        )

        controller.selectCredential(requestIndex = 0, credentialId = "credential-b")
        assertEquals(
            "credential-b",
            controller.state.value.selections.single { it.requestIndex == 0 }.credentialId,
        )
        assertEquals(
            setOf(familyName),
            controller.state.value.selections.single { it.requestIndex == 0 }.disclosedElements,
        )

        controller.toggleElement(requestIndex = 0, element = familyName)
        assertFalse(controller.state.value.canApprove)
        controller.approve()
        advanceUntilIdle()
        assertTrue(session.actions.isEmpty())

        controller.toggleElement(requestIndex = 0, element = familyName)
        controller.toggleElement(requestIndex = 0, element = unoffered)
        controller.approve()
        advanceUntilIdle()

        val approval = session.actions.single() as ProximityAction.Approve
        assertEquals(controller.state.value.review?.reviewId, approval.reviewId)
        assertEquals(2, approval.submission.documents.size)
        val primary = approval.submission.documents.single { it.requestIndex == 0 }
        assertEquals("credential-b", primary.credentialId)
        assertEquals(setOf(familyName), primary.disclosedElements)
        val proof = approval.submission.documents.single { it.requestIndex == 1 }
        assertEquals("proof-credential", proof.credentialId)
        assertEquals(setOf(eligibility), proof.disclosedElements)
        assertFalse(approval.submission.continueAfterResponse)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `repeated requests require an explicit choice and reset it for fresh consent`() = runTest {
        val session = FakeSession(ProximityState.ReviewRequired(review()))
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        assertFalse(controller.state.value.continueAfterResponse)
        controller.setContinueAfterResponse(true)
        controller.approve()
        advanceUntilIdle()

        val firstApproval = session.actions.single() as ProximityAction.Approve
        assertTrue(firstApproval.submission.continueAfterResponse)

        session.mutableState.value = ProximityState.AwaitingNextRequest(completedExchanges = 1)
        advanceUntilIdle()
        assertEquals(
            ProximityState.AwaitingNextRequest(completedExchanges = 1),
            controller.state.value.sessionState,
        )
        session.mutableState.value = ProximityState.ReviewRequired(review().copy(exchange = 2))
        advanceUntilIdle()

        assertFalse(controller.state.value.continueAfterResponse)
        assertEquals(
            "credential-a",
            controller.state.value.selections.single { it.requestIndex == 0 }.credentialId,
        )
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `lifecycle interruption preserves prerequisite remediation but cancels an active exchange`() = runTest {
        val session = FakeSession(ProximityState.CheckingPrerequisites(blockedCapabilities))
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        controller.handleLifecycleInterruption()
        advanceUntilIdle()
        assertTrue(session.actions.isEmpty())

        session.mutableState.value = ProximityState.AwaitingRequest(exchange = 1)
        advanceUntilIdle()
        controller.handleLifecycleInterruption()
        advanceUntilIdle()
        assertEquals(listOf<ProximityAction>(ProximityAction.Cancel), session.actions)
    }

    @Test
    fun `remediation dispatches only an advertised privacy-safe result and surfaces rejection`() = runTest {
        val rejection = ProximityError(
            category = ProximityErrorCategory.Capability,
            code = "bluetooth_still_unavailable",
            message = "Bluetooth is still unavailable",
            recovery = ProximityRecovery.RetryPrerequisites,
        )
        val session = FakeSession(
            initialState = ProximityState.CheckingPrerequisites(blockedCapabilities),
            actionResult = ProximityActionResult.Rejected(rejection),
        )
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        controller.remediate(
            ProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
        )
        advanceUntilIdle()

        assertEquals(
            listOf<ProximityAction>(
                ProximityAction.ReportRemediation(
                    ProximityRemediationAction.RequestBluetoothPermission,
                    ProximityHostActionResult.Completed,
                )
            ),
            session.actions,
        )
        assertEquals(rejection, controller.state.value.actionError)

        controller.remediate(
            ProximityRemediationAction.OpenApplicationSettings,
            WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Completed },
        )
        advanceUntilIdle()
        assertEquals(1, session.actions.size)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `dismiss cancels an in-flight host action without reporting a late result`() = runTest {
        val session = FakeSession(ProximityState.CheckingPrerequisites(blockedCapabilities))
        val controller = controller(FakeBackend(session = session))
        val cancelled = CompletableDeferred<Unit>()
        controller.start()
        advanceUntilIdle()

        controller.remediate(
            ProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            },
        )
        advanceUntilIdle()
        assertEquals(
            ProximityRemediationAction.RequestBluetoothPermission,
            controller.state.value.hostActionInProgress,
        )

        controller.dismiss()
        advanceUntilIdle()

        assertTrue(cancelled.isCompleted)
        assertTrue(session.actions.isEmpty())
        assertNull(controller.state.value.hostActionInProgress)
    }

    @Test
    fun `startup cancellation closes a session returned by a cancellation-insensitive late start`() = runTest {
        val startGate = CompletableDeferred<Unit>()
        val session = FakeSession(ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026))
        val backend = FakeBackend(session = session, startGate = startGate)
        val controller = controller(backend)

        controller.start()
        advanceUntilIdle()
        assertEquals(1, backend.startCalls)
        controller.cancel()
        startGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, session.closeCalls)
        assertFalse(controller.state.value.active)
        assertNull(controller.state.value.sessionState)
        assertTrue(controller.state.value.selections.isEmpty())
    }

    @Test
    fun `configuration provider is resolved once for each new session`() = runTest {
        val session = FakeSession(
            ProximityState.Completed(
                exchanges = 1,
                declined = false,
            )
        )
        val backend = FakeBackend(session)
        var policy = ProximityReaderPolicy.AllowAnonymousOrUntrusted
        val controller = WalletDemoProximityController(
            wallet = backend,
            configurationProvider = {
                ProximityConfiguration(readerPolicy = policy)
            },
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        controller.start()
        advanceUntilIdle()
        policy = ProximityReaderPolicy.RequireTrusted
        assertEquals(
            ProximityReaderPolicy.AllowAnonymousOrUntrusted,
            backend.configurations.single().readerPolicy,
        )

        controller.dismiss()
        advanceUntilIdle()
        controller.start()
        advanceUntilIdle()
        assertEquals(
            ProximityReaderPolicy.RequireTrusted,
            backend.configurations.last().readerPolicy,
        )
    }

    @Test
    fun `no-data ends the session and keeps its result available until dismissal`() = runTest {
        val session = FakeSession(ProximityState.AwaitingRequest(2))
        val controller = controller(FakeBackend(session))
        controller.start()
        advanceUntilIdle()
        session.mutableState.value = ProximityState.NoData(2)
        advanceUntilIdle()
        assertEquals(ProximityState.NoData(2), controller.state.value.sessionState)
        assertTrue(controller.state.value.isTerminal)
        controller.dismiss()
        advanceUntilIdle()
        assertEquals(1, session.closeCalls)
        assertNull(controller.state.value.sessionState)
    }

    private fun TestScope.controller(
        backend: ProximityPresentationBackend,
    ): WalletDemoProximityController = WalletDemoProximityController(
        wallet = backend,
        scope = this,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}

private class FakeBackend(
    private val session: ProximitySession,
    private val startGate: CompletableDeferred<Unit>? = null,
    private val capabilities: () -> ProximityCapabilities = { readyCapabilities },
) : ProximityPresentationBackend {
    var capabilityCalls: Int = 0
        private set
    var startCalls: Int = 0
        private set
    val configurations = mutableListOf<ProximityConfiguration>()

    override suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities {
        capabilityCalls += 1
        return capabilities()
    }

    override suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession {
        startCalls += 1
        configurations += configuration
        startGate?.let { withContext(NonCancellable) { it.await() } }
        return session
    }
}

private class FakeSession(
    initialState: ProximityState,
    private val actionResult: ProximityActionResult = ProximityActionResult.Accepted,
) : ProximitySession {
    val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<ProximityState> = mutableState
    val actions = mutableListOf<ProximityAction>()
    var closeCalls = 0
        private set

    override suspend fun dispatch(action: ProximityAction): ProximityActionResult {
        actions += action
        if (action == ProximityAction.Cancel && actionResult == ProximityActionResult.Accepted) {
            mutableState.value = ProximityState.Cancelled
        }
        return actionResult
    }

    override suspend fun close() {
        closeCalls += 1
    }
}

private val familyName = ProximityElementReference("org.iso.18013.5.1", "family_name")
private val portrait = ProximityElementReference("org.iso.18013.5.1", "portrait")
private val eligibility = ProximityElementReference("org.waltid.example.proof", "eligible")
private val unoffered = ProximityElementReference("org.iso.18013.5.1", "age_over_18")

private fun review(): ProximityReview = ProximityReview(
    reviewId = ProximityReviewId(Uuid.random().toString()),
    exchange = 1,
    documents = listOf(
        ProximityDocumentReview(
            requestIndex = 0,
            docType = "org.iso.18013.5.1.mDL",
            credentialOptions = listOf(
                credential("credential-a", listOf(familyName, portrait)),
                credential("credential-b", listOf(familyName)),
            ),
        ),
        ProximityDocumentReview(
            requestIndex = 1,
            docType = "org.waltid.example.proof",
            credentialOptions = listOf(credential("proof-credential", listOf(eligibility))),
        ),
    ),
    readerAuthentication = emptyList(),
    useCases = emptyList(),
    applicationAuthorizations = emptyList(),
)

private fun credential(
    id: String,
    elements: List<ProximityElementReference>,
): ProximityCredentialOption = ProximityCredentialOption(
    credentialId = id,
    label = id,
    issuer = "Example issuer",
    validUntil = Instant.DISTANT_FUTURE,
    deviceAuthentication = ProximityDeviceAuthenticationMethod.Signature,
    requestedElements = elements.map {
        ProximityRequestedElement(
            namespace = it.namespace,
            elementIdentifier = it.elementIdentifier,
            intentToRetain = it == portrait,
        )
    },
)

private val availableSelected = ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        selected = true,
        runtime = ProximityRuntimeObservation.Available,
    )

private val availableUnselected = availableSelected.copy(selected = false)

private val readyCapabilities = ProximityCapabilities(
    session = ProximitySessionConfiguration.Qr(),
    profile = ProximityProfile.Iso180135Edition2Dis2026,
    qrEngagement = availableSelected,
    nfcEngagement = availableUnselected,
    bluetoothLowEnergy = availableSelected,
    nfcRetrieval = availableUnselected,
    nfcV2Retrieval = availableUnselected,
    wifiAwareRetrieval = availableUnselected,
)

private val bluetoothUnavailable = ProximityError(
    category = ProximityErrorCategory.Capability,
    code = "bluetooth_permission_required",
    message = "Bluetooth permission is required",
    recovery = ProximityRecovery.RetryPrerequisites,
)

private val blockedCapabilities = readyCapabilities.copy(
    bluetoothLowEnergy = ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        selected = true,
        runtime = ProximityRuntimeObservation.Unavailable(bluetoothUnavailable, listOf(ProximityRemediationAction.RequestBluetoothPermission)),
    )
)

private val fallbackCapabilities = blockedCapabilities.copy(
    session = ProximitySessionConfiguration.ConventionalNfc(
        handover = ProximityNfcHandover.Static,
        retrieval = ProximityRetrievalOptions(
            nfc = ProximityNfcRetrievalConfiguration(),
        ),
        qrFallback = ProximityRetrievalOptions(),
    ),
    nfcEngagement = availableSelected,
    nfcRetrieval = availableSelected,
)
