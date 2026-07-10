package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents constraints on specific claims within a credential.
 * See: Section 6.3
 */
@Serializable
data class ClaimsQuery(
    /** Required if claim_sets is present in parent CredentialQuery */
    val id: String? = null,

    /** Path to the claim (format-specific interpretation) - nullable for mdoc format */
    val path: List<JsonElement>? = null,

    /** Namespace for mdoc credentials (e.g., "org.iso.18013.5.1") */
    val namespace: String? = null,

    /** Claim name within the namespace for mdoc credentials */
    @SerialName("claim_name")
    val claimName: String? = null,

    /** Optional specific values to match */
    val values: List<JsonPrimitive>? = null,

    /** defined at https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-parameter-in-the-claims-que */
    @SerialName("intent_to_retain")
    val intentToRetain: Boolean? = null
) {
    constructor(
        id: String? = null,
        pathStrings: List<String>?,
        values: List<JsonPrimitive>? = null
    ) : this(
        id = id,
        path = pathStrings?.map { JsonPrimitive(it) },
        values = values
    )

    /**
     * Returns the effective path for this claim query.
     * For mdoc credentials using namespace/claimName, constructs a path from those fields.
     * For other formats, returns the path field directly.
     */
    fun effectivePath(): List<JsonElement>? = when {
        path != null -> path
        namespace != null && claimName != null -> listOf(JsonPrimitive(namespace), JsonPrimitive(claimName))
        else -> null
    }

    /**
     * Returns a string key for this claim query, suitable for use in maps.
     */
    fun pathKey(): String = when {
        path != null -> path.joinToString(".")
        namespace != null && claimName != null -> "$namespace.$claimName"
        else -> id ?: "unknown"
    }
}
