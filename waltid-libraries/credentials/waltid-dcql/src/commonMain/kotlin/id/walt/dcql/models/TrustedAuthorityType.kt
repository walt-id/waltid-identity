package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Types for Trusted Authorities Query.
 * See Section 6.1.1 of OpenID4VP spec.
 */
@Serializable
enum class TrustedAuthorityType {
    /**
     * Contains the KeyIdentifier of the AuthorityKeyIdentifier as defined in
     * Section 4.2.1.1 of RFC5280, encoded as base64url. The raw byte representation
     * of this element MUST match with the AuthorityKeyIdentifier element of an
     * X.509 certificate in the certificate chain present in the Credential (e.g.,
     * in the header of an mdoc or SD-JWT). Note that the chain can consist of a
     * single certificate and the Credential can include the entire X.509 chain or
     * parts of it.
     */
    @SerialName("aki")
    AKI, // Example `values`: ["s9tIpPmhxdiuNkHMEWNpYim8S8Y"]

    /**
     * The identifier of a Trusted List as specified in ETSI TS 119 612 ETSI.TL. An
     * ETSI Trusted List contains references to other Trusted Lists, creating a list
     * of trusted lists, or entries for Trust Service Providers with corresponding
     * service description and X.509 Certificates. The trust chain of a matching
     * Credential MUST contain at least one X.509 Certificate that matches one of
     * the entries of the Trusted List or its cascading Trusted Lists.
     */
    @SerialName("etsi_tl")
    ETSI_TL, // Example `values`: ["https://lotl.example.com"]

    /**
     * The Entity Identifier as defined in Section 1 of OpenID.Federation that is bound
     * to an entity in a federation. While this Entity Identifier could be any entity
     * in that ecosystem, this entity would usually have the Entity Configuration of a
     * Trust Anchor. A valid trust path, including the given Entity Identifier, must be
     * constructible from a matching credential.
     */
    @SerialName("openid_federation")
    OPENID_FEDERATION, // Example `values`: ["https://trustanchor.example.com"]
}
