package id.walt.issuer2.service

import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.credentials.issuance.MdlLoginClaimMapping
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

internal object MdlAgeValidation {
    private const val BIRTH_DATE_FIELD = "credentialData.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}"

    fun validate(
        credentialData: JsonObject,
        mapping: JsonObject?,
        idTokenClaimsMapping: Map<String, String>?,
        validFrom: String?,
        canUseLoginClaims: Boolean,
    ) {
        val namespace = credentialData[MdlAge.NAMESPACE] as? JsonObject
        val namespaceMapping = mapping?.get(MdlAge.NAMESPACE) as? JsonObject
        val credentialBirthDate = namespace?.get(MdlAge.BIRTH_DATE)
        val birthDateFromClaims = canUseLoginClaims &&
            MdlLoginClaimMapping.forDestinations(idTokenClaimsMapping.orEmpty().values).canSupplyBirthDate
        val literalBirthDate = credentialBirthDate
            ?.takeUnless { it == JsonNull && birthDateFromClaims }
            ?.let { MdlAge.parseBirthDate(it, BIRTH_DATE_FIELD) }
        val mappedBirthDate = namespaceMapping?.get(MdlAge.BIRTH_DATE)
        val birthDate = if (mappedBirthDate == null) {
            literalBirthDate
        } else {
            mappedBirthDate.takeUnless(::isDataFunction)
                ?.let { MdlAge.parseBirthDate(it, "mapping.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}") }
        }

        val fixedValidFrom = validFrom?.takeUnless { JsonPrimitive(it).isTemplate() }?.let {
            it.toLongOrNull()?.let { seconds -> Instant.fromEpochSeconds(seconds) }
                ?: Instant.parseOrNull(it)
        }
        val expectedAge = if (birthDate != null && fixedValidFrom != null) {
            MdlAge.completedYears(birthDate, fixedValidFrom, BIRTH_DATE_FIELD).toUInt()
        } else {
            null
        }
        val credentialAge = namespace?.get(MdlAge.AGE_IN_YEARS)
        val mappedAge = namespaceMapping?.get(MdlAge.AGE_IN_YEARS)
        require(
            MdlAge.hasRequiredBirthDateSource(
                ageConfigured = credentialAge != null || mappedAge != null,
                birthDateSourceAvailable = birthDate != null || mappedBirthDate != null || birthDateFromClaims,
            )
        ) {
            "$BIRTH_DATE_FIELD is required when age_in_years is configured; supply it in the profile or runtime overrides"
        }
        credentialAge?.let { validateLiteralAge(it, "credentialData", expectedAge) }
        mappedAge?.takeUnless(::isDataFunction)?.let { validateLiteralAge(it, "mapping", expectedAge) }
    }

    private fun isDataFunction(value: JsonElement): Boolean =
        value is JsonPrimitive && value.isString && value.isTemplate()

    private fun validateLiteralAge(value: JsonElement, source: String, expectedAge: UInt?) {
        val path = "$source.${MdlAge.NAMESPACE}.${MdlAge.AGE_IN_YEARS}"
        val age = (value as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toUIntOrNull()
        require(age != null) { "$path must be a nonnegative integer" }
        require(expectedAge == null || age == expectedAge) {
            "$path must be $expectedAge for the birth_date and UTC date of msoData.validFrom"
        }
    }

    private fun Instant.Companion.parseOrNull(value: String): Instant? = try {
        parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
