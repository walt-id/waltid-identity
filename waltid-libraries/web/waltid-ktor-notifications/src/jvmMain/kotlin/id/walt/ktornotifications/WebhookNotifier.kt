package id.walt.ktornotifications

import id.walt.ktornotifications.core.KtorSessionNotifications.VerificationSessionWebhookNotification
import id.walt.ktornotifications.core.KtorSessionUpdate
import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

object WebhookNotifier {

    private val webhookClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun notify(update: KtorSessionUpdate, config: VerificationSessionWebhookNotification) {
        runCatching {
            webhookClient.post(config.url) {
                contentType(ContentType.Application.Json)
                setBody(update)

                if (config.basicAuthUser != null && config.basicAuthPass != null) {
                    basicAuth(config.basicAuthUser!!, config.basicAuthPass!!)
                }

                if (config.bearerToken != null) {
                    bearerAuth(config.bearerToken!!)
                }
            }
        }.getOrElse { ex ->
            if (ex is ConnectTimeoutException) {
                throw IllegalArgumentException("Could not connect to configured webhook URL for session update: ${config.url.hostWithPortIfSpecified}", ex)
            } else {
                throw IllegalArgumentException("Could not notify configured webhook for session update: ${ex.message}", ex)
            }
        }
    }

}
