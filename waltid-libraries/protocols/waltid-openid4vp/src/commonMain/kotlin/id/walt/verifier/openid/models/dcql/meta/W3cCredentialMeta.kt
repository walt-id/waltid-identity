package id.walt.verifier.openid.models.dcql.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Meta parameters specific to W3C Verifiable Credentials (jwt_vc_json, ldp_vc).
 * See Appendix B.1.1 of OpenID4VP spec (draft 28).
 */
@Serializable
@SerialName("W3cCredentialMeta")
data class W3cCredentialMeta(
    /**
     * REQUIRED. An array of string arrays that specifies the fully expanded types (IRIs)
     * after the @context was applied that the Verifier accepts.
     */
    @SerialName("type_values")
    val typeValues: List<List<String>>
    // Potentially add other W3C specific meta fields if defined by profiles
) : CredentialQueryMeta
