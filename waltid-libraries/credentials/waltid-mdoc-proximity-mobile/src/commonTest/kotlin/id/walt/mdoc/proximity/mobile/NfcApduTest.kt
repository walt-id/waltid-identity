package id.walt.mdoc.proximity.mobile

import kotlinx.io.bytestring.ByteString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NfcApduTest {
    @Test
    fun `short and extended command APDUs match independent wire vectors`() {
        val payload = ByteArray(256) { it.toByte() }
        // Wire encodings cross-checked with javax.smartcardio.CommandAPDU.
        val commands = listOf(
            NfcCommandApdu(0u, 0xa4u, 0u, 0u) to "00a40000",
            NfcCommandApdu(0u, 0xc0u, 0u, 0u, expectedResponseDataLength = 256) to "00c0000000",
            NfcCommandApdu(0u, 0xd6u, 0u, 0u, ByteString(byteArrayOf(1, 2, 3))) to "00d6000003010203",
            NfcCommandApdu(
                0u, 0xd6u, 0u, 0u, ByteString(byteArrayOf(1, 2, 3)),
                expectedResponseDataLength = 17,
            ) to "00d600000301020311",
            NfcCommandApdu(0u, 0xc0u, 0u, 0u, expectedResponseDataLength = 65_536) to "00c00000000000",
            NfcCommandApdu(0u, 0xd6u, 0u, 0u, ByteString(payload)) to "00d60000000100${payload.toHexString()}",
            NfcCommandApdu(
                0u, 0xd6u, 0u, 0u, ByteString(payload),
                expectedResponseDataLength = 65_536,
            ) to "00d60000000100${payload.toHexString()}0000",
        )

        commands.forEach { (command, hex) ->
            val encoded = hex.hexToByteArray()
            assertContentEquals(encoded, command.encode())
            assertEquals(command, NfcCommandApdu.decode(encoded))
        }
    }

    @Test
    fun `chaining flag is exposed without rewriting the class byte`() {
        assertFalse(NfcCommandApdu(0x00u, 0xc3u, 0u, 0u).isChained)
        assertTrue(NfcCommandApdu(0x10u, 0xc3u, 0u, 0u).isChained)
    }

    @Test
    fun `malformed and ambiguous command lengths fail closed`() {
        listOf(
            byteArrayOf(),
            byteArrayOf(0, 1, 2),
            byteArrayOf(0, 1, 2, 3, 2, 9),
            byteArrayOf(0, 1, 2, 3, 0, 0),
            byteArrayOf(0, 1, 2, 3, 0, 0, 0, 9),
            byteArrayOf(0, 1, 2, 3, 0, 0, 2, 9),
        ).forEach { encoded ->
            assertFailsWith<IllegalArgumentException> { NfcCommandApdu.decode(encoded) }
        }
    }

    @Test
    fun `response APDU retains data and status`() {
        val response = NfcResponseApdu(ByteString(byteArrayOf(1, 2, 3)), NfcStatusWord.SUCCESS)
        assertContentEquals(byteArrayOf(1, 2, 3, 0x90.toByte(), 0), response.encode())
        assertEquals(response, NfcResponseApdu.decode(response.encode()))
        assertFailsWith<IllegalArgumentException> { NfcResponseApdu.decode(byteArrayOf(0x90.toByte())) }
    }

    @Test
    fun `more-data status saturates at the ISO sentinel`() {
        assertEquals(0x6101u, NfcStatusWord.moreData(1))
        assertEquals(0x61ffu, NfcStatusWord.moreData(255))
        assertEquals(0x6100u, NfcStatusWord.moreData(256))
        assertEquals(0x6100u, NfcStatusWord.moreData(65_536))
    }

    @Test
    fun `arbitrary bounded command input never escapes parser failures`() {
        val random = Random(1347)
        repeat(1_000) {
            val bytes = random.nextBytes(random.nextInt(0, 512))
            runCatching { NfcCommandApdu.decode(bytes) }
                .exceptionOrNull()?.let { assertEquals(IllegalArgumentException::class, it::class) }
        }
    }
}
