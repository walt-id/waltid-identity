package id.walt.statuslist

import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.encoding.StatusListEncoding
import id.walt.statuslist.model.IndexedStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Test vectors from draft-ietf-oauth-status-list-20 Appendix C.
 */
class TokenStatusListAppendixCTest {
    @Test
    fun `C1 - 1-bit packed bytes encode to the spec lst`() {
        assertEquals(
            "eNrbuRgAAhcBXQ",
            TokenStatusListCodec.encode(byteArrayOf(0xB9.toByte(), 0xA3.toByte())),
        )
    }

    @Test
    fun `C2 - 2-bit packed bytes encode to the spec lst`() {
        assertEquals(
            "eNo76fITAAPfAgc",
            TokenStatusListCodec.encode(byteArrayOf(0xC9.toByte(), 0xF9.toByte()).let {
                byteArrayOf(0xC9.toByte(), 0x44.toByte(), 0xF9.toByte())
            }),
        )
    }

    @Test
    fun `Appendix C1 - full 1-bit encoding`() {
        assertSpecVector(
            bits = 1,
            statuses = mapOf(
                0 to 0b1,
                1993 to 0b1,
                25460 to 0b1,
                159495 to 0b1,
                495669 to 0b1,
                554353 to 0b1,
                645645 to 0b1,
                723232 to 0b1,
                854545 to 0b1,
                934534 to 0b1,
                1000345 to 0b1,
            ),
            expectedCborHex = """
                a2646269747301636c737458bd78daeddc010dc0200c0041a88249400ad2903e0f4b
                ba00bd93f002beb7a2a2010000a91e09000000000000000000000000000000807296
                04000000000000000000000000000000000000000000000000000000000000000000
                000000000000005c6f4800000000000000fc2c240000000000000000000000be1b12
                000000000000000000ecaa4b000000000000000000000000000000009b0b09000000
                00000000000038de9400000000000000002a30cc010000000080642f0bd8011b
            """,
        )
    }

    @Test
    fun `Appendix C2 - full 2-bit encoding`() {
        assertSpecVector(
            bits = 2,
            statuses = mapOf(
                0 to 0b01,
                1993 to 0b10,
                25460 to 0b01,
                159495 to 0b11,
                495669 to 0b01,
                554353 to 0b01,
                645645 to 0b10,
                723232 to 0b01,
                854545 to 0b01,
                934534 to 0b10,
                1000345 to 0b11,
            ),
            expectedCborHex = """
                a2646269747302636c737459013d78daeddb310d00211000412ea1a04004fe5520ed
                357c28c81d3312b6df68bc65480000000000406e2101000000000000000000000000
                0000000000000000000000000000000000000040795b020000000000000000000000
                00000000000000000000000000000000000000000000000000000000000000000000
                00000000000000000000000000000000000000000000000000000000000000000000
                0080f4ba0400000000000000000000000000406d764a000000000000000000000000
                000000000000000000e0922101000000000000000000000000000000000000fc1312
                00000000000000000000000000000000000000000000000000000000000000c0912e
                01000000000000000000000000000000000000c07d4b020000000000000000000000
                00000000a8614a0000000000000000000000406a1fcd60010c
            """,
        )
    }

    private fun assertSpecVector(bits: Int, statuses: Map<Int, Int>, expectedCborHex: String) {
        val indexStatuses = statuses
            .filterValues { it != 0 }
            .map { (index, status) -> IndexedStatus(index, status.toUInt()) }
        val compressed = TokenStatusListCodec.encodeBytes(indexStatuses, bits)
        val expectedLstBytes = lstBytesFromCbor(expectedCborHex)
        assertContentEquals(expectedLstBytes, compressed)
        assertEquals(StatusListEncoding.encodeBase64Url(expectedLstBytes), TokenStatusListCodec.encode(indexStatuses, bits))
        val decoded = TokenStatusListCodec.decode(TokenStatusListCodec.encode(indexStatuses, bits))
        indexStatuses.forEach { status ->
            assertEquals(status.value, TokenStatusListCodec.readStatus(decoded, status.index, bits))
        }
    }

    private fun lstBytesFromCbor(cborHex: String): ByteArray {
        val hex = cborHex.replace(Regex("\\s+"), "")
        val marker = "636c7374"
        val markerIndex = hex.indexOf(marker)
        require(markerIndex >= 0) { "CBOR test vector is missing lst" }
        val headerIndex = markerIndex + marker.length
        val first = hex.substring(headerIndex, headerIndex + 2).toInt(16)
        val (length, dataOffset) = when {
            first in 0x40..0x57 -> (first - 0x40) to (headerIndex + 2)
            first == 0x58 -> hex.substring(headerIndex + 2, headerIndex + 4).toInt(16) to (headerIndex + 4)
            first == 0x59 -> hex.substring(headerIndex + 2, headerIndex + 6).toInt(16) to (headerIndex + 6)
            else -> error("Unsupported CBOR byte-string header: ${first.toString(16)}")
        }
        return hex.substring(dataOffset, dataOffset + length * 2).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
