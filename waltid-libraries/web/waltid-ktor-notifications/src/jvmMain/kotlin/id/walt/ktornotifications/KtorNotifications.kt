package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.ktornotifications.core.KtorSessionUpdate

object KtorNotifications {

    suspend fun KtorSessionUpdate.notifySessionUpdate(sessionId: String, sessionNotifications: KtorSessionNotifications?) {
        val update = this

        SseNotifier.notify(sessionId, update)

        if (sessionNotifications != null && sessionNotifications.webhook != null) {
            WebhookNotifier.notify(update = update, config = sessionNotifications.webhook!!)
        }
    }

}
