package id.walt.credentials.issuance

import id.walt.mdoc.issuance.MdlAge
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class MdocDataMappingTest {
    private val issuedAt = Instant.parse("2027-08-25T13:15:16.123Z")
    private val validFrom = Instant.parse("2027-08-26T00:00:00Z")
    private val data = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) { put(MdlAge.BIRTH_DATE, "1988-08-25") }
    }
    private val mapping = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) {
            put(MdlAge.AGE_IN_YEARS, MdlAge.AGE_MAPPING)
            put(MdlAge.BIRTH_DATE, "1990-08-26")
            put("issued_at", "<timestamp>")
        }
    }

    @Test
    fun derivesMdlAgeFromMappedBirthDateAndUsesTheIssuanceClockForTemplates() = runTest {
        val resolved = MdocDataMapping.resolve(
            credentialData = data,
            dataMapping = mapping,
            docType = MdlAge.DOC_TYPE,
            validFrom = validFrom,
            clock = InstantClock(issuedAt),
        ) { emptyMap() }
        val namespace = resolved.getValue(MdlAge.NAMESPACE).jsonObject
        assertEquals(JsonPrimitive("1990-08-26"), namespace[MdlAge.BIRTH_DATE])
        assertEquals(JsonPrimitive(37), namespace[MdlAge.AGE_IN_YEARS])
        assertEquals(JsonPrimitive(issuedAt.toString()), namespace["issued_at"])
    }

    @Test
    fun appliesOrdinaryTemplateRulesToOtherDocumentTypes() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            MdocDataMapping.resolve(
                credentialData = data,
                dataMapping = mapping,
                docType = "org.example.document",
                validFrom = validFrom,
                clock = InstantClock(issuedAt),
            ) { emptyMap() }
        }
        assertContains(error.message.orEmpty(), "Unknown dynamic data function \"age-in-years\"")
    }
}
