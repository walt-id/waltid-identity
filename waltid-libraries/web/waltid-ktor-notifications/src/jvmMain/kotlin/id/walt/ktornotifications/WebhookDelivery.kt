package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications.WebhookRetryPolicy
import kotlinx.serialization.Serializable

/** Metadata exposed to operators for a delivery that exhausted its retry policy. */
@Serializable
data class WebhookDeadLetter(
    val deliveryId: String,
    val url: String,
    val target: String,
    val event: String,
    val requestId: String?,
    val attemptCount: Int,
    val createdAtEpochMilliseconds: Long,
    val failedAtEpochMilliseconds: Long,
    val lastError: String,
)

@Serializable
internal data class WebhookDelivery(
    val deliveryId: String,
    val url: String,
    val payload: String,
    val contentDigest: String,
    val target: String,
    val event: String,
    val requestId: String?,
    val basicAuthUser: String?,
    val basicAuthPass: String?,
    val bearerToken: String?,
    val retryPolicy: WebhookRetryPolicy,
    val enqueueOrder: Long,
    val attemptCount: Int = 0,
    val createdAtEpochMilliseconds: Long,
    val nextAttemptAtEpochMilliseconds: Long,
    val failedAtEpochMilliseconds: Long? = null,
    val lastError: String? = null,
) {
    fun deadLetter(): WebhookDeadLetter = WebhookDeadLetter(
        deliveryId = deliveryId,
        url = url,
        target = target,
        event = event,
        requestId = requestId,
        attemptCount = attemptCount,
        createdAtEpochMilliseconds = createdAtEpochMilliseconds,
        failedAtEpochMilliseconds = requireNotNull(failedAtEpochMilliseconds),
        lastError = requireNotNull(lastError),
    )
}

internal data class WebhookAttemptResult(
    val statusCode: Int? = null,
    val retryAfterMilliseconds: Long? = null,
    val error: Throwable? = null,
) {
    val successful: Boolean get() = statusCode in 200..299

    val retryable: Boolean
        get() = error != null || statusCode == 408 || statusCode == 429 || statusCode in 500..599

    fun failureMessage(): String = when {
        error != null -> "${error::class.simpleName}: ${error.message ?: "request failed"}"
        statusCode != null -> "Webhook endpoint returned HTTP $statusCode"
        else -> "Webhook delivery failed"
    }
}
