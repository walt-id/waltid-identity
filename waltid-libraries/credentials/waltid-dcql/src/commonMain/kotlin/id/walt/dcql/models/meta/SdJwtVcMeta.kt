package id.walt.dcql.models.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Meta parameters specific to IETF SD-JWT VC (dc+sd-jwt).
 * See Appendix B.3.5 of OpenID4VP spec (draft 28).
 */
@Serializable
@SerialName("SdJwtVcMeta")
data class SdJwtVcMeta(
    /**
     * REQUIRED. An array of strings that specifies allowed values for the type
     * of the requested Verifiable Credential (the 'vct' claim in SD-JWT VC).
     */
    @SerialName("vct_values")
    val vctValues: List<String> // Now non-nullable, spec says "REQUIRED. A non-empty array..."
    // Potentially add other SD-JWT VC specific meta fields
) : CredentialQueryMeta {
    init {
        require(vctValues.isNotEmpty()) { "vct_values must be a non-empty array" }
    }
}
