package id.walt.mdoc.issuance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

object MdlAge {
    const val DOC_TYPE = "org.iso.18013.5.1.mDL"
    const val NAMESPACE = "org.iso.18013.5.1"
    const val BIRTH_DATE = "birth_date"
    const val AGE_IN_YEARS = "age_in_years"

    private val fullDate = Regex("""[0-9]{4}-[0-9]{2}-[0-9]{2}""")

    /** Check the optional age claim on the final mDL namespace before signing. */
    fun validate(namespace: JsonObject, validFrom: Instant, namespacePath: String) {
        val age = namespace[AGE_IN_YEARS] ?: return
        val agePath = "$namespacePath.$AGE_IN_YEARS"
        val birthDatePath = "$namespacePath.$BIRTH_DATE"
        requireMatchesBirthDate(
            age = parseAge(age, agePath),
            birthDate = parseBirthDate(namespace[BIRTH_DATE], birthDatePath),
            validFrom = validFrom,
            agePath = agePath,
            birthDatePath = birthDatePath,
        )
    }

    fun parseAge(value: JsonElement, path: String): UInt {
        val age = (value as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toUIntOrNull()
        require(age != null) { "$path must be a nonnegative integer" }
        return age
    }

    fun parseBirthDate(value: JsonElement?, path: String): LocalDate {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(text != null && fullDate.matches(text)) { "$path must be an ISO date (YYYY-MM-DD)" }
        return try {
            LocalDate.parse(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$path must be a valid ISO date (YYYY-MM-DD)", e)
        }
    }

    fun requireMatchesBirthDate(
        age: UInt,
        birthDate: LocalDate,
        validFrom: Instant,
        agePath: String,
        birthDatePath: String,
    ) {
        val validityDate = validFrom.toLocalDateTime(TimeZone.UTC).date
        require(birthDate <= validityDate) { "$birthDatePath must be on or before the UTC date of MSO validFrom" }
        val expectedAge = birthDate.yearsUntil(validityDate).toUInt()
        require(age == expectedAge) { "$agePath must be $expectedAge for the birth_date and UTC date of msoData.validFrom" }
    }
}
