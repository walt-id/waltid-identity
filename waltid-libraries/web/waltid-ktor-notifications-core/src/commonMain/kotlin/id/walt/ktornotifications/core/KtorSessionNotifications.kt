package id.walt.ktornotifications.core

import kotlinx.serialization.Serializable

@Serializable
data class KtorSessionNotifications(
    val webhook: VerificationSessionWebhookNotification? = null
) {
    @Serializable
    data class VerificationSessionWebhookNotification(
        val url: String,
        val basicAuthUser: String? = null,
        val basicAuthPass: String? = null,
        val bearerToken: String? = null
    )
}
