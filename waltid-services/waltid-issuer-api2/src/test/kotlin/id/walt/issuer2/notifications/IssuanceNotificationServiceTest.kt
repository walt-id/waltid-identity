package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.openid4vci.offers.AuthenticationMethod
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals

class IssuanceNotificationServiceTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun webhookUsesConfiguredUrlAndKeepsRequestedTokenPayload() = runTest {
        var requestedUrl: String? = null
        var requestBody: String? = null
        val service = IssuanceNotificationService(
            httpClient = mockWebhookClient { request ->
                requestedUrl = request.url.toString()
                requestBody = request.body.asText()
                jsonResponse()
            },
        )
        val session = testSession(webhookUrl = "https://callbacks.example/issuer2")

        service.notify(
            session = session,
            event = IssuanceSessionEvent.requested_token,
            data = buildJsonObject {
                put("request", json.encodeToJsonElement(session))
            },
        )

        assertEquals("https://callbacks.example/issuer2", requestedUrl)
        val payload = json.parseToJsonElement(requireNotNull(requestBody)).let { it as JsonObject }
        assertEquals("session-123", payload["id"]?.jsonPrimitive?.content)
        assertEquals("requested_token", payload["type"]?.jsonPrimitive?.content)
        val request = payload["data"]?.jsonObject?.get("request")?.jsonObject
        assertEquals("session-123", request?.get("sessionId")?.jsonPrimitive?.contentOrNull)
        assertEquals("identity_credential", request?.get("credentialConfigurationId")?.jsonPrimitive?.contentOrNull)
        assertEquals("PRE_AUTHORIZED", request?.get("authenticationMethod")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun nonSuccessfulWebhookResponseDoesNotFailNotification() = runTest {
        var webhookCalls = 0
        val service = IssuanceNotificationService(
            httpClient = mockWebhookClient {
                webhookCalls++
                jsonResponse(HttpStatusCode.InternalServerError)
            },
        )

        service.notify(
            session = testSession(webhookUrl = "https://callbacks.example/issuer2"),
            event = IssuanceSessionEvent.issuance_status,
            data = buildJsonObject {
                put("status", "UNSUCCESSFUL")
            },
        )

        assertEquals(1, webhookCalls)
    }

    @Test
    fun webhookExceptionDoesNotFailNotification() = runTest {
        var webhookCalls = 0
        val service = IssuanceNotificationService(
            httpClient = mockWebhookClient {
                webhookCalls++
                throw IOException("callback unavailable")
            },
        )

        service.notify(
            session = testSession(webhookUrl = "https://callbacks.example/issuer2"),
            event = IssuanceSessionEvent.issuance_status,
            data = buildJsonObject {
                put("status", "UNSUCCESSFUL")
            },
        )

        assertEquals(1, webhookCalls)
    }

    private fun testSession(webhookUrl: String?): IssuanceSession =
        IssuanceSession(
            sessionId = "session-123",
            profileId = "identity-profile",
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialConfigurationId = "identity_credential",
            issuerKey = buildJsonObject { put("type", "jwk") },
            credentialData = buildJsonObject { put("given_name", "Jane") },
            expiresAt = kotlin.time.Instant.DISTANT_FUTURE,
            notifications = webhookUrl?.let {
                IssuanceNotifications(
                    webhook = IssuanceNotifications.WebhookNotification(url = it),
                )
            },
        )

    private fun mockWebhookClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request -> handler(request) }
            }
            install(ContentNegotiation) {
                json(json)
            }
        }

    private fun MockRequestHandleScope.jsonResponse(status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(
            content = "{}",
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private suspend fun Any.asText(): String =
        when (this) {
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readText()
            else -> toString()
        }
}
