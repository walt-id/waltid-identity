package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class IssuanceNotificationService(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    },
) {
    private val logger = KotlinLogging.logger {}
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<IssuanceSessionUpdate>>()

    fun subscribe(sessionId: String): SharedFlow<IssuanceSessionUpdate> =
        flowFor(sessionId).asSharedFlow()

    suspend fun notify(
        session: IssuanceSession,
        event: IssuanceSessionEvent,
        data: JsonObject,
    ) {
        val update = IssuanceSessionUpdate(
            id = session.sessionId,
            type = event,
            data = data,
        )
        if (!flowFor(session.sessionId).tryEmit(update)) {
            logger.warn { "Dropped issuance event for session ${session.sessionId} (type=$event)" }
        }
        session.notifications
            ?.webhook
            ?.url
            ?.let { webhookUrl -> sendWebhook(update, webhookUrl) }
    }

    suspend fun emitIssuanceStatus(session: IssuanceSession) =
        notify(
            session = session,
            event = IssuanceSessionEvent.issuance_status,
            data = buildJsonObject {
                put("sessionId", JsonPrimitive(session.sessionId))
                put("status", JsonPrimitive(session.status.name))
                session.statusReason?.let { put("reason", JsonPrimitive(it)) }
                put("closed", JsonPrimitive(session.isClosed))
                put("credentialConfigurationId", JsonPrimitive(session.credentialConfigurationId))
            },
        )

    private fun flowFor(sessionId: String): MutableSharedFlow<IssuanceSessionUpdate> =
        flows.computeIfAbsent(sessionId) {
            MutableSharedFlow(
                replay = 10,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

    private suspend fun sendWebhook(update: IssuanceSessionUpdate, webhookUrl: String) {
        runCatching {
            val response = httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(update)
            }
            if (response.status.isSuccess()) {
                logger.trace { "Sent issuance callback: $webhookUrl, ${update.type}, ${update.id}; response: ${response.status}" }
            } else {
                logger.warn { "Issuance callback returned ${response.status}: $webhookUrl, ${update.type}, ${update.id}" }
            }
        }.getOrElse { ex ->
            if (ex is CancellationException) throw ex
            logger.warn(ex) { "Failed to send issuance callback to $webhookUrl for session ${update.id} (type=${update.type})" }
        }
    }

}
