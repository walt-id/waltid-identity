@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package id.walt.mdoc.proximity

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class EngagementCoordinatorTest {
    @Test
    fun `readiness and engaged connection reject contradictory states`() {
        val reason = ProximityError.Capability("nfc_unavailable", "NFC is unavailable")
        assertFailsWith<IllegalArgumentException> {
            MdocEngagementReadiness(
                qrPayload = null,
                availableTransports = setOf(ProximityTransportKind.NFC),
                unavailableTransports = mapOf(ProximityTransportKind.NFC to reason),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MdocEngagedConnection(
                engagementMode = MdocEngagementMode.Qr,
                deviceEngagement = ImmutableBytes.of(byteArrayOf(1)),
                sessionHandover = MdocSessionHandover.NfcConnection(ImmutableBytes.of(byteArrayOf(2))),
                connection = TrackingConnection(ProximityTransportKind.NFC),
            )
        }
    }

    @Test
    fun `first completed engagement wins and preserves its exact handover`() = runTest {
        val qr = TrackingEngagement(MdocEngagementMode.Qr, waitForever = true)
        val handoverSelect = ImmutableBytes.of(byteArrayOf(1, 2, 3))
        val handoverRequest = ImmutableBytes.of(byteArrayOf(4, 5, 6))
        val nfcConnection = TrackingConnection(ProximityTransportKind.NFC)
        val nfc = TrackingEngagement(
            MdocEngagementMode.Nfc,
            result = MdocEngagedConnection(
                engagementMode = MdocEngagementMode.Nfc,
                deviceEngagement = ImmutableBytes.of(byteArrayOf(7, 8)),
                sessionHandover = MdocSessionHandover.NfcConnection(handoverSelect, handoverRequest),
                connection = nfcConnection,
            ),
        )

        val winner = MdocEngagementCoordinator().awaitWinner(prepared(qr, nfc))

        assertSame(nfc, winner.source)
        assertSame(nfcConnection, winner.engaged.connection)
        assertContentEquals(byteArrayOf(7, 8), winner.engaged.deviceEngagement.copy())
        assertEquals(MdocSessionHandover.NfcConnection(handoverSelect, handoverRequest), winner.engaged.sessionHandover)
        assertEquals(listOf(ProximityCloseReason.LOST_RACE), qr.closeReasons)
        assertEquals(emptyList(), nfc.closeReasons)
    }

    @Test
    fun `cancelling engagement selection closes every source exactly once`() = runTest {
        val qr = TrackingEngagement(MdocEngagementMode.Qr, waitForever = true)
        val nfc = TrackingEngagement(MdocEngagementMode.Nfc, waitForever = true)
        val selection = async { MdocEngagementCoordinator().awaitWinner(prepared(qr, nfc)) }
        runCurrent()

        selection.cancelAndJoin()

        assertEquals(listOf(ProximityCloseReason.LOST_RACE), qr.closeReasons)
        assertEquals(listOf(ProximityCloseReason.LOST_RACE), nfc.closeReasons)
    }

    @Test
    fun `a source-local cancellation is a failed candidate rather than a stalled race`() = runTest {
        val cancelling = object : PreparedMdocEngagement {
            override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Nfc)
            override val readiness = MdocEngagementReadiness(
                qrPayload = null,
                availableTransports = setOf(ProximityTransportKind.NFC),
                unavailableTransports = emptyMap(),
            )
            val closeReasons = mutableListOf<ProximityCloseReason>()

            override suspend fun awaitConnection(): MdocEngagedConnection =
                throw CancellationException("The NFC source stopped itself")

            override suspend fun close(reason: ProximityCloseReason) {
                closeReasons += reason
            }
        }

        val failure = assertFailsWith<ProximityException> {
            MdocEngagementCoordinator().awaitWinner(prepared(cancelling))
        }

        assertEquals("engagement_failed", failure.error.code)
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), cancelling.closeReasons)
    }

    @Test
    fun `engagement completed after cancellation is still closed`() = runTest {
        val release = CompletableDeferred<Unit>()
        val connection = TrackingConnection(ProximityTransportKind.NFC)
        val late = TrackingEngagement(
            mode = MdocEngagementMode.Nfc,
            nonCancellableRelease = release,
            result = MdocEngagedConnection(
                MdocEngagementMode.Nfc,
                ImmutableBytes.of(byteArrayOf(9)),
                MdocSessionHandover.ProvisionalNfcV2(
                    ImmutableBytes.of(byteArrayOf(1)),
                    ImmutableBytes.of(byteArrayOf(2)),
                ),
                connection,
            ),
        )
        val selection = async { MdocEngagementCoordinator().awaitWinner(prepared(late)) }
        runCurrent()

        selection.cancel()
        release.complete(Unit)
        selection.join()

        assertEquals(listOf(ProximityCloseReason.CANCELLED), late.closeReasons)
    }

    @Test
    fun `engaged connection with a mode not owned by its source is closed and rejected`() = runTest {
        val source = TrackingEngagement(
            mode = MdocEngagementMode.Nfc,
            result = MdocEngagedConnection(
                engagementMode = MdocEngagementMode.Qr,
                deviceEngagement = ImmutableBytes.of(byteArrayOf(1)),
                sessionHandover = MdocSessionHandover.Qr,
                connection = TrackingConnection(ProximityTransportKind.NFC),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MdocEngagementCoordinator().awaitWinner(prepared(source))
        }

        assertEquals(listOf(ProximityCloseReason.CANCELLED), source.closeReasons)
    }

    @Test
    fun `prepared engagement with mismatched modes is closed and rejected`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateMdocTestKey("engagement-mode-mismatch", setOf(KeyUsage.KEY_AGREEMENT))
        val candidate = TrackingEngagement(MdocEngagementMode.Nfc, waitForever = true)
        val source = object : MdocEngagementSource {
            override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)

            override suspend fun prepare(
                context: MdocEngagementPreparationContext,
                sessionScope: CoroutineScope,
            ): PreparedMdocEngagement = candidate
        }
        val profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026

        try {
            assertFailsWith<IllegalArgumentException> {
                MdocEngagementCoordinator().prepare(
                    sources = listOf(source),
                    context = MdocEngagementPreparationContext(
                        eDeviceKey = key,
                        engagementContext = EngagementContext(profile, 4096, MdocEngagementMode.Qr),
                        capabilities = MdocSessionCapabilities.forSession(profile, key, emptySet()),
                        limits = MdocProximityLimits(maximumSessionMessageBytes = 4096),
                    ),
                    sessionScope = this,
                )
            }
        } finally {
            key.capabilities.deleter?.delete()
            runtime.close()
        }

        assertEquals(listOf(ProximityCloseReason.CANCELLED), candidate.closeReasons)
    }

    @Test
    fun `prepare falls back only for typed availability failures`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateMdocTestKey("engagement-prepare-failures", setOf(KeyUsage.KEY_AGREEMENT))
        val profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026
        val context = MdocEngagementPreparationContext(
            eDeviceKey = key,
            engagementContext = EngagementContext(profile, 4096, MdocEngagementMode.Qr),
            capabilities = MdocSessionCapabilities.forSession(profile, key, emptySet()),
            limits = MdocProximityLimits(maximumSessionMessageBytes = 4096),
        )
        val unavailable = object : MdocEngagementSource {
            override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)
            override suspend fun prepare(
                context: MdocEngagementPreparationContext,
                sessionScope: CoroutineScope,
            ): PreparedMdocEngagement = throw ProximityException(
                ProximityError.Capability("nfc_unavailable", "NFC is unavailable")
            )
        }
        val available = TrackingEngagement(MdocEngagementMode.Nfc, waitForever = true)
        val availableSource = object : MdocEngagementSource {
            override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Nfc)
            override suspend fun prepare(
                context: MdocEngagementPreparationContext,
                sessionScope: CoroutineScope,
            ): PreparedMdocEngagement = available
        }

        try {
            val prepared = MdocEngagementCoordinator().prepare(
                listOf(unavailable, availableSource),
                context,
                this,
            )
            assertEquals(listOf(available), prepared.sources)

            val preparedBeforeProtocolFailure = TrackingEngagement(MdocEngagementMode.Nfc, waitForever = true)
            val sourceBeforeProtocolFailure = object : MdocEngagementSource {
                override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Nfc)
                override suspend fun prepare(
                    context: MdocEngagementPreparationContext,
                    sessionScope: CoroutineScope,
                ): PreparedMdocEngagement = preparedBeforeProtocolFailure
            }
            val invalidProtocolSource = object : MdocEngagementSource {
                override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)
                override suspend fun prepare(
                    context: MdocEngagementPreparationContext,
                    sessionScope: CoroutineScope,
                ): PreparedMdocEngagement = throw ProximityException(
                    ProximityError.Protocol("invalid_engagement", "Invalid engagement state")
                )
            }
            val protocolFailure = assertFailsWith<ProximityException> {
                MdocEngagementCoordinator().prepare(
                    listOf(sourceBeforeProtocolFailure, invalidProtocolSource),
                    context,
                    this,
                )
            }
            assertEquals("invalid_engagement", protocolFailure.error.code)
            assertEquals(
                listOf(ProximityCloseReason.CANCELLED),
                preparedBeforeProtocolFailure.closeReasons,
            )

            val invalid = object : MdocEngagementSource {
                override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Qr)
                override suspend fun prepare(
                    context: MdocEngagementPreparationContext,
                    sessionScope: CoroutineScope,
                ): PreparedMdocEngagement = error("invalid source state")
            }
            val preparedBeforeFailure = TrackingEngagement(MdocEngagementMode.Nfc, waitForever = true)
            val sourceBeforeFailure = object : MdocEngagementSource {
                override val modes: Set<MdocEngagementMode> = setOf(MdocEngagementMode.Nfc)
                override suspend fun prepare(
                    context: MdocEngagementPreparationContext,
                    sessionScope: CoroutineScope,
                ): PreparedMdocEngagement = preparedBeforeFailure
            }

            assertFailsWith<IllegalStateException> {
                MdocEngagementCoordinator().prepare(
                    listOf(sourceBeforeFailure, invalid),
                    context,
                    this,
                )
            }
            assertEquals(listOf(ProximityCloseReason.CANCELLED), preparedBeforeFailure.closeReasons)
        } finally {
            key.capabilities.deleter?.delete()
            runtime.close()
        }
    }

    private fun prepared(vararg sources: PreparedMdocEngagement): PreparedMdocEngagements =
        PreparedMdocEngagements(
            sources.toList(),
            MdocEngagementReadiness(
                qrPayload = null,
                availableTransports = sources.flatMap { it.readiness.availableTransports }.toSet(),
                unavailableTransports = emptyMap(),
            ),
        )

    private class TrackingEngagement(
        mode: MdocEngagementMode,
        private val result: MdocEngagedConnection? = null,
        private val waitForever: Boolean = false,
        private val nonCancellableRelease: CompletableDeferred<Unit>? = null,
    ) : PreparedMdocEngagement {
        override val modes: Set<MdocEngagementMode> = setOf(mode)
        override val readiness = MdocEngagementReadiness(null, setOf(ProximityTransportKind.NFC), emptyMap())
        val closeReasons = mutableListOf<ProximityCloseReason>()

        override suspend fun awaitConnection(): MdocEngagedConnection = when {
            waitForever -> awaitCancellation()
            nonCancellableRelease != null -> withContext(NonCancellable) {
                nonCancellableRelease.await()
                requireNotNull(result)
            }
            else -> requireNotNull(result)
        }

        override suspend fun close(reason: ProximityCloseReason) {
            if (closeReasons.isEmpty()) closeReasons += reason
        }
    }

    private class TrackingConnection(override val kind: ProximityTransportKind) : ProximityConnection {
        override suspend fun receive(): ImmutableBytes? = null
        override suspend fun send(message: ImmutableBytes) = Unit
        override suspend fun close(reason: ProximityCloseReason) = Unit
    }
}
