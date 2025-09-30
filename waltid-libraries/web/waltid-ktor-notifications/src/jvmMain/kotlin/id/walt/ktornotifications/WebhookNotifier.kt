package id.walt.ktornotifications

import id.walt.ktornotifications.KtorSessionNotifications.VerificationSessionWebhookNotification
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import ktornotifications.KtorSessionUpdate

object WebhookNotifier {

    private val webhookClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun notify(update: KtorSessionUpdate, config: VerificationSessionWebhookNotification) {
        webhookClient.post(config.url) {
           setBody(update)

            if (config.basicAuthUser != null && config.basicAuthPass != null) {
                basicAuth(config.basicAuthUser!!, config.basicAuthPass!!)
            }

            if (config.bearerToken != null) {
                bearerAuth(config.bearerToken!!)
            }
        }
    }

}
