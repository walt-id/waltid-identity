@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.MdocTDateInstantSerializer
import id.walt.mdoc.encoding.MdocTDateMode
import id.walt.mdoc.encoding.parseMdocTDate
import id.walt.mdoc.encoding.toMdocTDateString
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MdocTDateFormatTest {

    @Test
    fun validateMSOTimestamps() {
        val instant = Instant.fromEpochSeconds(1715786160, 123_000_000)
        val validityInfo = ValidityInfo(
            signed = instant,
            validFrom = instant,
            validUntil = instant.plus(kotlin.time.Duration.parse("365d")),
        )
        val hex = coseCompliantCbor.encodeToHexString(validityInfo)

        assertContains(hex, "323032342d30352d31355431353a31363a30305a") // "2024-05-15T15:16:00Z"
        assertFalse(
            hex.contains("2e313233"), // ".123" in hex — fractional seconds must not appear
            "CBOR must not contain fractional seconds in date-time strings",
        )
    }

    @Test
    fun validateToMdocTDateStringStripsFractionalSeconds() {
        assertEquals(
            "2026-05-15T15:16:00Z",
            "2026-05-15T15:16:00.000Z".toMdocTDateString(),
        )
    }

    @Test
    fun precheckRejectsEqualNormalizedTimestamps() {
        val instant = Instant.fromEpochSeconds(1715786160)
        val validityInfo = ValidityInfo(
            signed = instant,
            validFrom = instant,
            validUntil = instant,
        )
        assertFailsWith<IllegalArgumentException> {
            validityInfo.precheck()
        }
    }

    @Test
    fun precheckAcceptsSignedEqualToValidFromAndRejectsSignedAfterStart() {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val validUntil = Instant.parse("2027-09-03T12:00:00Z")
        ValidityInfo(signed, signed, validUntil).precheck()
        assertFailsWith<IllegalArgumentException> {
            ValidityInfo(
                signed = signed.plus(kotlin.time.Duration.parse("1s")),
                validFrom = signed,
                validUntil = validUntil,
            ).precheck()
        }
    }

    @Test
    fun precheckDoesNotBoundExpectedUpdate() {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val validFrom = Instant.parse("2026-09-13T12:00:00Z")
        val validUntil = Instant.parse("2026-10-03T12:00:00Z")
        val earlierUpdate = Instant.parse("2026-09-04T12:00:00Z")
        val info = ValidityInfo(signed, validFrom, validUntil, earlierUpdate)
        info.precheck()
        assertFailsWith<IllegalArgumentException> {
            info.requireExpectedUpdateWithinWindow()
        }
        ValidityInfo(signed, validFrom, validUntil, null).requireExpectedUpdateWithinWindow()
    }

    @Test
    fun validateRejectsPresentationBeforeValidFrom() {
        val now = kotlin.time.Clock.System.now()
        val info = ValidityInfo(
            signed = now,
            validFrom = now.plus(kotlin.time.Duration.parse("10d")),
            validUntil = now.plus(kotlin.time.Duration.parse("30d")),
        )
        info.precheck()
        assertFailsWith<IllegalArgumentException> {
            info.validate()
        }
    }

    @Test
    fun emittedValidityFieldsUseTag0WholeSecondsAndZ() {
        val signed = Instant.parse("2026-09-03T12:00:00Z")
        val validFrom = Instant.parse("2026-09-03T12:00:00Z")
        val validUntil = Instant.parse("2027-09-03T12:00:00Z")
        val expectedUpdate = Instant.parse("2026-12-03T12:00:00Z")
        val withUpdate = ValidityInfo(signed, validFrom, validUntil, expectedUpdate)
        val map = coseCompliantCbor.decodeFromByteArray<CborMap>(coseCompliantCbor.encodeToByteArray(withUpdate))
        assertTDate(map, "signed", "2026-09-03T12:00:00Z")
        assertTDate(map, "validFrom", "2026-09-03T12:00:00Z")
        assertTDate(map, "validUntil", "2027-09-03T12:00:00Z")
        assertTDate(map, "expectedUpdate", "2026-12-03T12:00:00Z")

        val omitted = ValidityInfo(signed, validFrom, validUntil)
        val omittedMap = coseCompliantCbor.decodeFromByteArray<CborMap>(coseCompliantCbor.encodeToByteArray(omitted))
        assertNull(omittedMap[CborString("expectedUpdate")])
    }

    @Test
    fun strictDecodeRejectsMissingTagFractionalSecondsAndOffsets() {
        val valid = CborString("2026-09-03T12:00:00Z", 0uL)
        assertEquals(
            Instant.parse("2026-09-03T12:00:00Z"),
            coseCompliantCbor.decodeFromByteArray(MdocTDateInstantSerializer, coseCompliantCbor.encodeToByteArray(valid)),
        )
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00Z")),
            )
        }
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00Z", 1uL)),
            )
        }
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00Z", 1uL, 0uL)),
            )
        }
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00Z", 0uL, 1uL)),
            )
        }
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00.000Z", 0uL)),
            )
        }
        assertFailsWith<Exception> {
            coseCompliantCbor.decodeFromByteArray(
                MdocTDateInstantSerializer,
                coseCompliantCbor.encodeToByteArray(CborString("2026-09-03T12:00:00+00:00", 0uL)),
            )
        }
        assertEquals(
            Instant.parse("2026-09-03T12:00:00Z"),
            parseMdocTDate("2026-09-03T12:00:00.000Z", MdocTDateMode.LENIENT),
        )
    }

    @Test
    fun strictDecodeRejectsNestedAndRepeatedCborTags() {
        assertEquals(Instant.parse(TDATE_TEXT), decodeTDate(cborTag(0, cborTstr(TDATE_TEXT))))
        assertFailsWith<Exception> { decodeTDate(cborTag(1, cborTag(0, cborTstr(TDATE_TEXT)))) }
        assertFailsWith<Exception> { decodeTDate(cborTag(0, cborTag(1, cborTstr(TDATE_TEXT)))) }
        assertRepeatedTag0Tdate(cborTag(0, cborTag(0, cborTstr(TDATE_TEXT))))
    }

    @Test
    fun validityInfoRejectsNestedCborTagsOnEachField() {
        for (field in validityFields) {
            assertFailsWith<Exception>(field) {
                decodeValidityInfo(validityInfoBytes(field) { tstr -> cborTag(1, cborTag(0, tstr)) })
            }
            assertFailsWith<Exception>(field) {
                decodeValidityInfo(validityInfoBytes(field) { tstr -> cborTag(0, cborTag(1, tstr)) })
            }
            assertRepeatedTag0ValidityInfo(validityInfoBytes(field) { tstr -> cborTag(0, cborTag(0, tstr)) })
        }
        decodeValidityInfo(validityInfoBytes())
    }

    private fun decodeTDate(bytes: ByteArray): Instant =
        coseCompliantCbor.decodeFromByteArray(MdocTDateInstantSerializer, bytes)

    private fun decodeValidityInfo(bytes: ByteArray): ValidityInfo =
        coseCompliantCbor.decodeFromByteArray(bytes)

    /**
     * kotlinx.serialization may flatten `0(0(tstr))` to a single tag 0, so repeated tag 0 is not
     * always distinguishable from a well-formed tdate. Reject it when the decoder preserves both tags.
     */
    private fun assertRepeatedTag0Tdate(bytes: ByteArray) {
        val tags = try {
            coseCompliantCbor.decodeFromByteArray<CborString>(bytes).tags.toList()
        } catch (_: Exception) {
            return
        }
        if (tags == listOf(0uL)) {
            assertEquals(Instant.parse(TDATE_TEXT), decodeTDate(bytes))
        } else {
            assertFailsWith<Exception> { decodeTDate(bytes) }
        }
    }

    private fun assertRepeatedTag0ValidityInfo(bytes: ByteArray) {
        try {
            decodeValidityInfo(bytes)
        } catch (_: Exception) {
            return
        }
    }

    private fun assertTDate(map: CborMap, key: String, expected: String) {
        val value = map[CborString(key)] as CborString
        assertEquals(listOf(0uL), value.tags, key)
        assertEquals(expected, value.value, key)
        assertFalse('.' in value.value, key)
        assertTrue(value.value.endsWith('Z'), key)
    }

    companion object {
        private const val TDATE_TEXT = "2026-09-08T12:00:00Z"
        private const val VALID_UNTIL_TEXT = "2027-09-08T12:00:00Z"
        private val validityFields = listOf("signed", "validFrom", "validUntil", "expectedUpdate")

        private fun cborTstr(text: String): ByteArray {
            val utf8 = text.encodeToByteArray()
            require(utf8.size < 24)
            return byteArrayOf((0x60 + utf8.size).toByte()) + utf8
        }

        private fun cborTag(tag: Int, payload: ByteArray): ByteArray {
            require(tag in 0..23)
            return byteArrayOf((0xC0 + tag).toByte()) + payload
        }

        private fun validityInfoBytes(
            nestedField: String? = null,
            nest: (ByteArray) -> ByteArray = { cborTag(0, it) },
        ): ByteArray {
            fun value(name: String, text: String): ByteArray {
                val tstr = cborTstr(text)
                return if (name == nestedField) nest(tstr) else cborTag(0, tstr)
            }
            val entries = listOf(
                cborTstr("signed") + value("signed", TDATE_TEXT),
                cborTstr("validFrom") + value("validFrom", TDATE_TEXT),
                cborTstr("validUntil") + value("validUntil", VALID_UNTIL_TEXT),
                cborTstr("expectedUpdate") + value("expectedUpdate", TDATE_TEXT),
            )
            return byteArrayOf((0xA0 + entries.size).toByte()) + entries.reduce { acc, bytes -> acc + bytes }
        }
    }
}
