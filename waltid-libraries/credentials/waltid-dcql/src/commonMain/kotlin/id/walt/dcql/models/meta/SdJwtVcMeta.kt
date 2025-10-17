package id.walt.dcql.models.meta

import id.walt.dcql.models.CredentialFormat
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
    @SerialName(VCT_VALUES_KEY)
    val vctValues: List<String> // Now non-nullable, spec says "REQUIRED. A non-empty array..."
    // Potentially add other SD-JWT VC specific meta fields
) : CredentialQueryMeta {
    override val format = CredentialFormat.DC_SD_JWT

    companion object {
        const val VCT_VALUES_KEY = "vct_values"
    }

    init {
        require(vctValues.isNotEmpty()) { "vct_values must be a non-empty array" }
    }
}
