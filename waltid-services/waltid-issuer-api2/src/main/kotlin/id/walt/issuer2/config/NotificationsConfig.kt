package id.walt.issuer2.config

import id.walt.ktornotifications.core.KtorSessionNotifications
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookNotificationConfig(
    val url: String,

    @SerialName("basic_auth_username")
    val basicAuthUser: String? = null,

    @SerialName("basic_auth_password")
    val basicAuthPass: String? = null,

    @SerialName("bearer_token")
    val bearerToken: String? = null
) {
    fun toLibraryType() = KtorSessionNotifications.VerificationSessionWebhookNotification(
        url = Url(url),
        basicAuthUser = basicAuthUser,
        basicAuthPass = basicAuthPass,
        bearerToken = bearerToken
    )
}

@Serializable
data class NotificationsConfig(
    val webhook: WebhookNotificationConfig? = null
) {
    fun toLibraryType() = KtorSessionNotifications(
        webhook = webhook?.toLibraryType()
    )
}
