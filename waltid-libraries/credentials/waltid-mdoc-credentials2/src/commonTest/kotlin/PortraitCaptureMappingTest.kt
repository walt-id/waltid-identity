@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.mapPortraitCaptureDate
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class PortraitCaptureMappingTest {
    @Test
    fun standardNamespacesAcceptDateOnlyAndFullTimestampInput() {
        for (namespace in listOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")) {
            for ((input, expected) in listOf(
                "2024-02-29" to "2024-02-29T00:00:00Z",
                "2024-02-29T13:34:56.987+01:00" to "2024-02-29T12:34:56Z",
            )) {
                val mapped = assertNotNull(mapPortraitCaptureDate(namespace, "portrait_capture_date", JsonPrimitive(input)))
                assertContentEquals(byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), mapped))
            }
        }
    }

    @Test
    fun unrelatedFieldsAndCustomNamespacesAreNotMapped() {
        assertNull(mapPortraitCaptureDate("custom.example", "portrait_capture_date", JsonPrimitive("2024-02-29")))
        assertNull(mapPortraitCaptureDate("org.iso.18013.5.1", "birth_date", JsonPrimitive("2024-02-29")))
    }

    @Test
    fun malformedAndOutOfRangeValuesAreRejected() {
        for (input in listOf("2023-02-29", "2024-02-29T12:60:00Z", "+10000-01-01T00:00:00Z", "garbage")) {
            assertFails { mapPortraitCaptureDate("org.iso.18013.5.1", "portrait_capture_date", JsonPrimitive(input)) }
        }
        assertFails { mapPortraitCaptureDate("org.iso.18013.5.1", "portrait_capture_date", JsonPrimitive(17)) }
    }
}
