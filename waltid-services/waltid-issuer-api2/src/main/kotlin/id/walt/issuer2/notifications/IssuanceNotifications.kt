package id.walt.issuer2.notifications

import kotlinx.serialization.Serializable

@Serializable
data class IssuanceNotifications(
    val webhook: WebhookNotification? = null,
) {
    @Serializable
    data class WebhookNotification(
        val url: String,
    ) {
        init {
            require(url.isNotBlank()) { "webhook url must not be blank" }
        }
    }
}
