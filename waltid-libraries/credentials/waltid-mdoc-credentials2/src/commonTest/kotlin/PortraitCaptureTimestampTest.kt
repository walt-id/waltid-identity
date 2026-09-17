@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.credsdata.PhotoId
import id.walt.mdoc.encoding.PortraitCaptureTimestampSerializer
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
    private val capture = LocalDate(2024, 2, 29)
    private val expectedValue = byteArrayOf(0xc0.toByte(), 0x74) + "2024-02-29T00:00:00Z".encodeToByteArray()

    @Test
    fun modelsProduceTagZeroTimestampIssuerSignedItems() {
        for (model in listOf(mdl(capture), photoId(capture))) {
            val item = model.toNamespaceIssuerSignedItems().values.flatten()
                .single { it.elementIdentifier == "portrait_capture_date" }
            assertContentEquals(expectedValue, coseCompliantCbor.encodeToByteArray(CborElement.serializer(), item.elementValue))
            val jsonValue = model.toNamespacesJson().values.firstNotNullOf { it["portrait_capture_date"] }
            assertEquals(JsonPrimitive("2024-02-29T00:00:00Z"), jsonValue)
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
    fun legacyModelCborDateOnlyValuesRemainReadable() {
        val legacyValue = (byteArrayOf(0x6a) + "2024-02-29".encodeToByteArray()).toHexString()
        val oldMdl = coseCompliantCbor.encodeToByteArray(mdl(capture)).toHexString()
            .replace(expectedValue.toHexString(), legacyValue).hexToByteArray()
        val oldPhoto = coseCompliantCbor.encodeToByteArray(photoId(capture)).toHexString()
            .replace(expectedValue.toHexString(), legacyValue).hexToByteArray()
        assertEquals(capture, coseCompliantCbor.decodeFromByteArray<Mdl>(oldMdl).portraitCaptureDate)
        assertEquals(capture, coseCompliantCbor.decodeFromByteArray<PhotoId>(oldPhoto).portraitCaptureDate)
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
    fun legacyJsonAndCopyKeepDateOnlyContract() {
        val mdl = mdl(capture)
        val photo = photoId(capture)
        for (json in listOf(Json.encodeToString(mdl), Json.encodeToString(photo))) {
            assertTrue(json.contains("\"portrait_capture_date\":\"2024-02-29\""))
        }
        assertEquals(capture, mdl.copy(portraitCaptureDate = capture).portraitCaptureDate)
        assertEquals(capture, photo.copy(portraitCaptureDate = capture).portraitCaptureDate)
    }

    @Test
    fun legacyModelRejectsLossyNonMidnightTimestampAndInvalidDate() {
        val mdlJson = Json.encodeToJsonElement(Mdl.serializer(), mdl(capture)).jsonObject
        val photoJson = Json.encodeToJsonElement(PhotoId.serializer(), photoId(capture)).jsonObject
        for (invalid in listOf("2024-02-29T12:34:56Z", "2023-02-29", "not-a-date")) {
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
            assertEquals("\"2024-02-29T00:00:00Z\"", Json.encodeToString(PortraitCaptureTimestampSerializer, capture))
            assertEquals("\"2024-02-29T12:34:56Z\"", Json.encodeToString(PortraitCaptureTimestampSerializer, Instant.parse("2024-02-29T13:34:56.987+01:00")))
            assertFailsWith<IllegalArgumentException> { Json.encodeToString(PortraitCaptureTimestampSerializer, "2024-02-29") }
            assertEquals(capture, Json.decodeFromString(serializer, "\"2024-02-29\""))
            assertEquals(Instant.parse("2024-02-29T12:34:56Z"), Json.decodeFromString(serializer, "\"2024-02-29T12:34:56Z\""))
        }
    }

    private fun mdl(capture: LocalDate?) = Mdl(
        familyName = "Example", givenName = "Holder", issueDate = LocalDate(2024, 3, 1),
        expiryDate = LocalDate(2030, 3, 1), documentNumber = "TEST-1", drivingPrivileges = emptyList(),
        portraitCaptureDate = capture,
    )

    private fun photoId(capture: LocalDate?) = PhotoId(
        familyNameUnicode = "Example", givenNameUnicode = "Holder", familyNameLatin1 = "Example", givenNameLatin1 = "Holder",
        birthDate = LocalDate(2000, 1, 1),
        portrait = byteArrayOf(1), issueDate = LocalDate(2024, 3, 1), expiryDate = LocalDate(2030, 3, 1),
        issuingAuthorityUnicode = "Example Authority", issuingCountry = "AT", portraitCaptureDate = capture,
    )
}
