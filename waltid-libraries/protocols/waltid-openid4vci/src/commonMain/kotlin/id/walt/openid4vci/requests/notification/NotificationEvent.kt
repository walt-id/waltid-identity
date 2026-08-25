package id.walt.openid4vci.requests.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationEvent {
    @SerialName("credential_accepted")
    CREDENTIAL_ACCEPTED,

    @SerialName("credential_failure")
    CREDENTIAL_FAILURE,

    @SerialName("credential_deleted")
    CREDENTIAL_DELETED,
}