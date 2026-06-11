package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

class IssuanceNotificationService {
    private val logger = KotlinLogging.logger {}

    suspend fun notify(
        session: IssuanceSession,
        event: IssuanceSessionEvent,
    ) {
        val update = session.toSessionUpdate(event)
        runCatching {
            update.notifySessionUpdate(
                sessionId = session.sessionId,
                sessionNotifications = session.notifications.toKtorSessionNotifications(),
            )
        }.getOrElse { ex ->
            if (ex is CancellationException) throw ex
            logger.warn(ex) { "Failed to send issuance notification for session ${session.sessionId} (event=$event)" }
        }
    }

    suspend fun emitIssuanceStatus(session: IssuanceSession) =
        notify(
            session = session,
            event = IssuanceSessionEvent.issuance_status,
        )

    private fun IssuanceSession.toSessionUpdate(event: IssuanceSessionEvent) =
        KtorSessionUpdate(
            target = sessionId,
            event = event.toString(),
            session = Json.encodeToJsonElement(this).jsonObject,
        )

    private fun IssuanceNotifications?.toKtorSessionNotifications(): KtorSessionNotifications? =
        this?.webhook?.let { webhook ->
            KtorSessionNotifications(
                webhook = KtorSessionNotifications.VerificationSessionWebhookNotification(
                    url = Url(webhook.url),
                ),
            )
        }
}