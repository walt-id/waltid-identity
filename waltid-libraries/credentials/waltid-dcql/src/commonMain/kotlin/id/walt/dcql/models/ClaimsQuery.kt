package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents constraints on specific claims within a credential.
 * See: Section 6.3
 *
 * For JSON-based credentials (jwt_vc_json, vc+sd-jwt, dc+sd-jwt):
 *   - Use `path` to specify the claim location using JSON path syntax
 *
 * For ISO mDL/mdoc credentials (mso_mdoc):
 *   - Use `namespace` and `claim_name` to specify the claim
 *   - Example: namespace="org.iso.18013.5.1", claim_name="given_name"
 */
@Serializable
data class ClaimsQuery(
    /** Required if claim_sets is present in parent CredentialQuery */
    val id: String? = null,

    /** Path to the claim (format-specific interpretation) - for JSON-based credentials */
    val path: List<JsonElement>? = null,

    /** Namespace for mdoc claims (e.g., "org.iso.18013.5.1") - for mso_mdoc format */
    val namespace: String? = null,

    /** Claim name within the namespace - for mso_mdoc format */
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
        pathStrings: List<String>,
        values: List<JsonPrimitive>? = null
    ) : this(
        id = id,
        path = pathStrings.map { JsonPrimitive(it) },
        values = values
    )

    /** Constructor for mdoc claims */
    constructor(
        id: String? = null,
        namespace: String,
        claimName: String,
        values: List<JsonPrimitive>? = null,
        intentToRetain: Boolean? = null
    ) : this(
        id = id,
        path = null,
        namespace = namespace,
        claimName = claimName,
        values = values,
        intentToRetain = intentToRetain
    )
}
