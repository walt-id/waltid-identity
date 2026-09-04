package id.waltid.openid4vci.wallet.notification

import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.requests.notification.DefaultNotificationRequest
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NotificationRequestBuilderTest {
    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    @Test
    fun `posts notification request with bearer access token`() = runTest {
        val client = client { request ->
            assertEquals("https://issuer.example/notification", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[DPOP_HEADER])
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals("notification-id", body["notification_id"]?.jsonPrimitive?.content)
            assertEquals("credential_accepted", body["event"]?.jsonPrimitive?.content)
            assertEquals("Stored", body["event_description"]?.jsonPrimitive?.content)

            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val notification = DefaultNotificationRequest(
            notificationId = "notification-id",
            event = NotificationEvent.CREDENTIAL_ACCEPTED,
            eventDescription = "Stored",
        )

        val result = NotificationRequestBuilder(client).send(
            notificationEndpoint = "https://issuer.example/notification",
            accessToken = "access-token",
            request = notification,
        )

        assertEquals(notification, assertIs<NotificationDeliveryResult.Success>(result).request)
    }

    @Test
    fun `posts notification request with dpop access token and fresh proof`() = runTest {
        val proofInputs = mutableListOf<Pair<String, String?>>()
        val client = client { request ->
            assertEquals("DPoP access-token", request.headers[HttpHeaders.Authorization])
            assertEquals("proof-1", request.headers[DPOP_HEADER])
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val notificationEndpoint = "https://issuer.example/notification"

        val result = NotificationRequestBuilder(client).send(
            notificationEndpoint = notificationEndpoint,
            accessToken = "access-token",
            request = DefaultNotificationRequest(
                notificationId = "notification-id",
                event = NotificationEvent.CREDENTIAL_ACCEPTED,
            ),
            dpopProofFactory = { endpoint, nonce ->
                proofInputs += endpoint to nonce
                "proof-${proofInputs.size}"
            },
        )

        assertIs<NotificationDeliveryResult.Success>(result)
        assertEquals(listOf<Pair<String, String?>>(notificationEndpoint to null), proofInputs)
    }

    @Test
    fun `retries dpop notification once with server nonce and fresh proof`() = runTest {
        var callCount = 0
        val proofInputs = mutableListOf<Pair<String, String?>>()
        val client = client { request ->
            callCount += 1
            assertEquals("DPoP access-token", request.headers[HttpHeaders.Authorization])
            assertEquals("proof-$callCount", request.headers[DPOP_HEADER])
            if (callCount == 1) {
                respond(
                    content = "{}",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                        HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"use_dpop_nonce\""),
                        DPOP_NONCE_HEADER to listOf("server-nonce"),
                    ),
                )
            } else {
                respond(content = "", status = HttpStatusCode.NoContent)
            }
        }
        val notificationEndpoint = "https://issuer.example/notification"

        val result = NotificationRequestBuilder(client).send(
            notificationEndpoint = notificationEndpoint,
            accessToken = "access-token",
            request = DefaultNotificationRequest(
                notificationId = "notification-id",
                event = NotificationEvent.CREDENTIAL_FAILURE,
            ),
            dpopProofFactory = { endpoint, nonce ->
                proofInputs += endpoint to nonce
                "proof-${proofInputs.size}"
            },
        )

        assertIs<NotificationDeliveryResult.Success>(result)
        assertEquals(2, callCount)
        assertEquals(
            listOf(notificationEndpoint to null, notificationEndpoint to "server-nonce"),
            proofInputs,
        )
    }

    @Test
    fun `parses invalid notification id response`() = runTest {
        val client = client {
            respond(
                content = """{"error":"invalid_notification_id"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = NotificationRequestBuilder(client).send(
            notificationEndpoint = "https://issuer.example/notification",
            accessToken = "access-token",
            request = DefaultNotificationRequest(
                notificationId = "unknown",
                event = NotificationEvent.CREDENTIAL_FAILURE,
            ),
        )

        val failure = assertIs<NotificationDeliveryResult.Failure>(result)
        assertEquals(HttpStatusCode.BadRequest.value, failure.statusCode)
        assertEquals(NotificationErrorCodes.INVALID_NOTIFICATION_ID, failure.error?.error)
    }

    private fun HttpRequestData.bodyText(): String =
        when (val requestBody = body) {
            is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> error("Streaming request body is not expected")
            is OutgoingContent.WriteChannelContent -> error("Streaming request body is not expected")
            is OutgoingContent.NoContent -> ""
            is io.ktor.http.content.TextContent -> requestBody.text
            is OutgoingContent.ProtocolUpgrade -> error("Protocol upgrade body is not expected")
            else -> error("Unsupported request body type: ${requestBody::class}")
        }
}
