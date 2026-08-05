package id.walt.issuer2.notifications

import kotlinx.serialization.Serializable

@Serializable
enum class IssuanceSessionEvent {
    credential_offer_created,
    resolved_credential_offer,

    pushed_authorization_request_received,
    pushed_authorization_request_failed,

    authorization_request_received,
    authorization_request_failed,
    authorization_code_issued,

    requested_token,
    tx_code_validation_failed,
    token_request_failed,
    access_token_refreshed,

    credential_request_received,
    dpop_proof_validation_failed,
    credential_request_proof_validation_failed,
    credential_request_failed,

    sdjwt_issue,
    jwt_issue,
    generated_mdoc,

    issuance_status,
}
