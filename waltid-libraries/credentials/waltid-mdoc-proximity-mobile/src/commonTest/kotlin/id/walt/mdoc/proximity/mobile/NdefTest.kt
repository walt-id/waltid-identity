package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdefTest {
    @Test
    fun `short long and identified records round trip exactly`() {
        val message = NdefMessage(
            listOf(
                NdefRecord(
                    NdefTypeNameFormat.WELL_KNOWN,
                    ImmutableBytes.of("Hs".encodeToByteArray()),
                    payload = ImmutableBytes.of(byteArrayOf(1, 2, 3)),
                ),
                NdefRecord(
                    NdefTypeNameFormat.EXTERNAL,
                    ImmutableBytes.of("example:type".encodeToByteArray()),
                    ImmutableBytes.of("id".encodeToByteArray()),
                    ImmutableBytes.of(ByteArray(256) { it.toByte() }),
                ),
            )
        )
        val encoded = message.encode()
        assertEquals(message, NdefMessage.decode(encoded))
        assertContentEquals(encoded, NdefMessage.decode(encoded).encode())
    }

    @Test
    fun `chunked physical records decode into one logical record`() {
        val encoded = byteArrayOf(
            0xb1.toByte(), 0x01, 0x02, 'T'.code.toByte(), 0x01, 0x02,
            0x56, 0x00, 0x01, 0x03,
        )
        val record = NdefMessage.decode(encoded).records.single()
        assertEquals(NdefTypeNameFormat.WELL_KNOWN, record.typeNameFormat)
        assertContentEquals(byteArrayOf('T'.code.toByte()), record.type.copy())
        assertContentEquals(byteArrayOf(1, 2, 3), record.payload.copy())
    }

    @Test
    fun `invalid flags chunks lengths and trailing bytes fail closed`() {
        listOf(
            byteArrayOf(),
            byteArrayOf(0x51, 0, 0),
            byteArrayOf(0x91.toByte(), 0, 0),
            byteArrayOf(0xd1.toByte(), 0, 0, 0),
            byteArrayOf(0xf1.toByte(), 0, 0),
            byteArrayOf(0xd6.toByte(), 0, 0),
            byteArrayOf(0xb1.toByte(), 1, 1, 'T'.code.toByte(), 1, 0x51, 0, 0),
            byteArrayOf(0xd0.toByte(), 0, 1, 1),
            byteArrayOf(0xd8.toByte(), 0, 0, 0),
            byteArrayOf(0xc0.toByte(), 0, 0, 0, 0, 0),
            byteArrayOf(0xf0.toByte(), 0, 0),
            byteArrayOf(0xd5.toByte(), 1, 0, 'T'.code.toByte()),
            byteArrayOf(0xc1.toByte(), 0, 0, 0),
        ).forEach { encoded ->
            assertFailsWith<IllegalArgumentException>(encoded.toHexString()) { NdefMessage.decode(encoded) }
        }

        listOf(
            NdefTypeNameFormat.WELL_KNOWN,
            NdefTypeNameFormat.MIME_MEDIA,
            NdefTypeNameFormat.ABSOLUTE_URI,
            NdefTypeNameFormat.EXTERNAL,
        ).forEach { format ->
            assertFailsWith<IllegalArgumentException> { NdefRecord(format) }
        }
    }

    @Test
    fun `configured bounds are enforced before allocation`() {
        val longHeader = byteArrayOf(0xc1.toByte(), 0, 0, 0, 1, 0)
        assertFailsWith<IllegalArgumentException> {
            NdefMessage.decode(longHeader, NdefLimits(maximumMessageBytes = 32, maximumRecordPayloadBytes = 255))
        }
        val twoRecords = NdefMessage(
            listOf(
                NdefRecord(NdefTypeNameFormat.EMPTY),
                NdefRecord(NdefTypeNameFormat.EMPTY),
            )
        ).encode()
        assertFailsWith<IllegalArgumentException> {
            NdefMessage.decode(twoRecords, NdefLimits(maximumRecordCount = 1))
        }
    }

    @Test
    fun `arbitrary bounded input never escapes parser failures`() {
        val random = Random(1347)
        repeat(1_000) {
            val bytes = random.nextBytes(random.nextInt(0, 128))
            runCatching { NdefMessage.decode(bytes, NdefLimits(maximumMessageBytes = 128)) }
                .exceptionOrNull()?.let { assertEquals(IllegalArgumentException::class, it::class) }
        }
    }
}
