package id.walt.issuer2.service

import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import id.walt.credentials.issuance.CredentialDataMergeUtils.isTemplate
import id.walt.openid4vci.mdoc.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

internal object MdlAgeValidation {
    private const val NAMESPACE_PATH = "$['${MdlAge.NAMESPACE}']"
    private const val BIRTH_DATE_PATH = "$NAMESPACE_PATH['${MdlAge.BIRTH_DATE}']"
    private const val BIRTH_DATE_FIELD = "credentialData.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}"

    fun validate(
        credentialData: JsonObject,
        mapping: JsonObject?,
        idTokenClaimsMapping: Map<String, String>?,
        validFrom: String?,
    ) {
        val namespace = credentialData[MdlAge.NAMESPACE] as? JsonObject
        val namespaceMapping = mapping?.get(MdlAge.NAMESPACE) as? JsonObject
        val credentialBirthDate = namespace?.get(MdlAge.BIRTH_DATE)
        val claimTargets = claimTargets(idTokenClaimsMapping)
        val birthDateFromClaims = BIRTH_DATE_PATH in claimTargets || NAMESPACE_PATH in claimTargets
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
        if (credentialAge != null || mappedAge != null) {
            require(birthDate != null || mappedBirthDate != null || birthDateFromClaims) {
                "$BIRTH_DATE_FIELD is required when age_in_years is configured"
            }
        }
        credentialAge?.let { validateLiteralAge(it, "credentialData", expectedAge) }
        mappedAge?.takeUnless(::isDataFunction)?.let { validateLiteralAge(it, "mapping", expectedAge) }
    }

    // The claims mapper writes into existing paths. Supply a destination for a deferred birth date
    // on the temporary claims-mapping input, leaving the profile and stored credential data intact.
    fun claimsDestination(credentialData: JsonObject, idTokenClaimsMapping: Map<String, String>): JsonObject {
        val namespace = credentialData[MdlAge.NAMESPACE] as? JsonObject
        val targets = claimTargets(idTokenClaimsMapping)
        if (namespace == null && MdlAge.NAMESPACE in credentialData) return credentialData
        if (BIRTH_DATE_PATH in targets && (namespace?.get(MdlAge.BIRTH_DATE) ?: JsonNull) == JsonNull) {
            val withBirthDate = JsonObject(namespace.orEmpty() + (MdlAge.BIRTH_DATE to JsonPrimitive("")))
            return JsonObject(credentialData + (MdlAge.NAMESPACE to withBirthDate))
        }
        if (NAMESPACE_PATH in targets && namespace == null) {
            return JsonObject(credentialData + (MdlAge.NAMESPACE to JsonObject(emptyMap())))
        }
        return credentialData
    }

    private fun claimTargets(mapping: Map<String, String>?): Set<String> =
        mapping.orEmpty().values.mapNotNullTo(mutableSetOf()) { path ->
            try {
                JsonPath.compile(path).path.replace('"', '\'')
            } catch (_: InvalidPathException) {
                null
            }
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
