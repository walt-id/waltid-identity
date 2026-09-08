package id.walt.issuer2.openid4vci

import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.requests.notification.NotificationEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2NotificationEndpointTest {
    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun notificationEndpointAcceptsAndStoresIdempotentWalletEvent() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()
        val request = buildJsonObject {
            put("notification_id", issuance.notificationId)
            put("event", "credential_accepted")
            put("event_description", "Credential stored")
            put("future_parameter", "ignored")
        }

        repeat(2) {
            val response = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
            assertTrue(response.bodyAsText().isEmpty())
        }

        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertEquals(NotificationEvent.CREDENTIAL_ACCEPTED, session.walletNotificationEvent)
        assertEquals("Credential stored", session.walletNotificationEventDescription)
    }

    @Test
    fun notificationEndpointRejectsUnknownIdWithoutChangingIssuanceStatus() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val response = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody("unknown-notification-id", "credential_failure"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        val error = response.body<JsonObject>()
        assertEquals("invalid_notification_id", error["error"]?.jsonPrimitive?.content)
        assertEquals(setOf("error"), error.keys)
        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertNull(session.walletNotificationEvent)
        assertNull(session.walletNotificationEventDescription)
    }

    @Test
    fun walletFailureEventDoesNotChangeSuccessfulIssuerStatus() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()
        val request = buildJsonObject {
            put("notification_id", issuance.notificationId)
            put("event", "credential_failure")
            put("event_description", "Credential storage failed")
        }

        val response = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertEquals(NotificationEvent.CREDENTIAL_FAILURE, session.walletNotificationEvent)
        assertEquals("Credential storage failed", session.walletNotificationEventDescription)
    }

    @Test
    fun notificationIdIsBoundToAccessTokenSession() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val first = client.issueCredential()
        val second = client.issueCredential()

        val response = client.post(second.notificationEndpoint) {
            bearerAuth(first.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(second.notificationId, "credential_accepted"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertEquals(
            "invalid_notification_id",
            response.body<JsonObject>()["error"]?.jsonPrimitive?.content,
        )
        assertNull(client.getSession(first.sessionId).walletNotificationEvent)
        assertNull(client.getSession(second.sessionId).walletNotificationEvent)
    }

    @Test
    fun notificationEndpointRejectsMalformedRequestAndMissingToken() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val malformed = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(issuance.notificationId, "unsupported_event"))
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status, malformed.bodyAsText())
        assertEquals(
            "invalid_notification_request",
            malformed.body<JsonObject>()["error"]?.jsonPrimitive?.content,
        )

        val unauthorized = client.post(issuance.notificationEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(notificationBody(issuance.notificationId, "credential_accepted"))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status, unauthorized.bodyAsText())
        assertEquals("invalid_token", unauthorized.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        assertNull(client.getSession(issuance.sessionId).walletNotificationEvent)
    }

    @Test
    fun notificationEndpointAuthenticatesBeforeDecodingMalformedJson() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        listOf(null, "Basic invalid-token").forEach { authorization ->
            val response = client.post(issuance.notificationEndpoint) {
                authorization?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("{")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
            assertEquals("invalid_token", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        }
        assertNull(client.getSession(issuance.sessionId).walletNotificationEvent)
    }

    @Test
    fun disabledNotificationEndpointIsNotAdvertisedOrIncludedInCredentialResponse() = testApplication {
        installIssuer2WithConfigFiles(
            configureServiceConfig = { it.copy(walletNotificationEndpointEnabled = false) }
        )
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.openBadgeCredential,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        assertNull(resolvedOffer.issuerMetadata.notificationEndpoint)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val credentialResponse = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
        )

        assertFalse("notification_id" in credentialResponse)
        val routeResponse = client.post("/openid4vci/notification") {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(notificationBody("unused", "credential_accepted"))
        }
        assertEquals(HttpStatusCode.NotFound, routeResponse.status)
    }

    private suspend fun HttpClient.issueCredential(): IssuedNotificationContext {
        val walletFlow = Issuer2WalletFlowDriver(this)
        val createdOffer = createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.openBadgeCredential,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val notificationEndpoint = assertNotNull(resolvedOffer.issuerMetadata.notificationEndpoint)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val response = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
        )
        val notificationId = assertNotNull(
            response["notification_id"]?.jsonPrimitive?.content
        )

        return IssuedNotificationContext(
            sessionId = createdOffer.offerId,
            accessToken = tokenResponse.access_token,
            notificationEndpoint = notificationEndpoint,
            notificationId = notificationId,
        )
    }

    private fun notificationBody(notificationId: String, event: String) = buildJsonObject {
        put("notification_id", notificationId)
        put("event", event)
    }

    private data class IssuedNotificationContext(
        val sessionId: String,
        val accessToken: String,
        val notificationEndpoint: String,
        val notificationId: String,
    )
}
