package id.walt.openid4vci.requests.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationEvent(val wireValue: String) {
    /** The wallet stored the issued credentials. */
    @SerialName("credential_accepted")
    CREDENTIAL_ACCEPTED("credential_accepted"),

    /** The wallet failed to process or store the issued credentials. */
    @SerialName("credential_failure")
    CREDENTIAL_FAILURE("credential_failure"),

    /** The user rejected the issued credentials, or the wallet deleted them during issuance. */
    @SerialName("credential_deleted")
    CREDENTIAL_DELETED("credential_deleted"),
    ;

    companion object {
        fun fromWireValue(value: String): NotificationEvent? =
            entries.firstOrNull { it.wireValue == value }
    }
}
