package id.walt.ktornotifications.core

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KtorSessionNotifications(
    val webhook: VerificationSessionWebhookNotification? = null
) {
    /** Remove authentication material before exposing notifications in sessions or callbacks. */
    fun withoutCredentials(): KtorSessionNotifications = copy(
        webhook = webhook?.copy(
            basicAuthUser = null,
            basicAuthPass = null,
            bearerToken = null,
            signingSecret = null,
        ),
    )

    @Serializable
    data class WebhookRetryPolicy(
        @SerialName("max_attempts")
        val maxAttempts: Int = 5,

        @SerialName("initial_backoff_seconds")
        val initialBackoffSeconds: Long = 1,

        @SerialName("max_backoff_seconds")
        val maxBackoffSeconds: Long = 60,

        @SerialName("backoff_multiplier")
        val backoffMultiplier: Double = 2.0,
    ) {
        init {
            require(maxAttempts > 0) { "max_attempts must be greater than zero" }
            require(initialBackoffSeconds >= 0) { "initial_backoff_seconds must not be negative" }
            require(maxBackoffSeconds >= initialBackoffSeconds) {
                "max_backoff_seconds must be greater than or equal to initial_backoff_seconds"
            }
            require(backoffMultiplier >= 1.0) { "backoff_multiplier must be at least 1.0" }
        }
    }

    @Serializable
    data class VerificationSessionWebhookNotification(
        val url: Url,

        @SerialName("basic_auth_username")
        val basicAuthUser: String? = null,

        @SerialName("basic_auth_password")
        val basicAuthPass: String? = null,

        @SerialName("bearer_token")
        val bearerToken: String? = null,

        /** Shared secret used to authenticate each outbound attempt with HMAC-SHA256. */
        @SerialName("signing_secret")
        val signingSecret: String? = null,

        @SerialName("retry_policy")
        val retryPolicy: WebhookRetryPolicy = WebhookRetryPolicy(),
    ) {
        init {
            require(signingSecret == null || signingSecret.encodeToByteArray().size >= 32) {
                "signing_secret must contain at least 32 UTF-8 bytes"
            }
        }
    }
}
