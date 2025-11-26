package id.walt.dcql.models

import id.walt.dcql.models.meta.CredentialQueryMeta
import id.walt.dcql.models.meta.CredentialQueryMetaPolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a query for a specific credential type.
 * See: Section 6.1
 */
@Serializable
data class CredentialQuery(
    /** Identifier for this query within the request */
    val id: String,

    /* Credential format identifier (e.g., "jwt_vc_json", "mso_mdoc") */
    /**
     * REQUIRED. A string that specifies the format of the requested Credential.
     * Valid Credential Format Identifier values are defined in Appendix B.
     */
    val format: CredentialFormat,

    /* Can multiple credentials match this query? */
    /**
     * OPTIONAL. A boolean which indicates whether multiple Credentials can be
     * returned for this Credential Query. If omitted, the default value is false.
     */
    val multiple: Boolean = false,

    /* Format-specific metadata constraints */
    /*
     * meta is REQUIRED. If no specific constraints, it should be an empty object,
     * which can map to NoMeta or a specific meta type with empty valid values if allowed.
     * For simplicity with the custom serializer, keeping it nullable but ensuring
     * the serializer handles the "empty object means NoMeta" case.
     * A non-nullable type with a default NoMeta would also be an option if the
     * custom serializer wasn't handling the "empty object" case.
     */
    /**
     * REQUIRED. An object defining additional properties requested by the Verifier
     * that apply to the metadata and validity data of the Credential. The properties
     * of this object are defined per Credential Format. Examples of those are in
     * Appendix B.3.5 and Appendix B.2.3. If empty, no specific constraints are
     * placed on the metadata or validity of the requested Credential.
     */
    @Serializable(with = CredentialQueryMetaPolymorphicSerializer::class)
    val meta: CredentialQueryMeta, // = NoMeta // mandatory to be set since draft 29

    /* Issuer constraints */
    /**
     * OPTIONAL. A non-empty array of objects as defined in Section 6.1.1 that specifies
     * expected authorities or trust frameworks that certify Issuers, that the Verifier
     * will accept. Every Credential returned by the Wallet SHOULD match at least one of
     * the conditions present in the corresponding trusted_authorities array if present.
     */
    @SerialName("trusted_authorities")
    val trustedAuthorities: List<TrustedAuthoritiesQuery>? = null,

    /* Holder binding requirement */
    /**
     * OPTIONAL. A boolean which indicates whether the Verifier requires a Cryptographic
     * Holder Binding proof. The default value is true, i.e., a Verifiable Presentation
     * with Cryptographic Holder Binding is required. If set to false, the Verifier
     * accepts a Credential without Cryptographic Holder Binding proof.
     */
    @SerialName("require_cryptographic_holder_binding")
    val requireCryptographicHolderBinding: Boolean = true,

    /* Specific claims requested */
    /**
     * OPTIONAL. A non-empty array of objects as defined in Section 6.3 that specifies
     * claims in the requested Credential. Verifiers MUST NOT point to the same claim
     * more than once in a single query. Wallets SHOULD ignore such duplicate claim
     * queries.
     */
    val claims: List<ClaimsQuery>? = null,

    /* Combinations of claim IDs */
    /**
     * OPTIONAL. A non-empty array containing arrays of identifiers for elements in
     * claims that specifies which combinations of claims for the Credential are
     * requested. The rules for selecting claims to send are defined in Section 6.4.1.
     */
    @SerialName("claim_sets")
    val claimSets: List<List<String>>? = null,
) {
    init {
        val metaFormat = meta.format
        if (metaFormat != null) {
            require(metaFormat == format) { "Invalid query \"meta\" for this format. Credential format is set to \"$format\", but query meta is for \"$metaFormat\"." }
        }
    }
}
