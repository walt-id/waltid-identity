@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.webdatafetching.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ResponseBodyDecodingConfiguration(
    /**
     * JSON format is often used to read the output of third-party services or in other dynamic environments where new properties can be added during the API evolution.
     * By default, unknown keys encountered during deserialization produce an error.
     * You can avoid this and just ignore such keys by setting this property to true.
     */
    val allowUnknownFields: Boolean = true,

    /**
     * Kotlin's naming policy recommends naming enum values using either uppercase underscore-separated names or upper camel case names.
     * Json uses exact Kotlin enum values names for decoding by default.
     * However, sometimes third-party JSONs have such values named in lowercase or some mixed case.
     */
    val caseInsensitiveEnums: Boolean = true,

    /** Do not enforce certain restrictions, e.g. keys and string literals must be quoted */
    val lenientParsing: Boolean = false,

    /**
     * JSON formats that from third parties can evolve, sometimes changing the field types. This can lead to exceptions during decoding when the actual values do not match the expected values. The default Json implementation is strict with respect to input types as was demonstrated in the Type safety is enforced section. You can relax this restriction using the coerceInputValues property.
     *
     * This property only affects decoding. It treats a limited subset of invalid input values as if the corresponding property was missing. The current list of supported invalid values is:
     *
     * - null inputs for non-nullable types
     * - unknown values for enums
     *
     * Typically used with [explicitNullValues]=false.
     */
    val coerce: Boolean = false,


    val explicitNullValues: Boolean = true,
    val allowTrailingCommas: Boolean = true,
    val ignoreComments: Boolean = true,
) {

    val json by lazy {
        Json {
            ignoreUnknownKeys = allowUnknownFields
            decodeEnumsCaseInsensitive = caseInsensitiveEnums
            isLenient = lenientParsing
            coerceInputValues = coerce
            explicitNulls = explicitNullValues
            allowTrailingComma = allowTrailingCommas
            allowComments = ignoreComments
        }
    }

    companion object {
        val Example = ResponseBodyDecodingConfiguration(
            allowUnknownFields = true,
            caseInsensitiveEnums = true,
            lenientParsing = false,
            coerce = false,
            explicitNullValues = true,
            allowTrailingCommas = true,
            ignoreComments = true
        )
    }
}
