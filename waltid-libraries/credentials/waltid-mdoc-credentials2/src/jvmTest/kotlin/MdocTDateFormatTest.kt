@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.toMdocTDateString
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class MdocTDateFormatTest {

    @Test
    fun `ValidityInfo encodes MSO timestamps without fractional seconds`() {
        val instant = Instant.fromEpochSeconds(1715786160, 123_000_000)
        val validityInfo = ValidityInfo(
            signed = instant,
            validFrom = instant,
            validUntil = instant,
        )
        val hex = coseCompliantCbor.encodeToHexString(validityInfo)

        assertContains(hex, "323032342d30352d31355431353a31363a30305a") // "2024-05-15T15:16:00Z"
        assertFalse(
            hex.contains("2e313233"), // ".123" in hex — fractional seconds must not appear
            "CBOR must not contain fractional seconds in date-time strings",
        )
    }

    @Test
    fun `toMdocTDateString strips fractional seconds from ISO strings`() {
        assertEquals(
            "2026-05-15T15:16:00Z",
            "2026-05-15T15:16:00.000Z".toMdocTDateString(),
        )
    }
}
