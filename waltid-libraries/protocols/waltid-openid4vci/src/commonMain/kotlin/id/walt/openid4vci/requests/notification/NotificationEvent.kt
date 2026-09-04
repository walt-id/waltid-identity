package id.walt.openid4vci.requests.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationEvent {
    /** Credential issuance completed successfully. */
    @SerialName("credential_accepted")
    CREDENTIAL_ACCEPTED,

    /** Credential issuance failed for a reason other than user action. */
    @SerialName("credential_failure")
    CREDENTIAL_FAILURE,

    /** Credential issuance failed because of a user action. */
    @SerialName("credential_deleted")
    CREDENTIAL_DELETED,
}
