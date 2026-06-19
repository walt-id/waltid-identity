package id.walt.issuer2.notifications

import id.walt.issuer2.domain.IssuanceSession
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

        service.notify(session, IssuanceSessionEvent.requested_token)

        val payload = received.await()
        assertEquals("session-123", payload.target)
        assertEquals("requested_token", payload.event)
        assertEquals("session-123", payload.session["sessionId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("identity_credential", payload.session["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("PRE_AUTHORIZED", payload.session["authenticationMethod"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun failedWebhookDoesNotFailNotification() = runTest {
        val service = IssuanceNotificationService()

        service.notify(
            session = testSession(webhookUrl = "http://127.0.0.1:9/issuer2"),
            event = IssuanceSessionEvent.issuance_status,
        )
    }

    @Test
    fun webhookRoutePayloadCanBeDecodedAsCommonSessionUpdate() {
        val payload = KtorSessionUpdate(
            target = "session-123",
            event = IssuanceSessionEvent.jwt_issue.toString(),
            session = buildJsonObject {
                put("sessionId", "session-123")
                put("credentialConfigurationId", "identity_credential")
            },
        )

        val encoded = json.encodeToString(KtorSessionUpdate.serializer(), payload)
        assertEquals(payload, json.decodeFromString(KtorSessionUpdate.serializer(), encoded))
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