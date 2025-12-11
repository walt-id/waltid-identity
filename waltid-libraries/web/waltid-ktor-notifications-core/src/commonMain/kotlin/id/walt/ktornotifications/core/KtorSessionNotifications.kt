package id.walt.ktornotifications.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KtorSessionNotifications(
    val webhook: VerificationSessionWebhookNotification? = null
) {
    @Serializable
    data class VerificationSessionWebhookNotification(
        val url: String,

        @SerialName("basic_auth_username")
        val basicAuthUser: String? = null,

        @SerialName("basic_auth_password")
        val basicAuthPass: String? = null,

        @SerialName("bearer_token")
        val bearerToken: String? = null
    )
}
