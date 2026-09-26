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

    private fun assertTDate(map: CborMap, key: String, expected: String) {
        val value = map[CborString(key)] as CborString
        assertEquals(listOf(0uL), value.tags, key)
        assertEquals(expected, value.value, key)
        assertFalse('.' in value.value, key)
        assertTrue(value.value.endsWith('Z'), key)
    }
}
