package id.walt.dcql.models

import kotlinx.serialization.Serializable

/**
 * Represents constraints on trusted issuers or frameworks.
 * See: Section 6.1.1
 */
@Serializable
data class TrustedAuthoritiesQuery(
    /**
     * REQUIRED. A string uniquely identifying the type of information about the
     * issuer trust framework. Types defined by this specification are listed below.
     */
    val type: TrustedAuthorityType, // e.g., "aki", "etsi_tl", "openid_federation"

    /**
     * REQUIRED. A non-empty array of strings, where each string (value) contains
     * information specific to the used Trusted Authorities Query type that allows
     * the identification of an issuer, a trust framework, or a federation that an
     * issuer belongs to.
     */
    val values: List<String>,
)
