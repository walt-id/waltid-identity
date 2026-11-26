package id.walt.openid4vci.mdoc

import id.walt.mdoc.issuance.MdlAge as MdocMdlAge
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@Deprecated("Use id.walt.mdoc.issuance.MdlAge", ReplaceWith("MdlAge", "id.walt.mdoc.issuance.MdlAge"))
object MdlAge {
    const val DOC_TYPE = MdocMdlAge.DOC_TYPE
    const val NAMESPACE = MdocMdlAge.NAMESPACE
    const val BIRTH_DATE = MdocMdlAge.BIRTH_DATE
    const val AGE_IN_YEARS = MdocMdlAge.AGE_IN_YEARS
    const val AGE_MAPPING = MdocMdlAge.AGE_MAPPING

    fun parseBirthDate(value: JsonElement?, path: String): LocalDate = MdocMdlAge.parseBirthDate(value, path)

    fun completedYears(birthDate: LocalDate, validFrom: Instant, birthDatePath: String): Int =
        MdocMdlAge.completedYears(birthDate, validFrom, birthDatePath)
}
