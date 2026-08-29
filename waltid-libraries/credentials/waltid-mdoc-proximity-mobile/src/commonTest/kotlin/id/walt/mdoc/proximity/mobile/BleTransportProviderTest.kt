@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.BlePeripheralServerOptions
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleTransportProviderTest {
    private val centralUuid = BleServiceUuid.parse("00112233-4455-6677-8899-aabbccddeeff")
    private val peripheralUuid = BleServiceUuid.parse("ffeeddcc-bbaa-9988-8766-554433221100")
    private val context = EngagementContext(
        profile = MdocProximityProfile.ISO_18013_5_ED2_DIS_2026,
        maximumMessageBytes = 1024,
        engagementMode = MdocEngagementMode.Qr,
    )

    @Test
    fun `capability keeps runtime failure separate from implemented profile support`() = runTest {
        val platform = FakePlatform(
            BleProximityAvailability.Unavailable("permission_missing", "Bluetooth permission is missing")
        )
        val provider = provider(BleMdocRoles.CentralClient(centralUuid), platform)

        val capability = provider.capability(context)

        assertTrue(capability.implemented)
        assertTrue(capability.profilePermitted)
        assertFalse(capability.runtimeAvailable)
        assertFalse(capability.sessionSelected)
        assertEquals("permission_missing", capability.unavailableReason?.code)
    }

    @Test
    fun `role selection and availability reject ambiguous preflight states`() {
        assertEquals(BleMdocRoleSelection.CENTRAL_CLIENT, BleMdocRoles.CentralClient(centralUuid).selection)
        assertEquals(BleMdocRoleSelection.PERIPHERAL_SERVER, BleMdocRoles.PeripheralServer(peripheralUuid).selection)
        assertEquals(BleMdocRoleSelection.DUAL, BleMdocRoles.Dual(centralUuid, peripheralUuid).selection)
        assertFailsWith<IllegalArgumentException> { BleProximityAvailability.Unavailable("", "Unavailable") }
        assertFailsWith<IllegalArgumentException> { BleProximityAvailability.Unavailable("unavailable", "") }
    }

    @Test
    fun `dual preparation exposes only successfully prepared role without an illegal method`() = runTest {
        val platform = FakePlatform().apply { centralFailure = IllegalStateException("scanner failed") }
        val provider = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform)

        val prepared = provider.prepare(context, this)
        val method = assertIs<DeviceRetrievalMethod.Ble>(prepared.connectionMethod)

        assertNull(method.centralMode)
        assertContentEquals(peripheralUuid.encoded().copy(), method.peripheralMode!!.uuid)
        assertEquals(0x80u, assertIs<BlePeripheralEndpoint.Mdoc>(method.peripheralEndpoint).options.psm)
    }

    @Test
    fun `reader-selected combined BLE offer prefers and preserves the reader peripheral endpoint`() = runTest {
        val readerUuid = BleServiceUuid.parse("12345678-1234-4abc-9234-1234567890ab")
        val offered = DeviceRetrievalMethod.Ble(
            peripheralMode = BlePeripheralMode(peripheralUuid.encoded().copy()),
            centralMode = BleCentralMode(readerUuid.encoded().copy()),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 0x81u)),
        )
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform)
        val offer = ReaderSelectedTransportOffer.Method(offered)

        assertTrue(provider.acceptsReaderOffer(offer))
        val prepared = provider.prepareReaderSelected(offer, context, this)
        val selected = assertIs<DeviceRetrievalMethod.Ble>(prepared.connectionMethod)

        assertNull(selected.peripheralMode)
        assertContentEquals(readerUuid.encoded().copy(), selected.centralMode!!.uuid)
        assertEquals(
            BlePeripheralServerOptions(psm = 0x81u),
            assertIs<BlePeripheralEndpoint.Reader>(selected.peripheralEndpoint).options,
        )
        assertEquals(readerUuid, platform.central.serviceUuid)
        assertTrue(platform.peripheral.closeReasons.isEmpty())
    }

    @Test
    fun `reader-selected NFC accepts an arbitrary exact 128-bit service UUID`() = runTest {
        val readerUuid = BleServiceUuid.parse("e4eaff77-2b04-2453-451a-6c2abf52f590")
        val offered = DeviceRetrievalMethod.Ble(
            centralMode = BleCentralMode(readerUuid.encoded().copy()),
            peripheralEndpoint = BlePeripheralEndpoint.Reader(BlePeripheralServerOptions(psm = 0xf3u)),
        )
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform)

        val prepared = provider.prepareReaderSelected(
            ReaderSelectedTransportOffer.Method(offered),
            context.copy(engagementMode = MdocEngagementMode.Nfc),
            this,
        )

        assertEquals(readerUuid, platform.central.serviceUuid)
        assertContentEquals(
            readerUuid.encoded().copy(),
            assertIs<DeviceRetrievalMethod.Ble>(prepared.connectionMethod).centralMode!!.uuid,
        )
    }

    @Test
    fun `conventional reader offer can defer the holder peripheral endpoint until preparation`() = runTest {
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform)
        val offer = ReaderSelectedTransportOffer.BlePeripheralServer

        assertTrue(provider.acceptsReaderOffer(offer))
        val prepared = provider.prepareReaderSelected(offer, context, this)
        val selected = assertIs<DeviceRetrievalMethod.Ble>(prepared.connectionMethod)

        assertNull(selected.centralMode)
        assertContentEquals(peripheralUuid.encoded().copy(), selected.peripheralMode!!.uuid)
        assertEquals(0x80u, assertIs<BlePeripheralEndpoint.Mdoc>(selected.peripheralEndpoint).options.psm)
        assertEquals(peripheralUuid, platform.peripheral.serviceUuid)
    }

    @Test
    fun `role preparation timeout is normalized before engagement is advertised`() = runTest {
        val platform = FakePlatform().apply { centralPreparationGate = CompletableDeferred() }
        val provider = provider(BleMdocRoles.CentralClient(centralUuid), platform)

        val failure = assertFailsWith<ProximityException> { provider.prepare(context, this) }

        assertEquals("ble_prepare_failed", failure.error.code)
        assertEquals("ble_prepare_timeout", assertIs<ProximityException>(failure.cause).error.code)
    }

    @Test
    fun `central preparation receives exact derived Ident and loser closes after role race`() = runTest {
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform)
        val prepared = provider.prepare(context, this)
        val peripheralRaw = FakeRawConnection(BleRawBearer.GATT, 23)
        platform.peripheral.connection.complete(peripheralRaw)

        val connection = prepared.awaitConnection()

        assertContentEquals(BleIdent.derive(ImmutableBytes.of(ByteArray(32) { it.toByte() })), platform.centralIdent)
        assertEquals(listOf(ProximityCloseReason.LOST_RACE), platform.central.closeReasons)
        assertTrue(platform.peripheral.closeReasons.isEmpty())
        connection.close(ProximityCloseReason.COMPLETED)
        assertEquals(1, peripheralRaw.finishCount)
    }

    @Test
    fun `prepared transport forwards completion to its selected connection exactly once`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.CentralClient(centralUuid), platform).prepare(context, this)
        val raw = FakeRawConnection(BleRawBearer.GATT, 23)
        platform.central.connection.complete(raw)
        prepared.awaitConnection()

        prepared.close(ProximityCloseReason.COMPLETED)
        prepared.close(ProximityCloseReason.COMPLETED)

        assertEquals(1, raw.finishCount)
        assertEquals(listOf(ProximityCloseReason.COMPLETED), raw.closeReasons)
        assertEquals(listOf(ProximityCloseReason.COMPLETED), platform.central.closeReasons)
    }

    @Test
    fun `prepared listener times out once and cannot be awaited again`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.CentralClient(centralUuid), platform).prepare(context, this)

        val timeout = assertFailsWith<ProximityException> { prepared.awaitConnection() }
        val secondAwait = assertFailsWith<ProximityException> { prepared.awaitConnection() }

        assertEquals("ble_connection_timeout", timeout.error.code)
        assertEquals("ble_listener_unavailable", secondAwait.error.code)
        assertEquals(listOf(ProximityCloseReason.TIMEOUT), platform.central.closeReasons)
    }

    @Test
    fun `dual role timeout preserves a failed role's proximity error`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform).prepare(context, this)
        platform.central.connection.completeExceptionally(
            ProximityException(
                ProximityError.Security(
                    "ble_ident_mismatch",
                    "The reader BLEIdent does not match EDeviceKeyBytes",
                )
            )
        )

        val failure = assertFailsWith<ProximityException> { prepared.awaitConnection() }

        assertEquals("ble_ident_mismatch", failure.error.code)
        assertEquals(listOf(ProximityCloseReason.CANCELLED), platform.central.closeReasons)
        assertEquals(listOf(ProximityCloseReason.CANCELLED), platform.peripheral.closeReasons)
    }

    @Test
    fun `GATT connection serializes complete messages and reports orderly disconnect`() = runTest {
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.CentralClient(centralUuid), platform)
        val prepared = provider.prepare(context, this)
        val raw = FakeRawConnection(BleRawBearer.GATT, 5)
        platform.central.connection.complete(raw)
        val connection = prepared.awaitConnection()

        connection.send(ImmutableBytes.of(byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
        raw.incomingPackets.send(byteArrayOf(1, 9, 8, 7, 6))
        raw.incomingPackets.send(byteArrayOf(0, 5, 4))

        assertEquals(listOf(5, 4), raw.writes.map(ByteArray::size))
        assertContentEquals(byteArrayOf(9, 8, 7, 6, 5, 4), connection.receive()!!.copy())
        raw.incomingPackets.close()
        assertNull(connection.receive())
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), raw.closeReasons)
    }

    @Test
    fun `L2CAP connection rejects concurrent receive and truncated close`() = runTest {
        val platform = FakePlatform()
        val provider = provider(BleMdocRoles.CentralClient(centralUuid), platform)
        val prepared = provider.prepare(context, this)
        val raw = FakeRawConnection(BleRawBearer.L2CAP, null)
        platform.central.connection.complete(raw)
        val connection = prepared.awaitConnection()
        supervisorScope {
            val firstReceive = async(start = CoroutineStart.UNDISPATCHED) { connection.receive() }

            assertFailsWith<ProximityException> { connection.receive() }
            raw.incomingPackets.send(byteArrayOf(0, 0, 0, 4, 1, 2))
            raw.incomingPackets.close()
            val failure = assertFailsWith<ProximityException> { firstReceive.await() }
            assertEquals("truncated_ble_message", failure.error.code)
            assertEquals(listOf(ProximityCloseReason.PROTOCOL_ERROR), raw.closeReasons)
        }
    }

    @Test
    fun `stalled write times out and closes the connection`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.CentralClient(centralUuid), platform).prepare(context, this)
        val raw = FakeRawConnection(BleRawBearer.GATT, 23).apply {
            writeGate = CompletableDeferred()
        }
        platform.central.connection.complete(raw)
        val connection = prepared.awaitConnection()

        val failure = assertFailsWith<ProximityException> {
            connection.send(ImmutableBytes.of(byteArrayOf(1)))
        }

        assertEquals("ble_inactivity_timeout", failure.error.code)
        assertEquals(listOf(ProximityCloseReason.TIMEOUT), raw.closeReasons)
    }

    @Test
    fun `platform write failure is normalized at the shared transport boundary`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.CentralClient(centralUuid), platform).prepare(context, this)
        val cause = IllegalStateException("platform write failed")
        val raw = FakeRawConnection(BleRawBearer.GATT, 23).apply { writeFailure = cause }
        platform.central.connection.complete(raw)
        val connection = prepared.awaitConnection()

        val failure = assertFailsWith<ProximityException> {
            connection.send(ImmutableBytes.of(byteArrayOf(1)))
        }

        assertEquals("ble_send_failed", failure.error.code)
        assertIs<IllegalStateException>(failure.cause)
        assertEquals(cause.message, failure.cause?.message)
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), raw.closeReasons)
    }

    @Test
    fun `stalled orderly finish reports timeout and closes with timeout reason`() = runTest {
        val platform = FakePlatform()
        val prepared = provider(BleMdocRoles.CentralClient(centralUuid), platform).prepare(context, this)
        val raw = FakeRawConnection(BleRawBearer.GATT, 23).apply {
            finishGate = CompletableDeferred()
        }
        platform.central.connection.complete(raw)
        val connection = prepared.awaitConnection()

        val failure = assertFailsWith<ProximityException> {
            connection.close(ProximityCloseReason.COMPLETED)
        }

        assertEquals("ble_inactivity_timeout", failure.error.code)
        assertEquals(listOf(ProximityCloseReason.TIMEOUT), raw.closeReasons)
    }

    @Test
    fun `session cancellation closes every prepared role idempotently`() = runTest {
        val platform = FakePlatform()
        val sessionJob = Job()
        val sessionScope = CoroutineScope(coroutineContext + sessionJob)
        val prepared = provider(BleMdocRoles.Dual(centralUuid, peripheralUuid), platform).prepare(context, sessionScope)

        sessionJob.cancel()
        prepared.close(ProximityCloseReason.CANCELLED)
        prepared.close(ProximityCloseReason.CANCELLED)

        assertTrue(platform.central.closeReasons.isNotEmpty())
        assertTrue(platform.peripheral.closeReasons.isNotEmpty())
    }

    private fun provider(roles: BleMdocRoles, platform: FakePlatform) = DefaultBleProximityTransportProvider(
        BleProximityTransportConfiguration(
            roles = roles,
            eDeviceKeyBytes = ImmutableBytes.of(ByteArray(32) { it.toByte() }),
        ),
        platform,
    )
}

private class FakePlatform(
    var platformCapability: BleProximityAvailability = BleProximityAvailability.Available,
) : BlePlatformAdapter {
    val central = FakePreparedRole(BlePlatformRole.CENTRAL_CLIENT)
    val peripheral = FakePreparedRole(BlePlatformRole.PERIPHERAL_SERVER, 0x80u)
    var centralFailure: Throwable? = null
    var peripheralFailure: Throwable? = null
    var centralPreparationGate: CompletableDeferred<Unit>? = null
    var centralIdent: ByteArray? = null

    override suspend fun capability(): BleProximityAvailability = platformCapability

    override suspend fun prepareCentralClient(
        serviceUuid: BleServiceUuid,
        expectedIdent: ByteArray,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole {
        centralPreparationGate?.await()
        centralFailure?.let { throw it }
        central.service = serviceUuid
        centralIdent = expectedIdent.copyOf()
        return central
    }

    override suspend fun preparePeripheralServer(
        serviceUuid: BleServiceUuid,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole {
        peripheralFailure?.let { throw it }
        peripheral.service = serviceUuid
        return peripheral
    }
}

private class FakePreparedRole(
    override val role: BlePlatformRole,
    override val l2capPsm: UInt? = null,
) : BlePreparedPlatformRole {
    lateinit var service: BleServiceUuid
    override val serviceUuid: BleServiceUuid get() = service
    val connection = CompletableDeferred<BleRawConnection>()
    val closeReasons = mutableListOf<ProximityCloseReason>()
    private var closed = false

    override suspend fun awaitConnection(): BleRawConnection = connection.await()

    override fun close(reason: ProximityCloseReason) {
        if (closed) return
        closed = true
        closeReasons += reason
        if (!connection.isCompleted) connection.cancel()
    }
}

private class FakeRawConnection(
    override val bearer: BleRawBearer,
    override val maximumGattPacketBytes: Int?,
) : BleRawConnection {
    val incomingPackets = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming = incomingPackets
    val writes = mutableListOf<ByteArray>()
    var finishCount = 0
    var writeGate: CompletableDeferred<Unit>? = null
    var writeFailure: Throwable? = null
    var finishGate: CompletableDeferred<Unit>? = null
    val closeReasons = mutableListOf<ProximityCloseReason>()
    private var closed = false

    override suspend fun write(bytes: ByteArray) {
        writeGate?.await()
        writeFailure?.let { throw it }
        writes += bytes.copyOf()
    }

    override suspend fun finish() {
        finishGate?.await()
        finishCount++
    }

    override fun close(reason: ProximityCloseReason) {
        if (closed) return
        closed = true
        closeReasons += reason
        incomingPackets.close()
    }
}
