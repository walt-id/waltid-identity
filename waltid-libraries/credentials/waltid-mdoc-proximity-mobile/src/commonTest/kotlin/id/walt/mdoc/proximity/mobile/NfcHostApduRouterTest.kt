@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NfcHostApduRouterTest {
    @Test
    fun `conventional retrieval defers exactly one APDU until the holder responds`() = runTest {
        val method = DeviceRetrievalMethod.Nfc(255u, 256u)
        val selected = mutableListOf<NfcHostApplication>()
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(method, 1024),
            nfcV2 = null,
            onApplicationSelected = { selected += it },
        )

        assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.DATA_TRANSFER)))
        val response = async { router.process(envelope(NfcDo53.encode(byteArrayOf(1, 2, 3)))) }
        runCurrent()

        assertContentEquals(byteArrayOf(1, 2, 3), router.retrievalConnection.receive()!!.copy())
        router.retrievalConnection.send(ImmutableBytes.of(byteArrayOf(4, 5)))

        assertContentEquals(byteArrayOf(4, 5), NfcDo53.decode(
            NfcResponseApdu.decode(response.await().copy()).data.copy(),
            1024,
        ))
        assertEquals(listOf(NfcHostApplication.RETRIEVAL), selected)
    }

    @Test
    fun `deactivation invalidates a pending holder response`() = runTest {
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
        )
        router.process(select(MdocNfcAid.DATA_TRANSFER))
        val response = async { runCatching { router.process(envelope(NfcDo53.encode(byteArrayOf(1)))) } }
        runCurrent()
        assertContentEquals(byteArrayOf(1), router.retrievalConnection.receive()!!.copy())

        router.deactivate(ProximityCloseReason.PEER_DISCONNECTED)

        assertEquals(true, response.await().isFailure)
        assertNull(router.retrievalConnection.receive())
    }

    @Test
    fun `deactivation is terminal and notifies its owner exactly once`() = runTest {
        val closeReasons = mutableListOf<ProximityCloseReason>()
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
            onDeactivated = closeReasons::add,
        )

        router.deactivate(ProximityCloseReason.PEER_DISCONNECTED)
        router.deactivate(ProximityCloseReason.CANCELLED)

        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), closeReasons)
        val response = router.process(select(MdocNfcAid.DATA_TRANSFER))
        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, response)
        assertTrue(router.retrievalConnection.receive() == null)
    }

    @Test
    fun `selection policy rejection does not advance the retrieval state machine`() = runTest {
        var allowed = false
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
            canSelectApplication = { allowed },
        )

        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, router.process(select(MdocNfcAid.DATA_TRANSFER)))
        allowed = true
        assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.DATA_TRANSFER)))
    }

    @Test
    fun `deactivation winning during selection policy evaluation suppresses selection ownership`() = runTest {
        val policyEntered = CompletableDeferred<Unit>()
        val releasePolicy = CompletableDeferred<Unit>()
        val selected = mutableListOf<NfcHostApplication>()
        val deactivated = mutableListOf<ProximityCloseReason>()
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
            canSelectApplication = {
                policyEntered.complete(Unit)
                releasePolicy.await()
                true
            },
            onApplicationSelected = selected::add,
            onDeactivated = deactivated::add,
        )

        val selection = async { router.process(select(MdocNfcAid.DATA_TRANSFER)) }
        policyEntered.await()
        val close = async { router.deactivate(ProximityCloseReason.PEER_DISCONNECTED) }
        runCurrent()
        assertTrue(close.isActive)
        releasePolicy.complete(Unit)

        assertStatus(NfcStatusWord.CONDITIONS_NOT_SATISFIED, selection.await())
        close.await()
        assertEquals(emptyList(), selected)
        assertEquals(listOf(ProximityCloseReason.PEER_DISCONNECTED), deactivated)
    }

    @Test
    fun `failed application selection does not commit its ownership callback`() = runTest {
        val selected = mutableListOf<NfcHostApplication>()
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
            onApplicationSelected = selected::add,
        )
        val wrongParameters = NfcCommandApdu(
            cla = 0u,
            instruction = 0xa4u,
            parameter1 = 0x04u,
            parameter2 = 0u,
            data = MdocNfcAid.DATA_TRANSFER,
        ).encode()

        assertStatus(NfcStatusWord.INCORRECT_PARAMETERS, router.process(wrongParameters))
        assertEquals(emptyList(), selected)
        assertStatus(NfcStatusWord.SUCCESS, router.process(select(MdocNfcAid.DATA_TRANSFER)))
        assertEquals(listOf(NfcHostApplication.RETRIEVAL), selected)
    }

    @Test
    fun `cancelled reader exchange invalidates its holder response ownership`() = runTest {
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 1024),
            nfcV2 = null,
        )
        router.process(select(MdocNfcAid.DATA_TRANSFER))
        val cancelled = async { router.process(envelope(NfcDo53.encode(byteArrayOf(1)))) }
        runCurrent()
        assertContentEquals(byteArrayOf(1), router.retrievalConnection.receive()!!.copy())
        cancelled.cancel()
        runCurrent()
        assertFailsWith<IllegalArgumentException> {
            router.retrievalConnection.send(ImmutableBytes.of(byteArrayOf(9)))
        }
        assertStatus(
            NfcStatusWord.CONDITIONS_NOT_SATISFIED,
            router.process(envelope(NfcDo53.encode(byteArrayOf(2)))),
        )
    }

    @Test
    fun `invalid holder response terminally releases its APDU ownership`() = runTest {
        val router = NfcHostApduRouter(
            engagement = null,
            retrieval = NfcRetrievalApduProcessor(DeviceRetrievalMethod.Nfc(255u, 256u), 4),
            nfcV2 = null,
        )
        router.process(select(MdocNfcAid.DATA_TRANSFER))
        val response = async { runCatching { router.process(envelope(NfcDo53.encode(byteArrayOf(1)))) } }
        runCurrent()
        assertContentEquals(byteArrayOf(1), router.retrievalConnection.receive()!!.copy())

        assertFailsWith<IllegalArgumentException> {
            router.retrievalConnection.send(ImmutableBytes.of(ByteArray(5)))
        }
        assertTrue(response.await().isFailure)
        assertStatus(
            NfcStatusWord.CONDITIONS_NOT_SATISFIED,
            router.process(envelope(NfcDo53.encode(byteArrayOf(2)))),
        )
    }

    private fun select(aid: ImmutableBytes): ByteArray = NfcCommandApdu(
        cla = 0u,
        instruction = 0xa4u,
        parameter1 = 0x04u,
        parameter2 = if (aid == MdocNfcAid.DATA_TRANSFER) 0x0cu else 0u,
        data = aid,
    ).encode()

    private fun envelope(payload: ByteArray): ByteArray = NfcCommandApdu(
        cla = 0u,
        instruction = 0xc3u,
        parameter1 = 0u,
        parameter2 = 0u,
        data = ImmutableBytes.of(payload),
        expectedResponseDataLength = 256,
    ).encode()

    private fun assertStatus(expected: UShort, response: ImmutableBytes) {
        assertEquals(expected, NfcResponseApdu.decode(response.copy()).statusWord)
    }
}
