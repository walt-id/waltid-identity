package id.walt.dcql.models.meta

import id.walt.dcql.models.CredentialFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Meta parameters specific to W3C Verifiable Credentials (jwt_vc_json, ldp_vc).
 * See Appendix B.1.1 of OpenID for Verifiable Presentations 1.0.
 */
@Serializable
@SerialName("W3cCredentialMeta")
data class JwtVcJsonMeta(
    /**
     * REQUIRED. An array of string arrays. Each inner array is a set of fully expanded type
     * IRIs, after `@context` is applied, that must be present on the credential. Compact type
     * strings still match, so a verifier can send either the expanded IRI or the compact name.
     */
    @SerialName(TYPE_VALUES_KEY)
    val typeValues: List<List<String>> // Now non-nullable, spec says "REQUIRED. A non-empty array..."

    // Potentially add other W3C specific meta fields if defined by profiles
) : CredentialQueryMeta {
    override val format = CredentialFormat.JWT_VC_JSON
    override fun getTypeString() = typeValues.joinToString()

    companion object {
        const val TYPE_VALUES_KEY = "type_values"
    }

    init {
        require(typeValues.isNotEmpty() && typeValues.all { it.isNotEmpty() }) {
            "type_values must be a non-empty array of non-empty string arrays"
        }
    }
}
