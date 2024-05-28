package id.walt.webwallet.usecase.notification

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.NotificationConfig
import id.walt.webwallet.db.models.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class NotificationDispatchUseCase(
    private val http: HttpClient,
    private val formatter: NotificationDataFormatter,
) {
    private val config by lazy { ConfigManager.getConfig<NotificationConfig>() }
    private val logger = KotlinLogging.logger {}

    suspend fun send(vararg notification: Notification) = notification.forEach {
        http.post(config.url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            config.apiKey?.let { bearerAuth(it) }
            setBody(formatter.format(it))
        }.also {
            logger.debug { "notification sent: ${it.status}" }
        }
    }
}