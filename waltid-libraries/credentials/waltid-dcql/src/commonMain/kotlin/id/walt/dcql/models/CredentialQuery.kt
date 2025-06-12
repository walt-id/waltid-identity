package id.walt.dcql.models

import id.walt.dcql.models.meta.CredentialQueryMeta
import id.walt.dcql.models.meta.CredentialQueryMetaPolymorphicSerializer
import id.walt.dcql.models.meta.NoMeta
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

    /** Credential format identifier (e.g., "jwt_vc_json", "mso_mdoc") */
    val format: CredentialFormat,

    /** Can multiple credentials match this query? */
    val multiple: Boolean = false,

    /*
     * meta is REQUIRED. If no specific constraints, it should be an empty object,
     * which can map to NoMeta or a specific meta type with empty valid values if allowed.
     * For simplicity with the custom serializer, keeping it nullable but ensuring
     * the serializer handles the "empty object means NoMeta" case.
     * A non-nullable type with a default NoMeta would also be an option if the
     * custom serializer wasn't handling the "empty object" case.
     */
    /** Format-specific metadata constraints */
    @Serializable(with = CredentialQueryMetaPolymorphicSerializer::class)
    val meta: CredentialQueryMeta = NoMeta, // mandatory to be set since draft 29
    /*
    * Default to NoMeta if not provided,
    * assuming serializer handles {} as NoMeta.
    * The spec says "meta: REQUIRED", so null is not ideal
    * unless the serializer interprets absence as NoMeta.
    */
    // val meta: CredentialQueryMeta = NoMeta, // Alternative: Non-nullable with default

    /** Issuer constraints */
    @SerialName("trusted_authorities")
    val trustedAuthorities: List<TrustedAuthoritiesQuery>? = null,

    /** Holder binding requirement */
    @SerialName("require_cryptographic_holder_binding")
    val requireCryptographicHolderBinding: Boolean = true,

    /** Specific claims requested */
    val claims: List<ClaimsQuery>? = null,

    /** Combinations of claim IDs */
    @SerialName("claim_sets")
    val claimSets: List<List<String>>? = null,
)
