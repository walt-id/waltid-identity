package id.walt.dcql.models.meta

import id.walt.dcql.models.CredentialFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Meta parameters specific to ISO mdoc (mso_mdoc).
 * See Appendix B.2.3 of OpenID4VP spec (draft 28).
 */
@Serializable
@SerialName("MsoMdocMeta")
data class MsoMdocMeta(
    /**
     * REQUIRED. String that specifies an allowed value for the doctype
     * of the requested Verifiable Credential.
     */
    @SerialName(DOCTYPE_VALUE_KEY)
    val doctypeValue: String
    // Potentially add other mdoc specific meta fields
) : CredentialQueryMeta {
    override val format = CredentialFormat.MSO_MDOC
    override fun getTypeString() = doctypeValue

    companion object {
        const val DOCTYPE_VALUE_KEY = "doctype_value"
    }

}
