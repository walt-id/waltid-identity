@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.toMdocTDateString
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.schemafulJsonToCborElement
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import kotlin.time.Instant

class MdocTDateRangeTest {
    @Test
    fun normalizedTimestampAcceptsBothFourDigitYearBoundaries() {
        for (value in listOf("0000-01-01T00:00:00Z", "9999-12-31T23:59:59Z")) {
            assertEquals(value, Instant.parse(value).toMdocTDateString())
        }
        assertEquals("2024-02-29T12:34:56Z", Instant.parse("2024-02-29T13:34:56.987+01:00").toMdocTDateString())
    }

    @Test
    fun yearRangeIsCheckedAfterUtcNormalization() {
        for (value in listOf("0000-01-01T00:00:00+01:00", "9999-12-31T23:59:59-01:00")) {
            assertFailsWith<IllegalArgumentException> { Instant.parse(value).toMdocTDateString() }
        }
    }

    @Test
    fun allMsoValidityFieldsRejectOutOfRangeYears() {
        val ordinary = Instant.parse("2026-09-08T00:00:00Z")
        for (value in listOf("-0001-12-31T23:59:59Z", "+10000-01-01T00:00:00Z")) {
            val invalid = Instant.parse(value)
            for (info in listOf(
                ValidityInfo(invalid, ordinary, ordinary),
                ValidityInfo(ordinary, invalid, ordinary),
                ValidityInfo(ordinary, ordinary, invalid),
                ValidityInfo(ordinary, ordinary, ordinary, invalid),
            )) {
                val failure = assertFails { coseCompliantCbor.encodeToByteArray(info) }
                assertTrue(generateSequence(failure) { it.cause }.any { it.message == "mdoc timestamps require a four-digit year" })
            }
        }
    }

    @Test
    fun schemaDatetimeUsesTheSameYearGuard() {
        assertFailsWith<IllegalArgumentException> {
            JsonPrimitive("+10000-01-01T00:00:00Z").schemafulJsonToCborElement(MdocsSchemaType(MdocsDatatype.DATETIME))
        }
    }
}
