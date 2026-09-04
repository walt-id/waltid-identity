package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class IssuanceNotificationServiceTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun notificationUsesCommonSessionUpdateEnvelope() = runTest {
        val service = IssuanceNotificationService()
        val session = testSession()
        val updates = SseNotifier.getSseFlow(session.sessionId)

        val received = async {
            withTimeout(1_000.milliseconds) {
                updates.first()
            }
        }

        service.notify(session, IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED)

        val payload = received.await()
        assertEquals("session-123", payload.target)
        assertEquals("session-123", payload.requestId)
        assertEquals("token_request_pre_authorized_code_succeeded", payload.event)
        assertEquals("session-123", payload.session["sessionId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("identity_credential", payload.session["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("PRE_AUTHORIZED", payload.session["authenticationMethod"]?.jsonPrimitive?.contentOrNull)
        assertEquals("redacted", payload.session["issuerKey"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun uncorrelatedFailureUsesIssuerStreamAndDirectErrorFields() = runTest {
        val service = IssuanceNotificationService()
        val requestId = "par-request-123"
        val updates = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val received = async {
            withTimeout(1_000.milliseconds) {
                updates.filter { it.requestId == requestId }.first()
            }
        }

        service.notify(
            requestId = requestId,
            session = null,
            event = IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED,
            error = "invalid_request",
            errorDescription = "Missing response_type",
        )

        val payload = received.await()
        assertEquals(requestId, payload.target)
        assertEquals(requestId, payload.requestId)
        assertEquals("pushed_authorization_request_failed", payload.event)
        assertEquals("invalid_request", payload.error)
        assertEquals("Missing response_type", payload.errorDescription)
        assertEquals(true, payload.session.isEmpty())
    }

    @Test
    fun failedWebhookDoesNotFailNotification() = runTest {
        val service = IssuanceNotificationService()

        service.notify(
            session = testSession(webhookUrl = "http://127.0.0.1:9/issuer2"),
            event = IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED,
        )
    }

    @Test
    fun webhookRoutePayloadCanBeDecodedAsCommonSessionUpdate() {
        val payload = KtorSessionUpdate(
            target = "session-123",
            event = IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED.value,
            session = buildJsonObject {
                put("sessionId", "session-123")
                put("credentialConfigurationId", "identity_credential")
            },
            requestId = "request-123",
            error = "invalid_request",
            errorDescription = "Invalid request",
        )

        val encoded = json.encodeToString(KtorSessionUpdate.serializer(), payload)
        val decoded = json.decodeFromString(KtorSessionUpdate.serializer(), encoded)
        assertEquals(payload, decoded)
        assertEquals("invalid_request", decoded.error)
        assertEquals("Invalid request", decoded.errorDescription)
        assertEquals(true, encoded.contains("\"error_description\":\"Invalid request\""))
    }

    private fun testSession(webhookUrl: String? = null): IssuanceSession =
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
}
