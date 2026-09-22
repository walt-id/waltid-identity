package id.walt.issuer2.openid4vci

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.testsupport.Issuer2TestNotificationServer
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.KTOR_TEST_APPLICATION_BASE_URL
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.getSession
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.openid4vci.dpop.DPoPConstants
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.waltid.openid4vci.wallet.dpop.DPoPProofBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
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
    fun changedWalletEventIsPublishedToTheOfferWebhookOnce() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()
        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val issuance = client.issueCredential(notificationServer.webhookUrl())

            val accepted = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                contentType(ContentType.Application.Json)
                setBody(notificationBody(issuance.notificationId, "credential_accepted", "Stored"))
            }
            assertEquals(HttpStatusCode.NoContent, accepted.status, accepted.bodyAsText())
            val repeated = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                contentType(ContentType.Application.Json)
                setBody(notificationBody(issuance.notificationId, "credential_accepted", "Stored"))
            }
            assertEquals(HttpStatusCode.NoContent, repeated.status, repeated.bodyAsText())
            notificationServer.awaitEvent(issuance.sessionId, IssuanceSessionEvent.WALLET_CREDENTIAL_ACCEPTED)
            assertEquals(
                1,
                notificationServer.getReceivedUpdates().count {
                    it.target == issuance.sessionId && it.event == IssuanceSessionEvent.WALLET_CREDENTIAL_ACCEPTED.value
                },
            )

            val failure = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                contentType(ContentType.Application.Json)
                setBody(notificationBody(issuance.notificationId, "credential_failure", "Could not store"))
            }
            assertEquals(HttpStatusCode.NoContent, failure.status, failure.bodyAsText())
            val failureEvent = notificationServer.awaitEvent(
                issuance.sessionId,
                IssuanceSessionEvent.WALLET_CREDENTIAL_FAILURE,
            )
            assertEquals("SUCCESSFUL", failureEvent.session["status"]?.jsonPrimitive?.content)
            assertEquals(
                "credential_failure",
                failureEvent.session["walletNotificationEvent"]?.jsonPrimitive?.content,
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun notificationEndpointAcceptsIdempotentEventAndALaterDistinctEvent() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()
        val accepted = notificationBody(issuance.notificationId, "credential_accepted", "Credential stored")

        repeat(2) {
            val response = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                contentType(ContentType.Application.Json)
                setBody(accepted)
            }
            assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
            assertTrue(response.bodyAsText().isEmpty())
        }

        val replaced = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(issuance.notificationId, "credential_failure", "Storage failed"))
        }
        assertEquals(HttpStatusCode.NoContent, replaced.status, replaced.bodyAsText())

        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertEquals(NotificationEvent.CREDENTIAL_FAILURE, session.walletNotificationEvent)
        assertEquals("Storage failed", session.walletNotificationEventDescription)
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
        assertEquals("invalid_notification_id", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertNull(session.walletNotificationEvent)
    }

    @Test
    fun credentialDeletedDoesNotChangeSuccessfulIssuerStatus() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val response = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(issuance.notificationId, "credential_deleted", "User rejected the credential"))
        }

        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
        val session = client.getSession(issuance.sessionId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertEquals(NotificationEvent.CREDENTIAL_DELETED, session.walletNotificationEvent)
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
        assertEquals("invalid_notification_id", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        assertNull(client.getSession(first.sessionId).walletNotificationEvent)
        assertNull(client.getSession(second.sessionId).walletNotificationEvent)
    }

    @Test
    fun invalidBearerIsRejectedBeforeMalformedBodyAndUsesBearerChallenge() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val response = client.post(issuance.notificationEndpoint) {
            header(HttpHeaders.Authorization, "Bearer not-a-jwt")
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
        assertEquals("invalid_token", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        val challenge = response.headers[HttpHeaders.WWWAuthenticate].orEmpty()
        assertTrue(challenge.startsWith("bearer "), challenge)
        assertFalse(challenge.startsWith("DPoP"))
        assertNull(client.getSession(issuance.sessionId).walletNotificationEvent)
    }

    @Test
    fun duplicateJsonMembersAreInvalidNotificationRequests() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val response = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"notification_id":"${issuance.notificationId}","event":"credential_accepted","event":"credential_failure"}"""
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertEquals("invalid_notification_request", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertNull(client.getSession(issuance.sessionId).walletNotificationEvent)
    }

    @Test
    fun eventDescriptionCharsetIsEnforcedOverHttp() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()

        val response = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(issuance.notificationId, "credential_accepted", "bad\nline"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertEquals("invalid_notification_request", response.body<JsonObject>()["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun dpopBoundTokenIsAcceptedAtNotificationEndpoint() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val walletFlow = Issuer2WalletFlowDriver(client)
        val createdOffer = client.createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.openBadgeCredential,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val notificationEndpoint = assertNotNull(resolvedOffer.issuerMetadata.notificationEndpoint)
        val dpopKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("notification-dpop"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val proofBuilder = DPoPProofBuilder()
        val tokenEndpoint = "$KTOR_TEST_APPLICATION_BASE_URL/openid4vci/token"
        val credentialEndpoint = "$KTOR_TEST_APPLICATION_BASE_URL/openid4vci/credential"
        val preAuthorizedCode = assertNotNull(resolvedOffer.offer.grants?.preAuthorizedCode?.preAuthorizedCode)
        val tokenResponse = client.post("/openid4vci/token") {
            header(
                DPoPConstants.HEADER_NAME,
                proofBuilder.buildProof(
                    key = dpopKey,
                    httpMethod = "POST",
                    targetUri = tokenEndpoint,
                    supportedAlgorithms = setOf(DPoPConstants.ES256),
                ),
            )
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        append("pre-authorized_code", preAuthorizedCode)
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.OK, tokenResponse.status, tokenResponse.bodyAsText())
        val accessToken = assertNotNull(tokenResponse.body<JsonObject>()["access_token"]?.jsonPrimitive?.content)
        val proofs = walletFlow.buildJwtProofs(
            issuerMetadata = resolvedOffer.issuerMetadata,
            credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
        )
        val credentialResponse = client.post("/openid4vci/credential") {
            header(HttpHeaders.Authorization, "DPoP $accessToken")
            header(
                DPoPConstants.HEADER_NAME,
                proofBuilder.buildProof(
                    key = dpopKey,
                    httpMethod = "POST",
                    targetUri = credentialEndpoint,
                    accessToken = accessToken,
                    supportedAlgorithms = setOf(DPoPConstants.ES256),
                ),
            )
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.offer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }
        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        val notificationId = assertNotNull(
            credentialResponse.body<JsonObject>()["notification_id"]?.jsonPrimitive?.content
        )

        val missingProof = client.post("/openid4vci/notification") {
            header(HttpHeaders.Authorization, "DPoP $accessToken")
            contentType(ContentType.Application.Json)
            setBody(notificationBody(notificationId, "credential_accepted"))
        }
        assertEquals(HttpStatusCode.Unauthorized, missingProof.status, missingProof.bodyAsText())
        assertTrue(missingProof.headers[HttpHeaders.WWWAuthenticate].orEmpty().startsWith("DPoP "))

        val accepted = client.post("/openid4vci/notification") {
            header(HttpHeaders.Authorization, "DPoP $accessToken")
            header(
                DPoPConstants.HEADER_NAME,
                proofBuilder.buildProof(
                    key = dpopKey,
                    httpMethod = "POST",
                    targetUri = notificationEndpoint,
                    accessToken = accessToken,
                    supportedAlgorithms = setOf(DPoPConstants.ES256),
                ),
            )
            contentType(ContentType.Application.Json)
            setBody(notificationBody(notificationId, "credential_accepted"))
        }
        assertEquals(HttpStatusCode.NoContent, accepted.status, accepted.bodyAsText())
        assertEquals(
            NotificationEvent.CREDENTIAL_ACCEPTED,
            client.getSession(createdOffer.offerId).walletNotificationEvent,
        )
    }

    @Test
    fun disabledNotificationEndpointIsNotAdvertisedOrRouted() = testApplication {
        installIssuer2WithConfigFiles(
            configureServiceConfig = { it.copy(notificationEndpointEnabled = false) }
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

    private suspend fun HttpClient.issueCredential(webhookUrl: String? = null): IssuedNotificationContext {
        val walletFlow = Issuer2WalletFlowDriver(this)
        val createdOffer = createWalletFlowCredentialOffer(
            scenario = Issuer2CredentialScenarios.openBadgeCredential,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
            runtimeOverrides = webhookUrl?.let {
                CredentialOfferRuntimeOverrides(
                    notifications = IssuanceNotifications(
                        webhook = IssuanceNotifications.WebhookNotification(it),
                    ),
                )
            },
        )
        val resolvedOffer = walletFlow.resolve(createdOffer)
        val notificationEndpoint = assertNotNull(resolvedOffer.issuerMetadata.notificationEndpoint)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val response = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
        )
        return IssuedNotificationContext(
            sessionId = createdOffer.offerId,
            accessToken = tokenResponse.access_token,
            notificationEndpoint = notificationEndpoint,
            notificationId = assertNotNull(response["notification_id"]?.jsonPrimitive?.content),
        )
    }

    private fun notificationBody(
        notificationId: String,
        event: String,
        eventDescription: String? = null,
    ) = buildJsonObject {
        put("notification_id", notificationId)
        put("event", event)
        eventDescription?.let { put("event_description", it) }
        put("future_parameter", "ignored")
    }

    private data class IssuedNotificationContext(
        val sessionId: String,
        val accessToken: String,
        val notificationEndpoint: String,
        val notificationId: String,
    )
}
