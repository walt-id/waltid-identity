package id.walt.issuer2.notifications

import kotlinx.serialization.Serializable

@Serializable
enum class IssuanceSessionEvent {
    resolved_credential_offer,
    requested_token,
    sdjwt_issue,
    jwt_issue,
    generated_mdoc,
    issuance_status,
}
