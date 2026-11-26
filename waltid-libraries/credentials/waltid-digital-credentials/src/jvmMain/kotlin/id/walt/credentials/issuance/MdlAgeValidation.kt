package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Checks the mDL age inputs available during profile or offer validation. */
object MdlAgeValidation {
    fun validateConfiguration(
        credentialData: JsonObject,
        mapping: JsonObject?,
        loginClaimDestinations: Collection<String>,
        validFrom: String?,
    ) {
        val namespace = credentialData[MdlAge.NAMESPACE] as? JsonObject
        val namespaceMapping = mapping?.get(MdlAge.NAMESPACE) as? JsonObject
        val login = MdlLoginClaimMapping.forDestinations(loginClaimDestinations)

        // Issuer mappings run after login. Skip values that will be replaced later.
        val mappedBirthDate = namespaceMapping?.get(MdlAge.BIRTH_DATE)
        val birthDateFromLogin = mappedBirthDate == null && login.canSupplyBirthDate
        val birthDateSource = if (mappedBirthDate == null) "credentialData" else "mapping"
        val birthDatePath = "$birthDateSource.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}"
        val birthDate = when {
            birthDateFromLogin -> null
            mappedBirthDate != null -> mappedBirthDate.takeUnless(::isDataFunction)
            else -> namespace?.get(MdlAge.BIRTH_DATE)
        }?.let { MdlAge.parseBirthDate(it, birthDatePath) }

        val mappedAge = namespaceMapping?.get(MdlAge.AGE_IN_YEARS)
        val configuredAge = mappedAge ?: namespace?.get(MdlAge.AGE_IN_YEARS) ?: return
        require(birthDate != null || mappedBirthDate != null || birthDateFromLogin) {
            "$birthDatePath is required when age_in_years is configured; supply it in the profile or runtime overrides"
        }
        val ageSource = if (mappedAge == null) "credentialData" else "mapping"
        val agePath = "$ageSource.${MdlAge.NAMESPACE}.${MdlAge.AGE_IN_YEARS}"
        val literalAge = when {
            mappedAge != null -> mappedAge.takeUnless(::isDataFunction)
            login.canSupplyAge -> null
            else -> configuredAge
        } ?: return
        val age = MdlAge.parseAge(literalAge, agePath)
        if (birthDate == null || validFrom == null || JsonPrimitive(validFrom).isTemplate()) return

        val fixedValidFrom = runCatching { MsoDataMapping.parseInstant(JsonPrimitive(validFrom), "validFrom") }.getOrNull()
        if (fixedValidFrom != null) {
            MdlAge.requireMatchesBirthDate(age, birthDate, fixedValidFrom, agePath, birthDatePath)
        }
    }

    private fun isDataFunction(value: JsonElement): Boolean =
        value is JsonPrimitive && value.isString && value.isTemplate()
}
