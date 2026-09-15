package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

class IssuanceNotificationService {
    private val logger = KotlinLogging.logger {}

    suspend fun notify(
        session: IssuanceSession,
        event: IssuanceSessionEvent,
    ) = notify(
        requestId = session.sessionId,
        session = session,
        event = event,
        error = session.failure?.error,
        errorDescription = session.failure?.errorDescription,
    )

    suspend fun notify(
        requestId: String,
        session: IssuanceSession?,
        event: IssuanceSessionEvent,
        error: String? = null,
        errorDescription: String? = null,
    ) {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        val update = KtorSessionUpdate(
            target = session?.sessionId ?: requestId,
            event = event.value,
            session = session?.toNotificationJson() ?: buildJsonObject {},
            requestId = requestId,
            error = error,
            errorDescription = errorDescription,
        )
        runCatching {
            SseNotifier.notify(ISSUER_EVENT_STREAM_TARGET, update)
            session?.let {
                update.notifySessionUpdate(
                    sessionId = it.sessionId,
                    sessionNotifications = it.notifications.toKtorSessionNotifications(),
                )
            }
        }.getOrElse { ex ->
            if (ex is CancellationException) throw ex
            logger.warn(ex) {
                "Failed to send issuance notification (requestId=$requestId, sessionId=${session?.sessionId}, event=$event)"
            }
        }
    }

    suspend fun emitIssuanceStatus(
        requestId: String,
        session: IssuanceSession,
    ) =
        notify(
            requestId = requestId,
            session = session,
            event = IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED,
            error = session.failure?.error,
            errorDescription = session.failure?.errorDescription,
        )

    private fun IssuanceSession.toNotificationJson(): JsonObject =
        Json.encodeToJsonElement(forNotificationPayload()).jsonObject

    private fun IssuanceSession.forNotificationPayload(): IssuanceSession =
        copy(
            issuanceRequests = issuanceRequests.map { it.copy(issuerKey = REDACTED_ISSUER_KEY) },
            failure = null,
        )

    private fun IssuanceNotifications?.toKtorSessionNotifications(): KtorSessionNotifications? =
        this?.webhook?.let { webhook ->
            KtorSessionNotifications(
                webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
                    url = Url(webhook.url),
                ),
            )
        }

    companion object {
        const val ISSUER_EVENT_STREAM_TARGET = "issuer2"

        private val REDACTED_ISSUER_KEY: JsonObject = buildJsonObject {
            put("type", JsonPrimitive("redacted"))
        }
    }
}
