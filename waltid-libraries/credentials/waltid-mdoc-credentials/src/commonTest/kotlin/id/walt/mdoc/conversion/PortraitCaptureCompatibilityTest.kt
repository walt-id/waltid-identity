package id.walt.mdoc.conversion

import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.TDateElement
import id.walt.mdoc.dataelement.json.*
import id.walt.mdoc.doc.MDocNameSpaceBuilder
import kotlinx.serialization.json.*
import kotlin.test.*

class PortraitCaptureCompatibilityTest {
    @Test
    fun savedDateOnlyProfilesAndTimestampInputsProduceTagZero() {
        for (namespace in listOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")) {
            for (conversion in listOf("stringToFullDate", "stringToTDate")) {
                val config = Json.decodeFromString<JsonObjectToCborMappingConfig>(
                    """{"entriesConfigMap":{"portrait_capture_date":{"type":"string","conversionType":"$conversion"},"birth_date":{"type":"string","conversionType":"stringToFullDate"}}}"""
                )
                for ((input, expected) in listOf(
                    "2024-02-29" to "2024-02-29T00:00:00Z",
                    "2024-02-29T13:34:56.987+01:00" to "2024-02-29T12:34:56Z",
                )) {
                    val data = buildJsonObject { put("portrait_capture_date", input); put("birth_date", "2000-01-01") }
                    val mapped = MDocNameSpaceBuilder.fromJsonObjectMappingConfig(namespace, data, config)
                    val portrait = assertIs<TDateElement>(mapped.claimsMap.value[MapKey("portrait_capture_date")])
                    assertContentEquals(byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(), portrait.toCBOR())
                    assertIs<FullDateElement>(mapped.claimsMap.value[MapKey("birth_date")])
                    assertEquals(input, data["portrait_capture_date"]!!.jsonPrimitive.content)
                    assertEquals(conversion, Json.encodeToJsonElement(config).jsonObject["entriesConfigMap"]!!.jsonObject["portrait_capture_date"]!!.jsonObject["conversionType"]!!.jsonPrimitive.content)
                }
            }
        }
    }

    @Test
    fun defaultMappingAlsoNormalizesPortraitButCustomNamespacesStayUnchanged() {
        val data = buildJsonObject { put("portrait_capture_date", "2024-02-29") }
        val mapped = MDocNameSpaceBuilder.fromJsonObject("org.iso.18013.5.1", data)
        assertIs<TDateElement>(mapped.claimsMap.value[MapKey("portrait_capture_date")])
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
