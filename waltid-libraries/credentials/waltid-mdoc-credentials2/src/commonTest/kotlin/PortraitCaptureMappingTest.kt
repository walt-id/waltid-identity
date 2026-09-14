@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.mapPortraitCaptureDate
import id.walt.mdoc.encoding.PortraitCaptureDateMapping
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.json.*
import kotlin.test.*

class PortraitCaptureMappingTest {
    @Test
    fun standardNamespacesAcceptDateOnlyAndFullTimestampInput() {
        for (namespace in listOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")) {
            for ((input, expected) in listOf(
                "2024-02-29" to "2024-02-29T00:00:00Z",
                "2024-02-29T13:34:56.987+01:00" to "2024-02-29T12:34:56Z",
            )) {
                val mapped = assertIs<PortraitCaptureDateMapping.Mapped>(mapPortraitCaptureDate(namespace, "portrait_capture_date", JsonPrimitive(input)))
                assertContentEquals(byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), mapped.value))
            }
        }
    }

    @Test
    fun unrelatedFieldsAndCustomNamespacesAreNotMapped() {
        for (value in listOf(JsonPrimitive("not-a-date"), JsonNull)) {
            assertEquals(PortraitCaptureDateMapping.NotApplicable, mapPortraitCaptureDate("custom.example", "portrait_capture_date", value))
            assertEquals(PortraitCaptureDateMapping.NotApplicable, mapPortraitCaptureDate("org.iso.18013.5.1", "birth_date", value))
        }
    }

    @Test
    fun explicitNullIsOmittedInStandardNamespacesAndLegacyAlias() {
        for (namespace in listOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")) {
            assertEquals(PortraitCaptureDateMapping.Omit, mapPortraitCaptureDate(namespace, "portrait_capture_date", JsonNull))
        }
    }

    @Test
    fun malformedAndOutOfRangeValuesAreRejected() {
        for (input in listOf("2023-02-29", "2024-02-29T12:60:00Z", "+10000-01-01T00:00:00Z", "garbage")) {
            assertFails { mapPortraitCaptureDate("org.iso.18013.5.1", "portrait_capture_date", JsonPrimitive(input)) }
        }
        for (value in listOf(JsonPrimitive(17), JsonPrimitive(false), JsonArray(emptyList()), JsonObject(emptyMap()))) {
            assertFails { mapPortraitCaptureDate("org.iso.18013.5.1", "portrait_capture_date", value) }
        }
    }
}
