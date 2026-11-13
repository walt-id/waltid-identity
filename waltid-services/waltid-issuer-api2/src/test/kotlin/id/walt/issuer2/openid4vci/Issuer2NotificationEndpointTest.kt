package id.walt.issuer2.openid4vci

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.models.CredentialOfferCredential
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.testsupport.Issuer2TestNotificationServer
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.KTOR_TEST_APPLICATION_BASE_URL
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
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
import io.ktor.client.request.get
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
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
    fun notificationRequiresJsonContentTypeAndVerifiesTokenBeforeInvalidBody() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val issuance = client.issueCredential()
        for (type in listOf(null, "text/plain", "application/jwt", "application/jsonp")) {
            val response = client.post(issuance.notificationEndpoint) {
                bearerAuth(issuance.accessToken)
                if (type != null) header(HttpHeaders.ContentType, type)
            }
            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }
        val accepted = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
            setBody(notificationBody(issuance.notificationId, "credential_accepted", "stored"))
        }
        assertEquals(HttpStatusCode.NoContent, accepted.status, accepted.bodyAsText())
        assertEquals("", accepted.bodyAsText())
        val excessive = """{"notification_id":"${issuance.notificationId}","event":"credential_accepted","ignored":""" +
            "[".repeat(20_000) + "0" + "]".repeat(20_000) + "}"
        for (body in listOf("{", excessive)) {
            val invalidToken = client.post(issuance.notificationEndpoint) {
                bearerAuth("invalid")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.Unauthorized, invalidToken.status)
            assertEquals("invalid_token", invalidToken.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        }
        val invalidBody = client.post(issuance.notificationEndpoint) {
            bearerAuth(issuance.accessToken)
            contentType(ContentType.Application.Json)
            setBody(excessive)
        }
        assertEquals(HttpStatusCode.BadRequest, invalidBody.status)
        assertEquals("invalid_notification_request", invalidBody.body<JsonObject>()["error"]?.jsonPrimitive?.content)
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
    fun deferredCollectionAcceptsWalletNotificationWithoutDroppingTheSession() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val configurationId = resolved.offer.credentialConfigurationIds.single()
        val pending = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(credentialRequest(configurationId, wallet.buildJwtProofs(resolved.issuerMetadata, configurationId)))
        }
        assertEquals(HttpStatusCode.Accepted, pending.status, pending.bodyAsText())
        val transactionId = pending.body<JsonObject>().getValue("transaction_id").jsonPrimitive.content
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status,
        )
        val issued = client.post(assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", transactionId) })
        }
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        val notificationId = issued.body<JsonObject>().getValue("notification_id").jsonPrimitive.content
        val accepted = client.post(assertNotNull(resolved.issuerMetadata.notificationEndpoint)) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(notificationBody(notificationId, "credential_accepted", "stored"))
        }
        assertEquals(HttpStatusCode.NoContent, accepted.status, accepted.bodyAsText())
        val session = client.getSession(offer.offerId)
        assertEquals(IssuanceSessionStatus.SUCCESSFUL, session.status)
        assertEquals(NotificationEvent.CREDENTIAL_ACCEPTED, session.walletNotificationEvent)
        assertEquals("stored", session.walletNotificationEventDescription)
        assertEquals(
            "READY",
            client.get("/issuer2/sessions/${offer.offerId}").body<JsonObject>()["deferredStatus"]?.jsonPrimitive?.contentOrNull,
        )
    }

    @Test
    fun deferredCollectionKeepsAnOverlappingWalletReportOnSuccess() = verifyOverlappingWalletReport(rejectCollection = false)

    @Test
    fun deferredCollectionKeepsAnOverlappingWalletReportOnRejection() = verifyOverlappingWalletReport(rejectCollection = true)

    private fun verifyOverlappingWalletReport(rejectCollection: Boolean) = testApplication {
        val collectionStarted = CompletableDeferred<Unit>()
        val resumeCollection = CompletableDeferred<Unit>()
        var overlap = false
        installIssuer2WithConfigFiles(credentialProofKeyAcceptance = CredentialProofKeyAcceptance { _, _ ->
            if (overlap) {
                collectionStarted.complete(Unit)
                withTimeout(10_000) { resumeCollection.await() }
                !rejectCollection
            } else true
        })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val scenarios = listOf(Issuer2CredentialScenarios.identitySdJwt, Issuer2CredentialScenarios.isoMdl)
        val offer = client.createCredentialOffer(MultiCredentialOfferCreateRequest(
            scenarios.map { CredentialOfferCredential(it.profileId) },
            AuthenticationMethod.PRE_AUTHORIZED,
            deferred = true,
        ))
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, txCode = null)
        val transactions = scenarios.map { scenario ->
            val proof = wallet.buildJwtProofs(resolved.issuerMetadata, scenario.credentialConfigurationId)
            val response = client.post(resolved.issuerMetadata.credentialEndpoint) {
                bearerAuth(token.access_token)
                contentType(ContentType.Application.Json)
                setBody(credentialRequest(scenario.credentialConfigurationId, proof))
            }
            assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
            response.body<JsonObject>().getValue("transaction_id").jsonPrimitive.content
        }
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/issuer2/credential-offers/${offer.offerId}/deferred/complete").status,
        )
        val endpoint = assertNotNull(resolved.issuerMetadata.deferredCredentialEndpoint)
        suspend fun collect(transactionId: String) = client.post(endpoint) {
            bearerAuth(token.access_token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("transaction_id", transactionId) })
        }
        val first = collect(transactions[0])
        assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())
        val notificationId = first.body<JsonObject>().getValue("notification_id").jsonPrimitive.content
        overlap = true
        coroutineScope {
            val collecting = async { collect(transactions[1]) }
            withTimeout(10_000) { collectionStarted.await() }
            try {
                val reported = client.post(assertNotNull(resolved.issuerMetadata.notificationEndpoint)) {
                    bearerAuth(token.access_token)
                    contentType(ContentType.Application.Json)
                    setBody(notificationBody(notificationId, "credential_accepted", "stored"))
                }
                assertEquals(HttpStatusCode.NoContent, reported.status, reported.bodyAsText())
                val reportedSession = client.getSession(offer.offerId)
                val reportedIdentifier = reportedSession.issuanceResults.entries.single {
                    it.value.walletNotification?.notificationId == notificationId
                }.key
                assertEquals(NotificationEvent.CREDENTIAL_ACCEPTED, reportedSession.issuanceResults.getValue(reportedIdentifier).walletNotification?.event)
            } finally {
                resumeCollection.complete(Unit)
            }
            val second = collecting.await()
            assertEquals(if (rejectCollection) HttpStatusCode.BadRequest else HttpStatusCode.OK, second.status, second.bodyAsText())
            if (rejectCollection) assertEquals("invalid_proof", second.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        }
        val finished = client.getSession(offer.offerId)
        val reportedIdentifier = finished.issuanceResults.entries.single {
            it.value.walletNotification?.notificationId == notificationId
        }.key
        assertEquals(
            NotificationEvent.CREDENTIAL_ACCEPTED,
            finished.issuanceResults.getValue(reportedIdentifier).walletNotification?.event,
        )
        assertEquals("stored", finished.issuanceResults.getValue(reportedIdentifier).walletNotification?.eventDescription)
        assertEquals(if (rejectCollection) 1 else 2, finished.issuanceResults.size)
        assertTrue(finished.issuanceResults.filterKeys { it != reportedIdentifier }.values.all { it.walletNotification?.event == null })
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

private val IssuanceSession.walletNotificationEvent
    get() = issuanceResults.values.singleOrNull()?.walletNotification?.event

private val IssuanceSession.walletNotificationEventDescription
    get() = issuanceResults.values.singleOrNull()?.walletNotification?.eventDescription
