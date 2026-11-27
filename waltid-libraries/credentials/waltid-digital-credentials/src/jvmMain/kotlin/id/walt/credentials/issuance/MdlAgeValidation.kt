package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Checks the mDL age inputs available during profile or offer validation. */
object MdlAgeValidation {
    fun validateConfiguration(
        credentialData: JsonObject,
        mapping: JsonObject?,
        loginClaimDestinations: Collection<String>,
        validFrom: String?,
        explicitBirthYear: JsonElement? = null,
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

        val mappedBirthYear = namespaceMapping?.get(MdlAge.AGE_BIRTH_YEAR)
        val configuredBirthYear = mappedBirthYear ?: namespace?.get(MdlAge.AGE_BIRTH_YEAR)
        if (explicitBirthYear != null && (mappedBirthYear != null || login.canSupplyBirthYear)) {
            val path = "credentialData.${MdlAge.NAMESPACE}.${MdlAge.AGE_BIRTH_YEAR}"
            val year = MdlAge.parseBirthYear(explicitBirthYear, path)
            birthDate?.let { MdlAge.requireMatchesBirthYear(year, it, path) }
        }
        val birthDateAvailable = birthDate != null || mappedBirthDate != null || birthDateFromLogin
        if (configuredBirthYear != null) {
            require(birthDateAvailable) {
                "$birthDatePath is required when age_birth_year is configured; supply it in the profile or runtime overrides"
            }
            val yearSource = if (mappedBirthYear == null) "credentialData" else "mapping"
            val yearPath = "$yearSource.${MdlAge.NAMESPACE}.${MdlAge.AGE_BIRTH_YEAR}"
            val literalBirthYear = when {
                mappedBirthYear != null -> mappedBirthYear.takeUnless(::isDataFunction)
                login.canSupplyBirthYear -> null
                else -> configuredBirthYear
            }
            literalBirthYear?.let {
                val year = MdlAge.parseBirthYear(it, yearPath)
                birthDate?.let { date -> MdlAge.requireMatchesBirthYear(year, date, yearPath) }
            }
        }

        val mappedAge = namespaceMapping?.get(MdlAge.AGE_IN_YEARS)
        val configuredAge = mappedAge ?: namespace?.get(MdlAge.AGE_IN_YEARS) ?: return
        require(birthDateAvailable) {
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

    /** Update a profile's inherited year when a literal offer override changes the birth date. */
    fun reconcileInheritedBirthYear(
        credentialData: JsonObject,
        credentialDataOverride: JsonObject?,
        mapping: JsonObject?,
        mappingOverride: JsonObject?,
    ): JsonObject {
        val namespace = credentialData[MdlAge.NAMESPACE] as? JsonObject ?: return credentialData
        val namespaceOverride = credentialDataOverride?.get(MdlAge.NAMESPACE) as? JsonObject
        val namespaceMapping = mapping?.get(MdlAge.NAMESPACE) as? JsonObject
        val namespaceMappingOverride = mappingOverride?.get(MdlAge.NAMESPACE) as? JsonObject
        if (MdlAge.AGE_BIRTH_YEAR !in namespace ||
            namespaceOverride?.containsKey(MdlAge.AGE_BIRTH_YEAR) == true ||
            namespaceMapping?.containsKey(MdlAge.AGE_BIRTH_YEAR) == true
        ) return credentialData
        if (namespaceOverride?.containsKey(MdlAge.BIRTH_DATE) != true &&
            namespaceMappingOverride?.containsKey(MdlAge.BIRTH_DATE) != true
        ) return credentialData

        val mappedBirthDate = namespaceMapping?.get(MdlAge.BIRTH_DATE)
        val birthDate = mappedBirthDate ?: namespace[MdlAge.BIRTH_DATE] ?: return credentialData
        if (birthDate == JsonNull || isDataFunction(birthDate)) return credentialData
        val source = if (mappedBirthDate == null) "credentialData" else "mapping"
        val year = MdlAge.parseBirthDate(birthDate, "$source.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}").year
        return JsonObject(credentialData + (MdlAge.NAMESPACE to JsonObject(
            namespace + (MdlAge.AGE_BIRTH_YEAR to JsonPrimitive(year)),
        )))
    }

    private fun isDataFunction(value: JsonElement): Boolean =
        value is JsonPrimitive && value.isString && value.isTemplate()
}
