package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityAction
import id.walt.wallet2.mobile.MobileWalletProximityActionResult
import id.walt.wallet2.mobile.MobileWalletProximityCapabilities
import id.walt.wallet2.mobile.MobileWalletProximityBleBearerPolicy
import id.walt.wallet2.mobile.MobileWalletProximityBleRoles
import id.walt.wallet2.mobile.MobileWalletProximityConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityCredentialOption
import id.walt.wallet2.mobile.MobileWalletProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.MobileWalletProximityDocumentReview
import id.walt.wallet2.mobile.MobileWalletProximityElementReference
import id.walt.wallet2.mobile.MobileWalletProximityError
import id.walt.wallet2.mobile.MobileWalletProximityErrorCategory
import id.walt.wallet2.mobile.MobileWalletProximityEngagementConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximityNfcEngagementMode
import id.walt.wallet2.mobile.MobileWalletProximityProfile
import id.walt.wallet2.mobile.MobileWalletProximityReaderPolicy
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettings
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.wallet2.mobile.MobileWalletProximityRetrievalConfiguration
import id.walt.wallet2.mobile.MobileWalletProximityRequestedElement
import id.walt.wallet2.mobile.MobileWalletProximityReview
import id.walt.wallet2.mobile.MobileWalletProximitySession
import id.walt.wallet2.mobile.MobileWalletProximityState
import id.walt.wallet2.mobile.MobileWalletProximityTransportCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class WalletDemoProximityControllerTest {
    @Test
    fun `selected runtime permission is resolved before the SDK session starts`() = runTest {
        var capabilities = blockedCapabilities
        val session = FakeSession(
            MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session, capabilities = { capabilities })
        val controller = controller(backend)
        val performed = mutableListOf<MobileWalletProximityRemediationAction>()

        controller.start()
        advanceUntilIdle()

        assertEquals(1, backend.capabilityCalls)
        assertEquals(0, backend.startCalls)
        assertEquals(
            MobileWalletProximityState.CheckingPrerequisites(blockedCapabilities),
            controller.state.value.sessionState,
        )

        controller.remediate(
            MobileWalletProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { action ->
                performed += action
                capabilities = readyCapabilities
                MobileWalletProximityHostActionResult.Completed
            },
        )
        advanceUntilIdle()

        assertEquals(listOf(MobileWalletProximityRemediationAction.RequestBluetoothPermission), performed)
        assertEquals(2, backend.capabilityCalls)
        assertEquals(1, backend.startCalls)
        assertEquals(session.state.value, controller.state.value.sessionState)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `denied selected permission cannot silently fall back to another transport`() = runTest {
        assertTrue(fallbackCapabilities.mayStart)
        val session = FakeSession(
            MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session, capabilities = { fallbackCapabilities })
        val controller = controller(backend)

        controller.start()
        advanceUntilIdle()
        assertEquals(0, backend.startCalls)

        controller.remediate(
            MobileWalletProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { MobileWalletProximityHostActionResult.Cancelled },
        )
        advanceUntilIdle()

        assertEquals(2, backend.capabilityCalls)
        assertEquals(0, backend.startCalls)
        assertEquals(
            MobileWalletProximityState.CheckingPrerequisites(fallbackCapabilities),
            controller.state.value.sessionState,
        )
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `start observes the SDK session without copying protocol state`() = runTest {
        val session = FakeSession(MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026))
        val backend = FakeBackend(session = session)
        val controller = controller(backend)

        controller.start()
        advanceUntilIdle()

        assertEquals(1, backend.startCalls)
        val configuration = requireNotNull(backend.lastConfiguration)
        val engagement = assertIs<MobileWalletProximityEngagementConfiguration.QrAndNfc>(
            configuration.engagement,
        )
        assertIs<MobileWalletProximityNfcEngagementMode.Negotiated>(engagement.mode)
        val retrieval = assertIs<MobileWalletProximityRetrievalConfiguration.Conventional>(
            configuration.retrieval,
        )
        assertNotNull(retrieval.bluetoothLowEnergy)
        assertNotNull(retrieval.nfc)
        assertEquals(session.state.value, controller.state.value.sessionState)
        assertTrue(controller.state.value.active)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `provisional NFCv2 hybrid profile selects only NFC engagement with alternate GATT BLE`() {
        val configuration = WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid.configuration()

        val engagement = assertIs<MobileWalletProximityEngagementConfiguration.NfcOnly>(
            configuration.engagement,
        )
        assertIs<MobileWalletProximityNfcEngagementMode.ProvisionalV2>(engagement.mode)
        val retrieval = assertIs<MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2>(
            configuration.retrieval,
        )
        val bluetooth = assertNotNull(retrieval.bluetoothLowEnergy)
        assertEquals(MobileWalletProximityBleRoles.CentralClient, bluetooth.roles)
        assertEquals(MobileWalletProximityBleBearerPolicy.GattOnly, bluetooth.bearerPolicy)
        assertNull(retrieval.qrNfc)
    }

    @Test
    fun `provisional NFCv2 direct profile has no fallback bearer`() {
        val configuration = WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct.configuration()

        val engagement = assertIs<MobileWalletProximityEngagementConfiguration.NfcOnly>(
            configuration.engagement,
        )
        assertIs<MobileWalletProximityNfcEngagementMode.ProvisionalV2>(engagement.mode)
        val retrieval = assertIs<MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2>(
            configuration.retrieval,
        )
        assertNull(retrieval.bluetoothLowEnergy)
        assertNull(retrieval.qrNfc)
    }

    @Test
    fun `start snapshots the selected profile before launching the session`() = runTest {
        var selected = WalletDemoProximityTransportProfile.Default
        val session = FakeSession(
            MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session)
        val controller = controller(backend) { selected }

        controller.start()
        selected = WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct
        advanceUntilIdle()

        assertIs<MobileWalletProximityEngagementConfiguration.QrAndNfc>(
            requireNotNull(backend.lastConfiguration).engagement,
        )
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `review defaults are complete and approval contains only the current holder choices`() = runTest {
        val session = FakeSession(MobileWalletProximityState.ReviewRequired(review()))
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

        val approval = session.actions.single() as MobileWalletProximityAction.Approve
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
        val session = FakeSession(MobileWalletProximityState.ReviewRequired(review()))
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        assertFalse(controller.state.value.continueAfterResponse)
        controller.setContinueAfterResponse(true)
        controller.approve()
        advanceUntilIdle()

        val firstApproval = session.actions.single() as MobileWalletProximityAction.Approve
        assertTrue(firstApproval.submission.continueAfterResponse)

        session.mutableState.value = MobileWalletProximityState.AwaitingNextRequest(completedExchanges = 1)
        advanceUntilIdle()
        assertEquals(
            MobileWalletProximityState.AwaitingNextRequest(completedExchanges = 1),
            controller.state.value.sessionState,
        )
        session.mutableState.value = MobileWalletProximityState.ReviewRequired(review().copy(exchange = 2))
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
        val session = FakeSession(MobileWalletProximityState.CheckingPrerequisites(blockedCapabilities))
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        controller.handleLifecycleInterruption()
        advanceUntilIdle()
        assertTrue(session.actions.isEmpty())

        session.mutableState.value = MobileWalletProximityState.AwaitingRequest(exchange = 1)
        advanceUntilIdle()
        controller.handleLifecycleInterruption()
        advanceUntilIdle()
        assertEquals(listOf<MobileWalletProximityAction>(MobileWalletProximityAction.Cancel), session.actions)
    }

    @Test
    fun `remediation dispatches only an advertised privacy-safe result and surfaces rejection`() = runTest {
        val rejection = MobileWalletProximityError(
            category = MobileWalletProximityErrorCategory.Capability,
            code = "bluetooth_still_unavailable",
            message = "Bluetooth is still unavailable",
            recoverable = true,
        )
        val session = FakeSession(
            initialState = MobileWalletProximityState.CheckingPrerequisites(blockedCapabilities),
            actionResult = MobileWalletProximityActionResult.Rejected(rejection),
        )
        val controller = controller(FakeBackend(session = session))
        controller.start()
        advanceUntilIdle()

        controller.remediate(
            MobileWalletProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { MobileWalletProximityHostActionResult.Completed },
        )
        advanceUntilIdle()

        assertEquals(
            listOf<MobileWalletProximityAction>(
                MobileWalletProximityAction.ReportRemediation(
                    MobileWalletProximityRemediationAction.RequestBluetoothPermission,
                    MobileWalletProximityHostActionResult.Completed,
                )
            ),
            session.actions,
        )
        assertEquals(rejection, controller.state.value.actionError)

        controller.remediate(
            MobileWalletProximityRemediationAction.OpenApplicationSettings,
            WalletDemoProximityHostActionExecutor { MobileWalletProximityHostActionResult.Completed },
        )
        advanceUntilIdle()
        assertEquals(1, session.actions.size)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `dismiss cancels an in-flight host action without reporting a late result`() = runTest {
        val session = FakeSession(MobileWalletProximityState.CheckingPrerequisites(blockedCapabilities))
        val controller = controller(FakeBackend(session = session))
        val cancelled = CompletableDeferred<Unit>()
        controller.start()
        advanceUntilIdle()

        controller.remediate(
            MobileWalletProximityRemediationAction.RequestBluetoothPermission,
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
            MobileWalletProximityRemediationAction.RequestBluetoothPermission,
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
        val session = FakeSession(MobileWalletProximityState.Preparing(MobileWalletProximityProfile.Iso180135Edition2Dis2026))
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
            MobileWalletProximityState.Completed(
                exchanges = 1,
                declined = false,
            )
        )
        val backend = FakeBackend(session)
        var policy = MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted
        val controller = WalletDemoProximityController(
            wallet = backend,
            readerTrustSettingsProvider = {
                MobileWalletProximityReaderTrustSettings(readerPolicy = policy)
            },
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        controller.start()
        advanceUntilIdle()
        policy = MobileWalletProximityReaderPolicy.RequireTrusted
        assertEquals(
            MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
            backend.configurations.single().readerPolicy,
        )

        controller.dismiss()
        advanceUntilIdle()
        controller.start()
        advanceUntilIdle()
        assertEquals(
            MobileWalletProximityReaderPolicy.RequireTrusted,
            backend.configurations.last().readerPolicy,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        backend: ProximityPresentationBackend,
        profileProvider: () -> WalletDemoProximityTransportProfile = {
            WalletDemoProximityTransportProfile.Default
        },
    ): WalletDemoProximityController = WalletDemoProximityController(
        wallet = backend,
        profileProvider = profileProvider,
        scope = this,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}

private class FakeBackend(
    private val session: MobileWalletProximitySession,
    private val startGate: CompletableDeferred<Unit>? = null,
    private val capabilities: () -> MobileWalletProximityCapabilities = { readyCapabilities },
) : ProximityPresentationBackend {
    var capabilityCalls: Int = 0
        private set
    var startCalls: Int = 0
        private set
    val configurations = mutableListOf<MobileWalletProximityConfiguration>()
    val lastConfiguration: MobileWalletProximityConfiguration?
        get() = configurations.lastOrNull()

    override suspend fun proximityPresentationCapabilities(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximityCapabilities {
        capabilityCalls += 1
        return capabilities()
    }

    override suspend fun startProximityPresentation(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximitySession {
        startCalls += 1
        configurations += configuration
        startGate?.let { withContext(NonCancellable) { it.await() } }
        return session
    }
}

private class FakeSession(
    initialState: MobileWalletProximityState,
    private val actionResult: MobileWalletProximityActionResult = MobileWalletProximityActionResult.Accepted,
) : MobileWalletProximitySession {
    val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<MobileWalletProximityState> = mutableState
    val actions = mutableListOf<MobileWalletProximityAction>()
    var closeCalls = 0
        private set

    override suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult {
        actions += action
        if (action == MobileWalletProximityAction.Cancel && actionResult == MobileWalletProximityActionResult.Accepted) {
            mutableState.value = MobileWalletProximityState.Cancelled
        }
        return actionResult
    }

    override suspend fun close() {
        closeCalls += 1
    }
}

private val familyName = MobileWalletProximityElementReference("org.iso.18013.5.1", "family_name")
private val portrait = MobileWalletProximityElementReference("org.iso.18013.5.1", "portrait")
private val eligibility = MobileWalletProximityElementReference("org.waltid.example.proof", "eligible")
private val unoffered = MobileWalletProximityElementReference("org.iso.18013.5.1", "age_over_18")

private fun review(): MobileWalletProximityReview = MobileWalletProximityReview(
    exchange = 1,
    documents = listOf(
        MobileWalletProximityDocumentReview(
            requestIndex = 0,
            docType = "org.iso.18013.5.1.mDL",
            credentialOptions = listOf(
                credential("credential-a", listOf(familyName, portrait)),
                credential("credential-b", listOf(familyName)),
            ),
        ),
        MobileWalletProximityDocumentReview(
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
    elements: List<MobileWalletProximityElementReference>,
): MobileWalletProximityCredentialOption = MobileWalletProximityCredentialOption(
    credentialId = id,
    label = id,
    issuer = "Example issuer",
    validUntil = Instant.DISTANT_FUTURE,
    deviceAuthentication = MobileWalletProximityDeviceAuthenticationMethod.Signature,
    requestedElements = elements.map {
        MobileWalletProximityRequestedElement(
            namespace = it.namespace,
            elementIdentifier = it.elementIdentifier,
            intentToRetain = it == portrait,
        )
    },
)

private val availableSelected = MobileWalletProximityTransportCapability(
    implemented = true,
    profilePermitted = true,
    runtimeAvailable = true,
    selected = true,
)

private val availableUnselected = availableSelected.copy(selected = false)

private val readyCapabilities = MobileWalletProximityCapabilities(
    profile = MobileWalletProximityProfile.Iso180135Edition2Dis2026,
    qrEngagement = availableSelected,
    nfcEngagement = availableUnselected,
    bluetoothLowEnergy = availableSelected,
    nfcRetrieval = availableUnselected,
    nfcV2Retrieval = availableUnselected,
    wifiAwareRetrieval = availableUnselected,
)

private val bluetoothUnavailable = MobileWalletProximityError(
    category = MobileWalletProximityErrorCategory.Capability,
    code = "bluetooth_permission_required",
    message = "Bluetooth permission is required",
    recoverable = true,
)

private val blockedCapabilities = readyCapabilities.copy(
    bluetoothLowEnergy = MobileWalletProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        runtimeAvailable = false,
        selected = true,
        unavailable = bluetoothUnavailable,
        remediationActions = listOf(MobileWalletProximityRemediationAction.RequestBluetoothPermission),
    )
)

private val fallbackCapabilities = blockedCapabilities.copy(
    nfcEngagement = availableSelected,
    nfcRetrieval = availableSelected,
)
