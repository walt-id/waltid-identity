package id.walt.proximity.physical

import android.content.ComponentName
import id.walt.mdoc.proximity.mobile.AndroidBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.AndroidNfcHostPlatformAdapter
import id.walt.mobile.test.PhysicalDeviceTest
import id.walt.wallet2.mobile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@PhysicalDeviceTest
class AndroidHolderPhysicalTest {
    @Test(timeout = 240_000)
    fun successDisconnectDuringReviewAndFreshRecovery() = runBlocking {
        PhysicalRun("holder").use { run ->
            run.launch()
            val fixture = PhysicalCredentialFixture.create()
            val coordinator = ProximityCoordinator(fixture.wallet, AndroidBleProximityTransportFactory(run.context),
                AndroidNfcHostPlatformAdapter(run.context, ComponentName(run.context, PhysicalMdocService::class.java)) { run.activity })
            try {
                withTimeout(210.seconds) {
                    for (round in 1..3) {
                        val session = coordinator.start(configuration(run.configuration))
                        try {
                            val ready = withTimeout(30.seconds) { session.state.first {
                                it is ProximityState.EngagementReady || it is ProximityState.Failed
                            } }
                            val engagements = assertIs<ProximityState.EngagementReady>(ready).engagements
                            run.event("holder-ready-$round",
                                "qr" to (engagements.filterIsInstance<ProximityEngagement.Qr>().singleOrNull()?.payload ?: ""),
                                "root" to fixture.root.toHexString(), "wrongRoot" to fixture.untrustedRoot.toHexString())
                            val review = assertIs<ProximityState.ReviewRequired>(withTimeout(60.seconds) {
                                session.state.first { it is ProximityState.ReviewRequired || it is ProximityState.Failed }
                            }).review
                            assertEquals(setOf("given_name", "family_name"), review.documents.single().credentialOptions.single()
                                .requestedElements.map { it.elementIdentifier }.toSet())
                            val submission = ProximitySubmission(listOf(ProximityDocumentSubmission(0, "peer-mdl",
                                setOf("given_name", "family_name").map {
                                    ProximityElementReference(PhysicalCredentialFixture.NAMESPACE, it)
                                }.toSet())))
                            val route = assertNotNull(session.connectedRoute)
                            assertEquals(if (run.configuration.startsWith("nfc")) ProximityEngagementMethod.Nfc else ProximityEngagementMethod.Qr,
                                route.engagement)
                            assertEquals(if (run.configuration == "nfc-direct-disconnect") ProximityTransport.Nfc else ProximityTransport.BluetoothLowEnergy,
                                route.transport)
                            run.event("holder-review-$round", "engagement" to route.engagement.name, "transport" to route.transport.name)
                            if (round == 2) {
                                withTimeout(15.seconds) { session.state.first { it is ProximityState.Failed || it is ProximityState.Cancelled } }
                                assertTrue(ProximityActionType.Approve !in session.state.value.legalActions)
                                assertTrue(session.dispatch(ProximityAction.Approve(review.reviewId, submission)) is ProximityActionResult.Rejected)
                                run.event("holder-rejected-$round", "disclosed" to "false", "approvalRejected" to "true")
                            } else {
                                run.input("approve-$round")
                                assertEquals(ProximityActionResult.Accepted, session.dispatch(ProximityAction.Approve(review.reviewId, submission)))
                                val completed = assertIs<ProximityState.Completed>(withTimeout(30.seconds) {
                                    session.state.first { it is ProximityState.Completed || it is ProximityState.Failed }
                                })
                                assertEquals(submission, assertNotNull(completed.receipt).submission)
                                run.event("holder-completed-$round", "approvedFieldCount" to "2")
                            }
                        } finally { session.close() }
                    }
                    run.event("holder-passed", "rounds" to "3")
                }
            } finally { fixture.runtime.close() }
        }
    }

    private fun configuration(name: String): ProximityConfiguration {
        val ble = ProximityBleConfiguration(
            roles = if (name.endsWith("peripheral")) ProximityBleRoles.PeripheralServer else ProximityBleRoles.CentralClient,
            bearerPolicy = if (name.startsWith("ble-l2cap")) ProximityBleBearerPolicy.PreferL2cap else ProximityBleBearerPolicy.GattOnly)
        return ProximityConfiguration(session = if (name.startsWith("nfc")) {
            ProximitySessionConfiguration.ConventionalNfc(handover = ProximityNfcHandover.Static,
                retrieval = if (name == "nfc-direct-disconnect") ProximityRetrievalOptions(bluetoothLowEnergy = null,
                    nfc = ProximityNfcRetrievalConfiguration(255, 256)) else ProximityRetrievalOptions(bluetoothLowEnergy = ble))
        } else ProximitySessionConfiguration.Qr(ProximityRetrievalOptions(bluetoothLowEnergy = ble)))
    }
}
