package id.walt.credentials.issuance

import id.walt.mdoc.issuance.MdlAge
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Instant

class MdocDataMappingTest {
    private val issuedAt = Instant.parse("2027-08-25T13:15:16.123Z")
    private val validFrom = Instant.parse("2027-08-26T00:00:00Z")
    private val data = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) { put(MdlAge.BIRTH_DATE, "1988-08-25") }
    }
    private val mapping = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) {
            put(MdlAge.AGE_IN_YEARS, 37)
            put(MdlAge.BIRTH_DATE, "1990-08-26")
            put("issued_at", "<timestamp>")
        }
    }

    @Test
    fun validatesSuppliedAgeAgainstMappedBirthDateAndUsesTheIssuanceClockForTemplates() = runTest {
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
    fun leavesAgeAbsentWhenOmitted() = runTest {
        val resolved = MdocDataMapping.resolve(data, null, MdlAge.DOC_TYPE, validFrom, InstantClock(issuedAt)) { emptyMap() }
        assertSame(data, resolved)
    }

    @Test
    fun rejectsWrongAgeAfterMapping() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            MdocDataMapping.resolve(
                credentialData = data,
                dataMapping = mapping,
                docType = MdlAge.DOC_TYPE,
                validFrom = issuedAt,
                clock = InstantClock(issuedAt),
            ) { emptyMap() }
        }
        assertContains(error.message.orEmpty(), "credentialData.${MdlAge.NAMESPACE}.age_in_years must be 36")
    }

    private val dataWithBirthYear = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) {
            put(MdlAge.BIRTH_DATE, "1988-08-25")
            put(MdlAge.AGE_BIRTH_YEAR, 1988)
            put(MdlAge.AGE_IN_YEARS, 36)
        }
    }
    private val birthDateMapping = buildJsonObject {
        putJsonObject(MdlAge.NAMESPACE) {
            put(MdlAge.BIRTH_DATE, "<date-before:13242d>")
        }
    }
    private val claimTime = Instant.parse("2026-11-27T14:00:00Z")

    @Test
    fun updatesInheritedBirthYearWhenResolvedMappingChangesTheBirthDate() = runTest {
        val resolved = MdocDataMapping.resolve(
            dataWithBirthYear, birthDateMapping, MdlAge.DOC_TYPE, claimTime, InstantClock(claimTime),
            mdlBirthYear = MdlBirthYear.Inherited,
        ) { emptyMap() }
        val namespace = resolved.getValue(MdlAge.NAMESPACE).jsonObject
        assertEquals(JsonPrimitive("1990-08-26"), namespace[MdlAge.BIRTH_DATE])
        assertEquals(JsonPrimitive(1990), namespace[MdlAge.AGE_BIRTH_YEAR])
        assertEquals(JsonPrimitive(36), namespace[MdlAge.AGE_IN_YEARS])
    }

    @Test
    fun rejectsExplicitBirthYearWhenResolvedMappingChangesTheBirthDate() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            MdocDataMapping.resolve(
                dataWithBirthYear, birthDateMapping, MdlAge.DOC_TYPE, claimTime, InstantClock(claimTime),
                mdlBirthYear = MdlBirthYear.Explicit(JsonPrimitive(1988)),
            ) { emptyMap() }
        }
        assertContains(error.message.orEmpty(), "credentialData.${MdlAge.NAMESPACE}.age_birth_year must be 1990")
    }

    @Test
    fun rejectsInvalidAgeWithoutMappingAndLeavesOtherDocTypesAlone() = runTest {
        for (age in listOf(JsonPrimitive(37), JsonPrimitive("39"), JsonNull)) {
            val supplied = buildJsonObject {
                putJsonObject(MdlAge.NAMESPACE) {
                    put(MdlAge.BIRTH_DATE, "1988-08-25")
                    put(MdlAge.AGE_IN_YEARS, age)
                }
            }
            val error = assertFailsWith<IllegalArgumentException> {
                MdocDataMapping.resolve(supplied, null, MdlAge.DOC_TYPE, validFrom, InstantClock(issuedAt)) { emptyMap() }
            }
            assertContains(error.message.orEmpty(), "credentialData.${MdlAge.NAMESPACE}.age_in_years")
            val other = MdocDataMapping.resolve(supplied, null, "org.example.document", validFrom, InstantClock(issuedAt)) { emptyMap() }
            assertSame(supplied, other)
        }
    }
}
