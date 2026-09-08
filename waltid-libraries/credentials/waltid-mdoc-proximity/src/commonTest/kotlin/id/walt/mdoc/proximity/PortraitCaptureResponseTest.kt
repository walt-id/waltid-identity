@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.coseCompliantCbor
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class PortraitCaptureResponseTest {
    @Test
    fun configuredCaptureDateIsReturnedAsAnExactTagZeroUtcTimestamp() = runTest {
        val fixture = MdlProfileResponseTest()
        for (date in listOf(LocalDate(1583, 1, 1), LocalDate(2024, 2, 29), LocalDate(9999, 12, 31))) {
            val elements = fixture.responseElements(fixture.profile().copy(portraitCaptureDate = date))
            val capture = assertIs<CborString>(elements["portrait_capture_date"])
            val expected = "${date}T00:00:00Z"
            assertEquals(expected, capture.value)
            assertEquals(20, capture.value.encodeToByteArray().size)
            assertEquals(expected, Instant.parse(capture.value).toString())
            // The byte prefix independently checks tag 0 followed by a 20-byte text string.
            assertContentEquals(byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), capture))
        }
        val omitted = fixture.responseElements(fixture.profile())
        assertTrue("portrait_capture_date" !in omitted)
    }
}
