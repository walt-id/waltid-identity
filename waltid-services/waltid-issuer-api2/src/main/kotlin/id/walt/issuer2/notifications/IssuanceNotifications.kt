package id.walt.issuer2.notifications

import id.walt.ktornotifications.core.KtorSessionNotifications.WebhookRetryPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IssuanceNotifications(
    val webhook: WebhookNotification? = null,
) {
    @Serializable
    data class WebhookNotification(
        val url: String,

        @SerialName("retry_policy")
        val retryPolicy: WebhookRetryPolicy = WebhookRetryPolicy(),
    ) {
        init {
            require(url.isNotBlank()) { "webhook url must not be blank" }
        }
    }
}
