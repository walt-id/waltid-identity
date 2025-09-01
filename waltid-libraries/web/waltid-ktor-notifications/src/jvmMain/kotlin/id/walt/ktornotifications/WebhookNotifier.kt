package id.walt.ktornotifications

import id.walt.ktornotifications.KtorSessionNotifications.VerificationSessionWebhookNotification
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
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
