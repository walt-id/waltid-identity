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

    @SerialName("credential_offer_retrieved")
    CREDENTIAL_OFFER_RETRIEVED("credential_offer_retrieved"),

    @SerialName("pushed_authorization_request_succeeded")
    PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED("pushed_authorization_request_succeeded"),

    @SerialName("pushed_authorization_request_failed")
    PUSHED_AUTHORIZATION_REQUEST_FAILED("pushed_authorization_request_failed"),

    @SerialName("authorization_request_succeeded")
    AUTHORIZATION_REQUEST_SUCCEEDED("authorization_request_succeeded"),

    @SerialName("authorization_request_failed")
    AUTHORIZATION_REQUEST_FAILED("authorization_request_failed"),

    @SerialName("token_request_failed")
    TOKEN_REQUEST_FAILED("token_request_failed"),

    @SerialName("token_request_authorization_code_succeeded")
    TOKEN_REQUEST_AUTHORIZATION_CODE_SUCCEEDED("token_request_authorization_code_succeeded"),

    @SerialName("token_request_authorization_code_failed")
    TOKEN_REQUEST_AUTHORIZATION_CODE_FAILED("token_request_authorization_code_failed"),

    @SerialName("token_request_pre_authorized_code_succeeded")
    TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED("token_request_pre_authorized_code_succeeded"),

    @SerialName("token_request_pre_authorized_code_failed")
    TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED("token_request_pre_authorized_code_failed"),

    @SerialName("token_request_refresh_token_succeeded")
    TOKEN_REQUEST_REFRESH_TOKEN_SUCCEEDED("token_request_refresh_token_succeeded"),

    @SerialName("token_request_refresh_token_failed")
    TOKEN_REQUEST_REFRESH_TOKEN_FAILED("token_request_refresh_token_failed"),

    @SerialName("nonce_request_succeeded")
    NONCE_REQUEST_SUCCEEDED("nonce_request_succeeded"),

    @SerialName("nonce_request_failed")
    NONCE_REQUEST_FAILED("nonce_request_failed"),

    @SerialName("credential_request_failed")
    CREDENTIAL_REQUEST_FAILED("credential_request_failed"),

    @SerialName("credential_request_sd_jwt_vc_succeeded")
    CREDENTIAL_REQUEST_SD_JWT_VC_SUCCEEDED("credential_request_sd_jwt_vc_succeeded"),

    @SerialName("credential_request_sd_jwt_vc_failed")
    CREDENTIAL_REQUEST_SD_JWT_VC_FAILED("credential_request_sd_jwt_vc_failed"),

    @SerialName("credential_request_w3c_vc_succeeded")
    CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED("credential_request_w3c_vc_succeeded"),

    @SerialName("credential_request_w3c_vc_failed")
    CREDENTIAL_REQUEST_W3C_VC_FAILED("credential_request_w3c_vc_failed"),

    @SerialName("credential_request_mso_mdoc_succeeded")
    CREDENTIAL_REQUEST_MSO_MDOC_SUCCEEDED("credential_request_mso_mdoc_succeeded"),

    @SerialName("credential_request_mso_mdoc_failed")
    CREDENTIAL_REQUEST_MSO_MDOC_FAILED("credential_request_mso_mdoc_failed"),

    @SerialName("issuance_status_changed")
    ISSUANCE_STATUS_CHANGED("issuance_status_changed"),
}
