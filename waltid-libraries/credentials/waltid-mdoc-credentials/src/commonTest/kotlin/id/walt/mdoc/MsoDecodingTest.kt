package id.walt.mdoc

import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.MSO
import id.walt.mdoc.mso.ValidityInfo
import kotlin.test.*
import kotlin.time.Instant

class MsoDecodingTest {
    private val time = Instant.parse("2026-01-01T00:00:00Z")
    private val fields = MSO(
        StringElement("1.0"), StringElement("SHA-256"), MapElement(emptyMap()),
        DeviceKeyInfo(MapElement(emptyMap())), StringElement("example.document"),
        ValidityInfo(time, time, time),
    ).toMapElement().value

    private fun document(values: Map<MapKey, DataElement>) = MDocBuilder("example.document").build(
        COSESign1(listOf(
            ByteStringElement(byteArrayOf()), MapElement(emptyMap()),
            ByteStringElement(EncodedCBORElement(MapElement(values)).toCBOR()), ByteStringElement(byteArrayOf()),
        ))
    )

    @Test
    fun ignoresAdditionalMsoFieldsWithoutChangingSignedBytes() {
        val extension = mapOf(MapKey("_branding") to ListElement(listOf(
            MapElement(mapOf(MapKey("name") to StringElement("Test credential"))),
            EncodedCBORElement(MapElement(emptyMap())),
            MapElement(emptyMap()),
            ListElement(emptyList()),
        )))
        for (values in listOf(extension + fields, fields + extension)) {
            val doc = document(values)
            val original = doc.toCBOR()
            val mso = assertNotNull(doc.MSO)
            assertEquals("example.document", mso.docType.value)
            assertEquals(time, mso.validityInfo.validFrom.value)
            assertSame(mso, doc.MSO)
            assertContentEquals(original, doc.toCBOR())
        }
    }

    @Test
    fun stillRejectsMissingOrMalformedRequiredFields() {
        for (values in listOf(
            fields - MapKey("docType"),
            fields + (MapKey("validityInfo") to StringElement("invalid")),
        )) {
            assertFails { document(values).MSO }
        }
    }

    @Test
    fun malformedDocTypeCannotVerify() {
        val doc = document(fields + (MapKey("docType") to NumberElement(1)))
        // Decoding may fail first on platforms with runtime casts.
        assertFalse(runCatching { doc.verifyDocType() }.getOrDefault(false))
    }
}
