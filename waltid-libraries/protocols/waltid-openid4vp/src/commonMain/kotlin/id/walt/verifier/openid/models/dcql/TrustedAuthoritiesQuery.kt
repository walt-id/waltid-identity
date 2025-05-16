package id.walt.verifier.openid.models.dcql

import kotlinx.serialization.Serializable

/**
 * Represents constraints on trusted issuers or frameworks.
 * See: Section 6.1.1
 */
@Serializable
data class TrustedAuthoritiesQuery(
    val type: TrustedAuthorityType,
    val values: List<String>,
)
