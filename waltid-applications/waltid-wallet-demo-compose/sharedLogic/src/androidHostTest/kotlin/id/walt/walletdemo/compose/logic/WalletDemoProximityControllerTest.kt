package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityNfcRetrievalConfiguration
import id.walt.wallet2.mobile.ProximityRetrievalOptions
import id.walt.wallet2.mobile.ProximityConnectedRoute
import id.walt.wallet2.mobile.ProximityEngagementMethod
import id.walt.wallet2.mobile.ProximityTransport
import id.walt.wallet2.mobile.ProximityReviewId
import id.walt.wallet2.mobile.ProximityRecovery
import id.walt.wallet2.mobile.ProximityRuntimeObservation
import id.walt.wallet2.mobile.ProximityAction
import id.walt.wallet2.mobile.ProximityActionResult
import id.walt.wallet2.mobile.ProximityCapabilities
import id.walt.wallet2.mobile.ProximityBleBearerPolicy
import id.walt.wallet2.mobile.ProximityBleRoles
import id.walt.wallet2.mobile.ProximityConfiguration
import id.walt.wallet2.mobile.ProximityCredentialOption
import id.walt.wallet2.mobile.ProximityDeviceAuthenticationMethod
import id.walt.wallet2.mobile.ProximityDocumentReview
import id.walt.wallet2.mobile.ProximityElementReference
import id.walt.wallet2.mobile.ProximityError
import id.walt.wallet2.mobile.ProximityEngagement
import id.walt.wallet2.mobile.ProximityErrorCategory
import id.walt.wallet2.mobile.ProximitySessionConfiguration
import id.walt.wallet2.mobile.ProximityHostActionResult
import id.walt.wallet2.mobile.ProximityNfcHandover
import id.walt.wallet2.mobile.ProximityProfile
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityReaderTrustSettings
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    fun `selected BLE permission is offered even when NFC can start and decline allows fallback`() = runTest {
        assertTrue(fallbackCapabilities.mayStart)
        val session = FakeSession(
            ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session, capabilities = { fallbackCapabilities })
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        assertEquals(1, backend.capabilityCalls)
        assertEquals(0, backend.startCalls)
        assertEquals(ProximityRemediationAction.RequestBluetoothPermission, controller.state.value.automaticPermissionAction)
        controller.remediate(ProximityRemediationAction.RequestBluetoothPermission,
            WalletDemoProximityHostActionExecutor { ProximityHostActionResult.Cancelled })
        advanceUntilIdle()
        assertEquals(1, backend.startCalls)
        assertEquals(session.state.value, controller.state.value.sessionState)
        assertNull(controller.state.value.automaticPermissionAction)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `optional permission setup can be skipped only with a complete viable route`() = runTest {
        for (capabilities in listOf(fallbackCapabilities, blockedCapabilities)) {
            val session = FakeSession(ProximityState.EngagementReady(listOf(ProximityEngagement.Nfc)))
            val backend = FakeBackend(session, capabilities = { capabilities })
            val controller = controller(backend)
            controller.start()
            advanceUntilIdle()
            assertEquals(0, backend.startCalls)
            controller.continueWithAvailableConnection()
            advanceUntilIdle()
            assertEquals(if (capabilities.mayStart) 1 else 0, backend.startCalls)
            controller.dismiss()
            advanceUntilIdle()
        }
    }

    @Test
    fun `start observes the SDK session without copying protocol state`() = runTest {
        val session = FakeSession(ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026))
        val backend = FakeBackend(session = session)
        val controller = controller(backend)

        controller.start()
        advanceUntilIdle()

        assertEquals(1, backend.startCalls)
        val configuration = requireNotNull(backend.lastConfiguration)
        val engagement = assertIs<ProximitySessionConfiguration.ConventionalNfc>(
            configuration.session,
        )
        assertEquals(ProximityNfcHandover.Negotiated, engagement.handover)
        assertEquals(engagement.retrieval, engagement.qrFallback)
        val retrieval = engagement.retrieval
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

        val engagement = assertIs<ProximitySessionConfiguration.ProvisionalNfcV2>(
            configuration.session,
        )
        val retrieval = engagement
        val bluetooth = assertNotNull(retrieval.bluetoothLowEnergy)
        assertEquals(ProximityBleRoles.CentralClient, bluetooth.roles)
        assertEquals(ProximityBleBearerPolicy.GattOnly, bluetooth.bearerPolicy)
        assertNull(retrieval.qrFallback)
    }

    @Test
    fun `provisional NFCv2 direct profile has no fallback bearer`() {
        val configuration = WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct.configuration()

        val engagement = assertIs<ProximitySessionConfiguration.ProvisionalNfcV2>(
            configuration.session,
        )
        val retrieval = engagement
        assertNull(retrieval.bluetoothLowEnergy)
        assertNull(retrieval.qrFallback)
    }

    @Test
    fun `compatibility profiles preserve engagement choice and narrow data transfer`() {
        for (profile in listOf(WalletDemoProximityTransportProfile.Bluetooth)) {
            val session = assertIs<ProximitySessionConfiguration.ConventionalNfc>(profile.configuration().session)
            assertEquals(ProximityNfcHandover.Negotiated, session.handover)
            assertEquals(session.retrieval, session.qrFallback)
            assertNull(session.retrieval.nfc)
            assertEquals(profile == WalletDemoProximityTransportProfile.Bluetooth, session.retrieval.bluetoothLowEnergy != null)
        }

    }

    @Test
    fun `start snapshots the selected profile before launching the session`() = runTest {
        var selected = WalletDemoProximityTransportProfile.Default
        val session = FakeSession(
            ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026)
        )
        val backend = FakeBackend(session = session)
        val controller = controller(backend) { selected }

        controller.start()
        selected = WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct
        advanceUntilIdle()

        assertIs<ProximitySessionConfiguration.ConventionalNfc>(
            requireNotNull(backend.lastConfiguration).session,
        )
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
    fun `only actual system presentment exempts NFC sessions from background interruption`() = runTest {
        for (state in listOf(
            ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026),
            ProximityState.EngagementReady(listOf(ProximityEngagement.Qr("mdoc:test"))),
            ProximityState.AwaitingRequest(1),
            ProximityState.ReviewRequired(review()),
        )) {
            var presenting = false
            val session = FakeSession(state)
            val controller = WalletDemoProximityController(
                wallet = FakeBackend(session),
                scope = this,
                dispatcher = StandardTestDispatcher(testScheduler),
                systemPresentationActive = { presenting },
            )
            controller.start()
            advanceUntilIdle()
            presenting = true
            controller.handleLifecycleInterruption()
            advanceUntilIdle()
            assertTrue(session.actions.isEmpty())
            presenting = false
            controller.handleLifecycleInterruption()
            advanceUntilIdle()
            assertEquals(listOf<ProximityAction>(ProximityAction.Cancel), session.actions)
            controller.dismiss()
            advanceUntilIdle()
        }
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
    fun `transport and reader settings are combined once for each new session`() = runTest {
        val session = FakeSession(
            ProximityState.Completed(
                exchanges = 1,
                declined = false,
            )
        )
        val backend = FakeBackend(session)
        var profile = WalletDemoProximityTransportProfile.Default
        var policy = ProximityReaderPolicy.AllowAnonymousOrUntrusted
        val controller = WalletDemoProximityController(
            wallet = backend,
            profileProvider = { profile },
            readerTrustSettingsProvider = {
                ProximityReaderTrustSettings(readerPolicy = policy)
            },
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        controller.start()
        advanceUntilIdle()
        profile = WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct
        policy = ProximityReaderPolicy.RequireTrusted
        val first = backend.configurations.single()
        assertIs<ProximitySessionConfiguration.ConventionalNfc>(first.session)
        assertEquals(
            ProximityReaderPolicy.AllowAnonymousOrUntrusted,
            first.readerPolicy,
        )

        controller.dismiss()
        advanceUntilIdle()
        controller.start()
        advanceUntilIdle()
        val second = backend.configurations.last()
        assertIs<ProximitySessionConfiguration.ProvisionalNfcV2>(second.session)
        assertEquals(
            ProximityReaderPolicy.RequireTrusted,
            second.readerPolicy,
        )
    }

    @Test
    fun `revealing prepared QR and NFC never restarts the session and QR brightness follows visibility`() = runTest {
        val qr = ProximityEngagement.Qr("mdoc:stable")
        val session = FakeSession(ProximityState.EngagementReady(listOf(qr, ProximityEngagement.Nfc)))
        val backend = FakeBackend(session)
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        assertNull(controller.state.value.displayedEngagement)
        assertFalse(controller.state.value.qrVisible)
        repeat(3) {
            controller.showEngagement(ProximityEngagementMethod.Qr)
            assertTrue(controller.state.value.qrVisible)
            assertEquals(listOf(qr, ProximityEngagement.Nfc),
                (controller.state.value.sessionState as ProximityState.EngagementReady).engagements)
            controller.showEngagement(ProximityEngagementMethod.Nfc)
            assertFalse(controller.state.value.qrVisible)
        }
        assertEquals(1, backend.startCalls)
        assertEquals(0, session.closeCalls)
        session.mutableState.value = ProximityState.EngagementReady(listOf(qr))
        advanceUntilIdle()
        assertEquals(ProximityEngagementMethod.Qr, controller.state.value.displayedEngagement)
        controller.showEngagement(ProximityEngagementMethod.Nfc)
        assertEquals(listOf(ProximityEngagementMethod.Qr), controller.state.value.engagementChoices)
        assertTrue(controller.state.value.qrVisible)
        controller.dismiss()
        advanceUntilIdle()
        assertFalse(controller.state.value.qrVisible)
    }

    @Test
    fun `connection controls cannot replace a connecting or consent exchange`() = runTest {
        val ready = listOf(ProximityEngagement.Qr("mdoc:stable"), ProximityEngagement.Nfc)
        val session = FakeSession(ProximityState.EngagementReady(ready))
        val backend = FakeBackend(session)
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        controller.showEngagement(ProximityEngagementMethod.Qr)
        for (state in listOf(ProximityState.Connecting(ready), ProximityState.ReviewRequired(review()))) {
            session.mutableState.value = state
            advanceUntilIdle()
            val before = controller.state.value
            controller.showEngagement(ProximityEngagementMethod.Nfc)
            advanceUntilIdle()
            assertEquals(before, controller.state.value)
            assertFalse(controller.state.value.qrVisible)
            assertTrue(controller.state.value.engagementChoices.isEmpty())
            assertEquals(1, backend.startCalls)
            assertEquals(0, session.closeCalls)
        }
        assertTrue(controller.state.value.canApprove)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `single prepared NFC does not advertise QR merely because QR capability exists`() {
        val state = WalletDemoProximityUiState(sessionState = ProximityState.EngagementReady(listOf(ProximityEngagement.Nfc)))
        assertEquals(listOf(ProximityEngagementMethod.Nfc), state.engagementChoices)
        assertEquals(ProximityEngagementMethod.Nfc, state.displayedEngagement)
        assertFalse(state.qrVisible)
    }

    @Test
    fun `retry waits for old session cleanup before starting`() = runTest {
        val closed = CompletableDeferred<Unit>()
        val session = FakeSession(ProximityState.Failed(
            ProximityError(ProximityErrorCategory.Transport, "nfc_failed", "Connection failed", ProximityRecovery.StartNewSession)), closeGate = closed)
        val backend = FakeBackend(session)
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        controller.restart()
        advanceUntilIdle()
        assertEquals(1, session.closeCalls)
        assertEquals(1, backend.startCalls)
        assertTrue(controller.state.value.selections.isEmpty())
        closed.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, backend.startCalls)
        assertIs<ProximitySessionConfiguration.ConventionalNfc>(backend.lastConfiguration?.session)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `terminal NFC denial keeps its settings action available and settings recovery starts a fresh session`() = runTest {
        val denied = ProximityError(ProximityErrorCategory.Capability,
            "nfc_access_not_accepted", "NFC access was not accepted", ProximityRecovery.StartNewSession)
        val session = FakeSession(ProximityState.Failed(denied))
        val backend = FakeBackend(session)
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        val returned = CompletableDeferred<Unit>()
        controller.remediate(ProximityRemediationAction.OpenApplicationSettings, WalletDemoProximityHostActionExecutor { action ->
            assertEquals(ProximityRemediationAction.OpenApplicationSettings, action)
            assertEquals(1, session.closeCalls)
            returned.await()
            ProximityHostActionResult.Completed
        })
        advanceUntilIdle()
        assertEquals(1, backend.startCalls)
        returned.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, backend.startCalls)
        assertTrue(session.actions.isEmpty())
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `actual route is visible in review even when connecting state was never observed`() = runTest {
        val route = ProximityConnectedRoute(ProximityEngagementMethod.Nfc,
            ProximityTransport.BluetoothLowEnergy)
        val session = FakeSession(ProximityState.ReviewRequired(review()), connectedRoute = route)
        val controller = controller(FakeBackend(session))
        controller.start()
        advanceUntilIdle()
        assertEquals(route, controller.state.value.connectedRoute)
        session.mutableState.value = ProximityState.Completed(1, false)
        advanceUntilIdle()
        assertEquals(route, controller.state.value.connectedRoute)
        controller.dismiss()
        advanceUntilIdle()
        assertNull(controller.state.value.connectedRoute)
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

    @Test
    fun `dismissing and reopening also waits for radio cleanup`() = runTest {
        val closeGate = CompletableDeferred<Unit>()
        val session = FakeSession(ProximityState.Preparing(ProximityProfile.Iso180135Edition2Dis2026), closeGate = closeGate)
        val backend = FakeBackend(session)
        val controller = controller(backend)
        controller.start()
        advanceUntilIdle()
        controller.dismiss()
        controller.start()
        advanceUntilIdle()
        assertEquals(1, backend.startCalls)
        closeGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, backend.startCalls)
        controller.dismiss()
        advanceUntilIdle()
    }

    @Test
    fun `explicit NFC host action is available for NFC-only sessions and ignores QR and connected states`() = runTest {
        val nfc = ProximityEngagement.Nfc
        val session = FakeSession(ProximityState.EngagementReady(listOf(nfc)))
        val backend = FakeBackend(session)
        var requests = 0
        val controller = controller(backend, requestNfcPresentment = { requests += 1 })
        controller.start()
        advanceUntilIdle()
        assertNull(controller.state.value.displayedEngagement)
        assertEquals(0, requests)
        controller.showEngagement(ProximityEngagementMethod.Nfc)
        assertEquals(1, requests)
        assertEquals(ProximityEngagementMethod.Nfc, controller.state.value.displayedEngagement)
        for (next in listOf(
            ProximityState.EngagementReady(listOf(ProximityEngagement.Qr("mdoc:stable"))),
            ProximityState.Connecting(listOf(nfc)),
            ProximityState.ReviewRequired(review()),
        )) {
            session.mutableState.value = next
            advanceUntilIdle()
            controller.showEngagement(ProximityEngagementMethod.Nfc)
            assertEquals(1, requests)
        }
        assertEquals(1, backend.startCalls)
        assertEquals(0, session.closeCalls)
        controller.dismiss()
        advanceUntilIdle()
    }

    private fun TestScope.controller(
        backend: ProximityPresentationBackend,
        requestNfcPresentment: (() -> Unit)? = null,
        profileProvider: () -> WalletDemoProximityTransportProfile = {
            WalletDemoProximityTransportProfile.Default
        },
    ): WalletDemoProximityController = WalletDemoProximityController(
        wallet = backend,
        profileProvider = profileProvider,
        scope = this,
        dispatcher = StandardTestDispatcher(testScheduler),
        requestNfcPresentment = requestNfcPresentment,
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
    val lastConfiguration: ProximityConfiguration?
        get() = configurations.lastOrNull()

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
    private val closeGate: CompletableDeferred<Unit>? = null,
    override val connectedRoute: ProximityConnectedRoute? = null,
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
        closeGate?.await()
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
