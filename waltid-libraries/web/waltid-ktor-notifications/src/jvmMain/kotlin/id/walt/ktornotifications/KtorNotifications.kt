package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger { }

object KtorNotifications {

    suspend fun KtorSessionUpdate.notifySessionUpdate(sessionId: String, sessionNotifications: KtorSessionNotifications?) {
        val update = this

        SseNotifier.notify(sessionId, update)

        if (sessionNotifications != null && sessionNotifications.webhook != null) {
            log.debug { "Webhook configured for session $sessionId, sending notification for event: ${update.event}" }
            WebhookNotifier.notify(update = update, config = sessionNotifications.webhook!!)
        } else {
            log.trace { "No webhook configured for session $sessionId (notifications: ${sessionNotifications != null}, webhook: ${sessionNotifications?.webhook != null})" }
        }
    }

}
