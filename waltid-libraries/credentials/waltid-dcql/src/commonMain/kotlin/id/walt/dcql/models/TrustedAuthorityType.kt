package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Types for Trusted Authorities Query.
 * See Section 6.1.1 of OpenID4VP spec.
 */
@Serializable
enum class TrustedAuthorityType {
    @SerialName("aki")
    AKI,
    @SerialName("etsi_tl")
    ETSI_TL,
    @SerialName("openid_federation")
    OPENID_FEDERATION,
}
