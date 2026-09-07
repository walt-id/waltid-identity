@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.credsdata.PhotoId
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.*
import kotlin.time.Instant

class PortraitCaptureTimestampTest {
    private val capture = Instant.parse("2024-02-29T12:34:56Z")
    private val expectedValue = byteArrayOf(0xc0.toByte(), 0x74) + "2024-02-29T12:34:56Z".encodeToByteArray()

    @Test
    fun modelsProduceTagZeroTimestampIssuerSignedItems() {
        for (model in listOf(mdl(capture), photoId(capture))) {
            val item = model.toNamespaceIssuerSignedItems().values.flatten()
                .single { it.elementIdentifier == "portrait_capture_date" }
            assertContentEquals(expectedValue, coseCompliantCbor.encodeToByteArray(CborElement.serializer(), item.elementValue))
        }
    }

    @Test
    fun directModelCborAndJsonRoundTripPreserveCaptureInstant() {
        val mdl = mdl(capture)
        val photoId = photoId(capture)
        assertEquals(capture, coseCompliantCbor.decodeFromByteArray<Mdl>(coseCompliantCbor.encodeToByteArray(mdl)).portraitCaptureDate)
        assertEquals(capture, coseCompliantCbor.decodeFromByteArray<PhotoId>(coseCompliantCbor.encodeToByteArray(photoId)).portraitCaptureDate)
        for (encoded in listOf(coseCompliantCbor.encodeToByteArray(mdl), coseCompliantCbor.encodeToByteArray(photoId))) {
            assertTrue(encoded.toHexString().contains(expectedValue.toHexString()))
        }
        assertEquals(capture, Json.decodeFromString<Mdl>(Json.encodeToString(mdl)).portraitCaptureDate)
        assertEquals(capture, Json.decodeFromString<PhotoId>(Json.encodeToString(photoId)).portraitCaptureDate)
    }

    @Test
    fun unknownCaptureTimeIsOmitted() {
        for (model in listOf(mdl(null), photoId(null))) {
            assertTrue(model.toNamespaceIssuerSignedItems().values.flatten().none { it.elementIdentifier == "portrait_capture_date" })
        }
        assertFalse(Json.encodeToString(mdl(null)).contains("portrait_capture_date"))
        assertFalse(Json.encodeToString(photoId(null)).contains("portrait_capture_date"))
    }

    @Test
    fun captureInstantNormalizesOffsetAndFractionToUtcWholeSeconds() {
        val captureWithOffset = Instant.parse("2024-02-29T13:34:56.987+01:00")
        for (model in listOf(mdl(captureWithOffset), photoId(captureWithOffset))) {
            val value = model.toNamespaceIssuerSignedItems().values.flatten()
                .single { it.elementIdentifier == "portrait_capture_date" }.elementValue
            assertContentEquals(expectedValue, coseCompliantCbor.encodeToByteArray(CborElement.serializer(), value))
        }
    }

    @Test
    fun issuanceRejectsCaptureInstantsOutsideFourDigitYearRange() {
        for (capture in listOf(Instant.parse("+10000-01-01T00:00:00Z"), Instant.parse("-0001-01-01T00:00:00Z"))) {
            assertFailsWith<IllegalArgumentException> { mdl(capture).toNamespaceIssuerSignedItems() }
            assertFailsWith<IllegalArgumentException> { photoId(capture).toNamespaceIssuerSignedItems() }
        }
    }

    @Test
    fun modelInputRejectsDateOnlyAndInvalidCalendarTime() {
        val mdlJson = Json.encodeToJsonElement(Mdl.serializer(), mdl(capture)).jsonObject
        val photoJson = Json.encodeToJsonElement(PhotoId.serializer(), photoId(capture)).jsonObject
        for (invalid in listOf("2024-02-29", "2023-02-29T12:34:56Z", "2024-02-29T12:60:56Z", "not-a-timestamp")) {
            assertFails("mDL: $invalid") {
                Json.decodeFromJsonElement(Mdl.serializer(), JsonObject(mdlJson + ("portrait_capture_date" to JsonPrimitive(invalid))))
            }
            assertFails("Photo ID: $invalid") {
                Json.decodeFromJsonElement(PhotoId.serializer(), JsonObject(photoJson + ("portrait_capture_date" to JsonPrimitive(invalid))))
            }
        }
    }

    @Test
    fun bothNamespaceRegistrationsDecodeCaptureInstants() {
        Mdl.registerSerializationTypes()
        PhotoId.registerSerializationTypes()
        for (namespace in listOf("org.iso.18013.5.1", "org.iso.23220.1", "org.iso.23220.photoid.1")) {
            val serializer = assertNotNull(MdocsCborSerializer.lookupSerializer(namespace, "portrait_capture_date"), namespace)
            assertEquals(capture, Json.decodeFromString(serializer, "\"2024-02-29T12:34:56Z\""))
        }
    }

    private fun mdl(capture: Instant?) = Mdl(
        familyName = "Example", givenName = "Holder", issueDate = LocalDate(2024, 3, 1),
        expiryDate = LocalDate(2030, 3, 1), documentNumber = "TEST-1", drivingPrivileges = emptyList(),
        portraitCaptureDate = capture,
    )

    private fun photoId(capture: Instant?) = PhotoId(
        familyNameUnicode = "Example", givenNameUnicode = "Holder", familyNameLatin1 = "Example", givenNameLatin1 = "Holder",
        birthDate = LocalDate(2000, 1, 1),
        portrait = byteArrayOf(1), issueDate = LocalDate(2024, 3, 1), expiryDate = LocalDate(2030, 3, 1),
        issuingAuthorityUnicode = "Example Authority", issuingCountry = "AT", portraitCaptureDate = capture,
    )
}
