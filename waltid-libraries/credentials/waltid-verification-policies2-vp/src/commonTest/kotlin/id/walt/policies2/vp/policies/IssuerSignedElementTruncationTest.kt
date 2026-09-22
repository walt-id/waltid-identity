@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, kotlinx.serialization.cbor.DelicateCborApi::class)

package id.walt.policies2.vp.policies

import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Element values belong in a policy result, a portrait does not.
 *
 * Keeping the values is deliberate: integers, dates, short text and arrays of them are what make an mdoc
 * serialisation problem diagnosable from a stored session, and they cost nothing. A 250 KB portrait cost a
 * megabyte per policy - the value once, the serialised item again as hex - and with the policies an Enterprise
 * profile runs it pushed the session past MongoDB's 16 MB document limit, so every portrait presentation failed
 * with BsonMaximumSizeExceededException.
 *
 * The same claim arrives as either a byte string or a text string depending on whether the issuer applied
 * `base64UrlStringToByteString`, so both are bounded. The first fix bounded only byte strings and the measurement
 * showed no improvement, because the fixture issued the portrait as text.
 */
class IssuerSignedElementTruncationTest {

    @Test
    fun `a small value is kept exactly as it was decoded`() {
        val short = CborString("Doe")
        val number = CborInteger(42)
        val shortBytes = CborByteString(ByteArray(MAX_INLINE_BYTES))

        assertSame(short, short.withoutBulkBytes(), "short text must survive whole")
        assertSame(number, number.withoutBulkBytes(), "integers must survive whole")
        assertSame(shortBytes, shortBytes.withoutBulkBytes(), "a byte string at the limit is not truncated")
    }

    @Test
    fun `a portrait sized byte string keeps only its length and leading bytes`() {
        val portrait = CborByteString(ByteArray(250_000) { (it % 251).toByte() })

        val stored = portrait.withoutBulkBytes()

        val summary = assertIs<Map<*, *>>(stored)
        assertEquals("bytes", summary["type"])
        assertEquals(250_000, summary["length"])
        assertEquals(true, summary["truncated"])
        // Hex, so two characters per byte: enough to recognise a format from its magic bytes.
        assertEquals(MAX_INLINE_BYTES * 2, (summary["prefix_hex"] as String).length)
    }

    @Test
    fun `a portrait sized text string keeps only its length and prefix`() {
        val base64Portrait = CborString("A".repeat(333_334))

        val stored = base64Portrait.withoutBulkBytes()

        val summary = assertIs<Map<*, *>>(stored)
        assertEquals("text", summary["type"])
        assertEquals(333_334, summary["length"])
        assertEquals(true, summary["truncated"])
        assertEquals(MAX_INLINE_TEXT, (summary["prefix"] as String).length)
    }

    /** The serialised item carries the same bytes, so bounding the value alone would not bound the result. */
    @Test
    fun `a serialised item is hex until it grows, then a marked prefix`() {
        val small = ByteArray(MAX_INLINE_SERIALIZED_BYTES) { 1 }
        assertIs<String>(small.truncatedHex(MAX_INLINE_SERIALIZED_BYTES))

        val large = ByteArray(500_000) { 2 }
        val summary = assertIs<Map<*, *>>(large.truncatedHex(MAX_INLINE_SERIALIZED_BYTES))
        assertEquals(500_000, summary["length"])
        assertEquals(true, summary["truncated"])
        assertTrue((summary["prefix_hex"] as String).length == MAX_INLINE_SERIALIZED_BYTES * 2)
    }
}
