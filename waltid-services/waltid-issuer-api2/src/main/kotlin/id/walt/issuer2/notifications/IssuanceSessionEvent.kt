package id.walt.issuer2.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Issuance lifecycle events. Kotlin entries are SCREAMING_SNAKE; [value] is the lowercase wire name
 * published on webhooks and SSE.
 */
@Serializable
enum class IssuanceSessionEvent(val value: String) {
    @SerialName("credential_offer_created")
    CREDENTIAL_OFFER_CREATED("credential_offer_created"),

    @SerialName("credential_offer_resolved")
    CREDENTIAL_OFFER_RESOLVED("credential_offer_resolved"),

    @SerialName("pushed_authorization_request_received")
    PUSHED_AUTHORIZATION_REQUEST_RECEIVED("pushed_authorization_request_received"),

    @SerialName("pushed_authorization_request_failed")
    PUSHED_AUTHORIZATION_REQUEST_FAILED("pushed_authorization_request_failed"),

    @SerialName("authorization_request_received")
    AUTHORIZATION_REQUEST_RECEIVED("authorization_request_received"),

    @SerialName("authorization_request_failed")
    AUTHORIZATION_REQUEST_FAILED("authorization_request_failed"),

    @SerialName("authorization_code_issued")
    AUTHORIZATION_CODE_ISSUED("authorization_code_issued"),

    @SerialName("access_token_issued")
    ACCESS_TOKEN_ISSUED("access_token_issued"),

    @SerialName("tx_code_validation_failed")
    TX_CODE_VALIDATION_FAILED("tx_code_validation_failed"),

    @SerialName("token_request_failed")
    TOKEN_REQUEST_FAILED("token_request_failed"),

    @SerialName("access_token_refreshed")
    ACCESS_TOKEN_REFRESHED("access_token_refreshed"),

    @SerialName("credential_request_received")
    CREDENTIAL_REQUEST_RECEIVED("credential_request_received"),

    @SerialName("dpop_proof_validation_failed")
    DPOP_PROOF_VALIDATION_FAILED("dpop_proof_validation_failed"),

    @SerialName("credential_proof_validation_failed")
    CREDENTIAL_PROOF_VALIDATION_FAILED("credential_proof_validation_failed"),

    @SerialName("credential_request_failed")
    CREDENTIAL_REQUEST_FAILED("credential_request_failed"),

    @SerialName("sd_jwt_issued")
    SD_JWT_ISSUED("sd_jwt_issued"),

    @SerialName("jwt_issued")
    JWT_ISSUED("jwt_issued"),

    @SerialName("mdoc_issued")
    MDOC_ISSUED("mdoc_issued"),

    @SerialName("issuance_status")
    ISSUANCE_STATUS("issuance_status"),
}
