package id.walt.statuslist.codec

import id.walt.statuslist.model.IndexedStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecRoundTripTest {
    @Test
    fun `token status list round-trip preserves values`() {
        val statuses = listOf(
            IndexedStatus(0, 1u),
            IndexedStatus(3, 1u),
            IndexedStatus(15, 1u),
        )
        val encoded = TokenStatusListCodec.encode(statuses, statusSize = 1)
        val decoded = TokenStatusListCodec.decode(encoded)
        statuses.forEach { assertEquals(it.value, TokenStatusListCodec.readStatus(decoded, it.index, 1)) }
        assertEquals(0u, TokenStatusListCodec.readStatus(decoded, 1, 1))
    }

    @Test
    fun `bitstring status list round-trip preserves values`() {
        val statuses = listOf(IndexedStatus(0, 1u), IndexedStatus(9, 1u))
        val encoded = BitstringStatusListCodec.encode(statuses)
        val decoded = BitstringStatusListCodec.decode(encoded)
        statuses.forEach { assertEquals(it.value, BitstringStatusListCodec.readStatus(decoded, it.index)) }
        assertEquals(0u, BitstringStatusListCodec.readStatus(decoded, 1))
    }

    @Test
    fun `status list 2021 round-trip preserves values`() {
        val statuses = listOf(IndexedStatus(0, 1u), IndexedStatus(12, 1u))
        val encoded = StatusList2021Codec.encode(statuses)
        val decoded = StatusList2021Codec.decode(encoded)
        statuses.forEach { assertEquals(it.value, StatusList2021Codec.readStatus(decoded, it.index)) }
    }

    @Test
    fun `revocation list 2020 round-trip preserves values`() {
        val statuses = listOf(IndexedStatus(0, 1u), IndexedStatus(20, 1u))
        val encoded = RevocationList2020Codec.encode(statuses)
        val decoded = RevocationList2020Codec.decode(encoded)
        statuses.forEach { assertEquals(it.value, RevocationList2020Codec.readStatus(decoded, it.index)) }
    }

    @Test
    fun `token status list 2-bit round-trip preserves custom values`() {
        val statuses = listOf(
            IndexedStatus(0, 1u),
            IndexedStatus(1, 2u),
            IndexedStatus(2, 3u),
        )
        val encoded = TokenStatusListCodec.encode(statuses, statusSize = 2)
        val decoded = TokenStatusListCodec.decode(encoded)
        statuses.forEach { assertEquals(it.value, TokenStatusListCodec.readStatus(decoded, it.index, 2)) }
        assertEquals(0u, TokenStatusListCodec.readStatus(decoded, 3, 2))
    }
}
