package id.walt.dcql.models

import kotlinx.serialization.Serializable

/**
 * Represents constraints on trusted issuers or frameworks.
 * See: Section 6.1.1
 */
@Serializable
data class TrustedAuthoritiesQuery(
    /**  e.g., "aki", "etsi_tl", "openid_federation" */
    val type: TrustedAuthorityType,
    val values: List<String>,
)
