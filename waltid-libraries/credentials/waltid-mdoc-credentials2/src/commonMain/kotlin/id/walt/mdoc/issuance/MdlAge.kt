package id.walt.mdoc.issuance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

object MdlAge {
    const val DOC_TYPE = "org.iso.18013.5.1.mDL"
    const val NAMESPACE = "org.iso.18013.5.1"
    const val BIRTH_DATE = "birth_date"
    const val AGE_IN_YEARS = "age_in_years"
    const val AGE_MAPPING = "<age-in-years>"

    private val fullDate = Regex("""[0-9]{4}-[0-9]{2}-[0-9]{2}""")

    fun hasRequiredBirthDateSource(ageConfigured: Boolean, birthDateSourceAvailable: Boolean): Boolean =
        !ageConfigured || birthDateSourceAvailable

    fun parseBirthDate(value: JsonElement?, path: String): LocalDate {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(text != null && fullDate.matches(text)) { "$path must be an ISO date (YYYY-MM-DD)" }
        return try {
            LocalDate.parse(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$path must be a valid ISO date (YYYY-MM-DD)", e)
        }
    }

    fun completedYears(birthDate: LocalDate, validFrom: Instant, birthDatePath: String): Int {
        val validityDate = validFrom.toLocalDateTime(TimeZone.UTC).date
        require(birthDate <= validityDate) { "$birthDatePath must be on or before the UTC date of MSO validFrom" }
        return birthDate.yearsUntil(validityDate)
    }
}
