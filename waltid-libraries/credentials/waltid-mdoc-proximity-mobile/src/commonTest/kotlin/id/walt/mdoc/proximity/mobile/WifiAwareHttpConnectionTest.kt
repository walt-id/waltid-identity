package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WifiAwareHttpConnectionTest {
    @Test
    fun `one TCP stream carries strict sequential mdoc HTTP exchanges`() = runTest {
        val request = request(byteArrayOf(1, 2, 3))
        val raw = FakeWifiAwareRawConnection(
            mutableListOf(request.copyOfRange(0, 17), request.copyOfRange(17, request.size)),
        )
        val connection = WifiAwareHttpConnection(raw, maximumMessageBytes = 16)

        assertContentEquals(byteArrayOf(1, 2, 3), connection.receive()!!.copy())
        connection.send(ImmutableBytes.of(byteArrayOf(9, 8)))

        assertContentEquals(
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: application/cbor\r\n\r\n".encodeToByteArray() +
                byteArrayOf(9, 8),
            raw.writes.single(),
        )
        assertNull(connection.receive())
    }

    @Test
    fun `ambiguous request framing fails closed`() = runTest {
        val raw = FakeWifiAwareRawConnection(
            mutableListOf(
                "POST /mdoc HTTP/1.1\r\nHost: [fe80::1]\r\nContent-Type: application/cbor\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\n0"
                    .encodeToByteArray(),
            ),
        )
        val failure = assertFailsWith<ProximityException> {
            WifiAwareHttpConnection(raw, maximumMessageBytes = 16).receive()
        }

        assertEquals("wifi_aware_http_invalid", failure.error.code)
        assertEquals(listOf(ProximityCloseReason.PROTOCOL_ERROR), raw.closeReasons)
    }

    @Test
    fun `truncated body and oversized length fail closed`() = runTest {
        val truncated = FakeWifiAwareRawConnection(
            mutableListOf(requestHeader(contentLength = 2) + byteArrayOf(1), null),
        )
        val truncatedFailure = assertFailsWith<ProximityException> {
            WifiAwareHttpConnection(truncated, maximumMessageBytes = 16).receive()
        }
        assertEquals("wifi_aware_http_invalid", truncatedFailure.error.code)

        val oversized = FakeWifiAwareRawConnection(
            mutableListOf(requestHeader(contentLength = 17)),
        )
        val oversizedFailure = assertFailsWith<ProximityException> {
            WifiAwareHttpConnection(oversized, maximumMessageBytes = 16).receive()
        }
        assertEquals("wifi_aware_http_invalid", oversizedFailure.error.code)
    }

    private fun request(body: ByteArray): ByteArray = requestHeader(body.size) + body

    private fun requestHeader(contentLength: Int): ByteArray =
        "POST /mdoc HTTP/1.1\r\nHost: [fe80::1]\r\nContent-Type: application/cbor\r\nContent-Length: $contentLength\r\n\r\n"
            .encodeToByteArray()
}

private class FakeWifiAwareRawConnection(
    private val reads: MutableList<ByteArray?>,
) : WifiAwareRawConnection {
    val writes = mutableListOf<ByteArray>()
    val closeReasons = mutableListOf<ProximityCloseReason>()

    override suspend fun read(maximumBytes: Int): ByteArray? {
        val next = if (reads.isEmpty()) null else reads.removeAt(0)
        if (next == null || next.size <= maximumBytes) return next
        reads.add(0, next.copyOfRange(maximumBytes, next.size))
        return next.copyOf(maximumBytes)
    }

    override suspend fun write(bytes: ByteArray) {
        writes += bytes.copyOf()
    }

    override fun close(reason: ProximityCloseReason) {
        closeReasons += reason
    }
}
