package id.walt.issuer2.notifications

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.testsupport.KTOR_TEST_APPLICATION_BASE_URL
import id.walt.issuer2.testsupport.Issuer2TestNotificationServer
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertJwtVcJsonCredentialPayload
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.createIssuer2ClientAttestationTestMaterial
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.issuer2TestJson
import id.walt.issuer2.testsupport.resolveOffer
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.dpop.DPoPConstants
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.proof.ProofKeyBinding
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class IssuanceNotificationRouteTest {
    private fun CredentialOfferCreateRequest.withRuntimeOverrides(
        runtimeOverrides: CredentialOfferRuntimeOverrides,
    ): CredentialOfferCreateRequest = copy(
        credentials = credentials.map { it.copy(runtimeOverrides = runtimeOverrides) }
    )


    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun webhookReceiverGetsEnterpriseNotificationEnvelopeForPreAuthorizedEvents() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(
                                url = notificationServer.webhookUrl(),
                            ),
                        ),
                    ),
                )
            )

            val credentialPayload = client.completePreAuthorizedJwtIssuance(createdOffer)
            assertJwtVcJsonCredentialPayload(credentialPayload)

            val expectedEvents = listOf(
                IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED,
                IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED,
                IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED,
            )
            expectedEvents.forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }

            val receivedUpdates = notificationServer.getReceivedUpdates()
                .filter { it.target == createdOffer.offerId }
            assertEquals(expectedEvents.map { it.value }, receivedUpdates.map { it.event })

            val tokenRequest = assertNotNull(
                receivedUpdates.first { it.event == IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED.value }
                    .session
            )
            assertEquals(createdOffer.offerId, tokenRequest["sessionId"]?.jsonPrimitive?.contentOrNull)
            val tokenIssuanceRequest = assertNotNull(tokenRequest["issuanceRequests"])
                .jsonArray
                .single()
                .jsonObject
            assertEquals(
                "OpenBadgeCredential_jwt_vc_json",
                tokenIssuanceRequest["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull,
            )

            val credentialSuccess = receivedUpdates.first {
                it.event == IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED.value
            }
            assertEquals(createdOffer.offerId, credentialSuccess.session["sessionId"]?.jsonPrimitive?.contentOrNull)
            assertEquals("SUCCESSFUL", credentialSuccess.session["status"]?.jsonPrimitive?.contentOrNull)
            val credentialSuccessIssuanceRequest = assertNotNull(credentialSuccess.session["issuanceRequests"])
                .jsonArray
                .single()
                .jsonObject
            assertEquals(
                "redacted",
                credentialSuccessIssuanceRequest["issuerKey"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull,
            )

            val status = receivedUpdates.first { it.event == IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED.value }
            assertEquals("SUCCESSFUL", status.session["status"]?.jsonPrimitive?.contentOrNull)
            assertEquals("true", status.session["isClosed"]?.jsonPrimitive?.contentOrNull)
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun webhookReceiverGetsSessionCorrelatedTokenFailure() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_WITH_PROVIDED_TX_CODE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = createdOffer.resolveOffer(client)
            val preAuthorizedCode = assertNotNull(
                resolvedOffer.grants?.preAuthorizedCode?.preAuthorizedCode,
            )

            val tokenResponse = client.post("/openid4vci/token") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", GrantType.PreAuthorizedCode.value)
                            append("pre-authorized_code", preAuthorizedCode)
                            append("tx_code", "000000")
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.BadRequest, tokenResponse.status, tokenResponse.bodyAsText())

            val expectedEvents = listOf(
                IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED,
            )
            expectedEvents.forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }
            val receivedUpdates = notificationServer.getReceivedUpdates()
                .filter { it.target == createdOffer.offerId }
            assertEquals(expectedEvents.map { it.value }, receivedUpdates.map { it.event })

            // ACTIVE is the default and therefore omitted: the session is still usable for a retry.
            val failure = receivedUpdates.single {
                it.event == IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED.value
            }
            assertNull(failure.session["status"]?.jsonPrimitive?.contentOrNull)
            assertEquals("invalid_grant", failure.error)
            assertEquals("tx_code is invalid", failure.errorDescription)
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun uncorrelatedDpopProofFailurePublishesGenericIssuerEventOnly() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = createdOffer.resolveOffer(client)
            val preAuthorizedCode = assertNotNull(resolvedOffer.grants?.preAuthorizedCode?.preAuthorizedCode)

            val walletKey = JWKKey.generate(KeyType.secp256r1)
            val tokenResponse = client.post("/openid4vci/token") {
                header(
                    DPoPConstants.HEADER_NAME,
                    dpopProof(walletKey, "$KTOR_TEST_APPLICATION_BASE_URL/openid4vci/token"),
                )
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", GrantType.PreAuthorizedCode.value)
                            append("pre-authorized_code", preAuthorizedCode)
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, tokenResponse.status, tokenResponse.bodyAsText())
            val accessToken = assertNotNull(tokenResponse.body<JsonObject>()["access_token"]?.jsonPrimitive?.contentOrNull)
            val requestId = "credential-dpop-failure"
            val issuerEvents = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

            val credentialResponse = client.post("/openid4vci/credential") {
                header(HttpHeaders.XRequestId, requestId)
                header(HttpHeaders.Authorization, "DPoP $accessToken")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("credential_configuration_id", JsonPrimitive("OpenBadgeCredential_jwt_vc_json")) })
            }
            // invalid_dpop_proof is answered with 401 and a DPoP challenge, per RFC 9449.
            assertEquals(HttpStatusCode.Unauthorized, credentialResponse.status)
            assertEquals(requestId, credentialResponse.headers[HttpHeaders.XRequestId])

            val errorBody = credentialResponse.body<JsonObject>()
            val failure = issuerEvents.replayCache
                .filter { it.requestId == requestId }
                .single()
            assertEquals(IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED.value, failure.event)
            assertEquals(requestId, failure.target)
            assertEquals(true, failure.session.isEmpty())
            assertEquals(errorBody["error"]?.jsonPrimitive?.contentOrNull, failure.error)
            assertEquals(errorBody["error_description"]?.jsonPrimitive?.contentOrNull, failure.errorDescription)

            notificationServer.awaitEvent(
                createdOffer.offerId,
                IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED,
            )
            val events = notificationServer.getReceivedUpdates()
                .filter { it.target == createdOffer.offerId }
                .map { it.event }
            assertEquals(
                listOf(
                    IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                    IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                    IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED,
                ).map { it.value },
                events,
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    private suspend fun dpopProof(key: JWKKey, targetUri: String): String = key.signJws(
        buildJsonObject {
            put("jti", JsonPrimitive("proof-${key.getPublicKey().getThumbprint()}"))
            put(DPoPConstants.HTTP_METHOD_CLAIM, JsonPrimitive("POST"))
            put(DPoPConstants.HTTP_URI_CLAIM, JsonPrimitive(targetUri))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }.toString().encodeToByteArray(),
        headers = mapOf(
            "typ" to JsonPrimitive(DPoPConstants.JWT_TYPE),
            "jwk" to key.getPublicKey().exportJWKObject(),
        ),
    )

    @Test
    fun webhookReceiverGetsRefreshTokenEvents() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            val clientAttestation = createIssuer2ClientAttestationTestMaterial()
            installIssuer2WithConfigFiles { config ->
                config.copy(clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig)
            }
            val client = apiClient()
            val walletFlow = Issuer2WalletFlowDriver(
                client = client,
                walletClientConfig = walletClientConfig,
                attestationAssembler = clientAttestation.attestationAssembler,
            )
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = walletFlow.resolve(createdOffer)
            val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
            val refreshToken = assertNotNull(tokenResponse.refresh_token)

            val refreshedResponse = walletFlow.refreshAccessToken(
                resolvedOffer = resolvedOffer,
                refreshToken = refreshToken,
            )
            assertBearerAccessToken(refreshedResponse)

            val expectedEvents = listOf(
                IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED,
                IssuanceSessionEvent.TOKEN_REQUEST_REFRESH_TOKEN_SUCCEEDED,
            )

            expectedEvents.distinct().forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }
            assertEquals(
                expectedEvents.map { it.value },
                notificationServer.getReceivedUpdates()
                    .filter { it.target == createdOffer.offerId }
                    .map { it.event },
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun webhookReceiverGetsCredentialProofFailureSequence() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )

            val requestId = "credential-proof-failure"
            val credentialResponse = client.requestPreAuthorizedJwtCredential(
                createdOffer,
                tamperProof = true,
                requestId = requestId,
            )
            assertEquals(HttpStatusCode.BadRequest, credentialResponse.status)
            assertEquals(requestId, credentialResponse.headers[HttpHeaders.XRequestId])
            val errorBody = credentialResponse.body<JsonObject>()

            val expectedEvents = listOf(
                IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_SUCCEEDED,
                IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_FAILED,
            )
            expectedEvents.forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }
            val receivedUpdates = notificationServer.getReceivedUpdates()
                .filter { it.target == createdOffer.offerId }
            assertEquals(expectedEvents.map { it.value }, receivedUpdates.map { it.event })
            // invalid_proof is retryable: the grant stays usable, so the session is not concluded.
            assertNull(
                receivedUpdates.singleOrNull { it.event == IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED.value },
            )
            val proofFailureUpdate = assertNotNull(
                receivedUpdates.singleOrNull {
                    it.event == IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_FAILED.value
                },
            )
            assertNull(proofFailureUpdate.session["status"]?.jsonPrimitive?.contentOrNull)
            assertEquals(requestId, proofFailureUpdate.requestId)
            assertEquals(errorBody["error"]?.jsonPrimitive?.contentOrNull, proofFailureUpdate.error)
            assertEquals(
                errorBody["error_description"]?.jsonPrimitive?.contentOrNull,
                proofFailureUpdate.errorDescription,
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun unknownCredentialConfigurationDoesNotCloseIssuanceSession() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val walletFlow = Issuer2WalletFlowDriver(client, walletClientConfig)
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = walletFlow.resolve(createdOffer)
            val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)

            val requestId = "credential-unknown-configuration"
            val credentialResponse = client.post("/openid4vci/credential") {
                header(HttpHeaders.XRequestId, requestId)
                bearerAuth(tokenResponse.access_token)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("credential_configuration_id", "unknown_credential_configuration") })
            }
            assertEquals(HttpStatusCode.BadRequest, credentialResponse.status)
            val errorBody = credentialResponse.body<JsonObject>()

            val failureEvent = notificationServer.awaitEvent(
                createdOffer.offerId,
                IssuanceSessionEvent.CREDENTIAL_REQUEST_FAILED,
            )
            assertEquals(requestId, failureEvent.requestId)
            assertEquals("unknown_credential_configuration", failureEvent.error)
            assertEquals(errorBody["error"]?.jsonPrimitive?.contentOrNull, failureEvent.error)
            assertEquals(
                errorBody["error_description"]?.jsonPrimitive?.contentOrNull,
                failureEvent.errorDescription,
            )
            assertNull(
                notificationServer.getReceivedUpdates().singleOrNull {
                    it.target == createdOffer.offerId &&
                        it.event == IssuanceSessionEvent.ISSUANCE_STATUS_CHANGED.value
                },
            )

            val storedSession = client.get("/issuer2/sessions/${createdOffer.offerId}").body<IssuanceSession>()
            assertEquals(IssuanceSessionStatus.ACTIVE, storedSession.status)
            assertEquals(false, storedSession.isClosed)
            assertNull(storedSession.failure)

            val credentialPayload = walletFlow.requestCredential(
                resolvedOffer = resolvedOffer,
                accessToken = tokenResponse.access_token,
            )
            assertJwtVcJsonCredentialPayload(credentialPayload)
            notificationServer.awaitEvent(
                createdOffer.offerId,
                IssuanceSessionEvent.CREDENTIAL_REQUEST_W3C_VC_SUCCEEDED,
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun webhookReceiverGetsParAndAuthorizationRequestEvents() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            val clientAttestation = createIssuer2ClientAttestationTestMaterial()
            installIssuer2WithConfigFiles { config ->
                config.copy(clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig)
            }
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = createdOffer.resolveOffer(client)
            val clientId = "issuer2-notification-test"
            val redirectUri = "https://wallet.example/callback"
            val authorizationServerIssuer = assertNotNull(
                client.get("/.well-known/oauth-authorization-server/openid4vci")
                    .body<JsonObject>()["issuer"]
                    ?.jsonPrimitive
                    ?.contentOrNull,
            )
            val attestationHeaders = clientAttestation.attestationAssembler.buildAttestationHeaders(
                instanceKey = JWKKey.generate(KeyType.secp256r1),
                clientId = clientId,
                audience = authorizationServerIssuer,
            )
            val parResponse = client.post("/openid4vci/par") {
                header(ClientAttestationHeaders.CLIENT_ATTESTATION, attestationHeaders.attestationJwt)
                header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, attestationHeaders.popJwt)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("response_type", "code")
                            append("redirect_uri", redirectUri)
                            append("state", "notification-state")
                            append("scope", resolvedOffer.credentialConfigurationIds.single())
                            append("issuer_state", createdOffer.offerId)
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, parResponse.status, parResponse.bodyAsText())
            val requestUri = assertNotNull(
                parResponse.body<JsonObject>()["request_uri"]?.jsonPrimitive?.contentOrNull,
            )

            val authorizationResponse = client.get("/openid4vci/authorize") {
                parameter("client_id", clientId)
                parameter("request_uri", requestUri)
            }
            assertEquals(HttpStatusCode.Found, authorizationResponse.status, authorizationResponse.bodyAsText())

            val expectedEvents = listOf(
                IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED,
            )
            expectedEvents.forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }
            assertEquals(
                expectedEvents.map { it.value },
                notificationServer.getReceivedUpdates()
                    .filter { it.target == createdOffer.offerId }
                    .map { it.event },
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun parAgainstPreAuthorizedSessionIsRejectedWithoutSessionNotification() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            val clientAttestation = createIssuer2ClientAttestationTestMaterial()
            installIssuer2WithConfigFiles { config ->
                config.copy(clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig)
            }
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.withRuntimeOverrides(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(notificationServer.webhookUrl()),
                        ),
                    ),
                )
            )
            val resolvedOffer = createdOffer.resolveOffer(client)
            val clientId = "issuer2-notification-test"
            val authorizationServerIssuer = assertNotNull(
                client.get("/.well-known/oauth-authorization-server/openid4vci")
                    .body<JsonObject>()["issuer"]
                    ?.jsonPrimitive
                    ?.contentOrNull,
            )
            val attestationHeaders = clientAttestation.attestationAssembler.buildAttestationHeaders(
                instanceKey = JWKKey.generate(KeyType.secp256r1),
                clientId = clientId,
                audience = authorizationServerIssuer,
            )

            // issuer_state from a pre-authorized offer is not valid for an authorization-code flow.
            val parResponse = client.post("/openid4vci/par") {
                header(ClientAttestationHeaders.CLIENT_ATTESTATION, attestationHeaders.attestationJwt)
                header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, attestationHeaders.popJwt)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("response_type", "code")
                            append("redirect_uri", "https://wallet.example/callback")
                            append("state", "notification-state")
                            append("scope", resolvedOffer.credentialConfigurationIds.single())
                            append("issuer_state", createdOffer.offerId)
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.BadRequest, parResponse.status)
            assertEquals(
                "invalid_request",
                parResponse.body<JsonObject>()["error"]?.jsonPrimitive?.content,
            )

            assertEquals(
                listOf(
                    IssuanceSessionEvent.CREDENTIAL_OFFER_CREATED,
                    IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED,
                ).map { it.value },
                notificationServer.getReceivedUpdates()
                    .filter { it.target == createdOffer.offerId }
                    .map { it.event },
            )
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun sseRouteStreamsEnterpriseNotificationEnvelope() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)

        var resolvedOfferCredentialIssuer: String? = null
        val update = client.readFirstSseUpdate(createdOffer.offerId) {
            resolvedOfferCredentialIssuer = createdOffer.resolveOffer(client).credentialIssuer
        }

        assertEquals(createdOffer.offerId, update.target)
        assertEquals(IssuanceSessionEvent.CREDENTIAL_OFFER_RETRIEVED.value, update.event)
        assertEquals(createdOffer.offerId, update.session["sessionId"]?.jsonPrimitive?.contentOrNull)
        val issuanceRequest = assertNotNull(update.session["issuanceRequests"])
            .jsonArray
            .single()
            .jsonObject
        assertEquals(
            "OpenBadgeCredential_jwt_vc_json",
            issuanceRequest["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull,
        )
        assertEquals(
            resolvedOfferCredentialIssuer,
            update.session["credentialOffer"]?.jsonObject?.get("credential_issuer")?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun HttpClient.completePreAuthorizedJwtIssuance(
        createdOffer: CredentialOfferCreateResponse,
    ): JsonObject {
        val credentialResponse = requestPreAuthorizedJwtCredential(createdOffer)
        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        return credentialResponse.body()
    }

    private suspend fun HttpClient.requestPreAuthorizedJwtCredential(
        createdOffer: CredentialOfferCreateResponse,
        tamperProof: Boolean = false,
        requestId: String? = null,
    ): HttpResponse {
        val resolvedOffer = createdOffer.resolveOffer(this)
        val preAuthorizedCode = assertNotNull(resolvedOffer.grants?.preAuthorizedCode?.preAuthorizedCode)
        val tokenResponse = TokenRequestBuilder(walletClientConfig, this).exchangePreAuthorizedCode(
            tokenEndpoint = "/openid4vci/token",
            preAuthorizedCode = preAuthorizedCode,
            txCode = null,
            anonymous = true,
        )
        assertBearerAccessToken(tokenResponse)

        val issuerMetadata = get("/.well-known/openid-credential-issuer/openid4vci").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body<CredentialIssuerMetadata>()
        val nonceResponse = post("/openid4vci/nonce").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body<JsonObject>()
        val nonce = assertNotNull(nonceResponse["c_nonce"]?.jsonPrimitive?.contentOrNull)
        val proofKey = JWKKey.generate(KeyType.secp256r1)
        val holderDid = DidJwkRegistrar()
            .registerByKey(proofKey, DidJwkCreateOptions(KeyType.secp256r1))
            .did
        val validProofs = JwtProofBuilder().buildProof(
            key = proofKey,
            audience = issuerMetadata.credentialIssuer,
            nonce = nonce,
            binding = ProofKeyBinding.KeyId("$holderDid#0"),
        )
        val proofs = if (tamperProof) {
            validProofs.copy(jwt = validProofs.jwt?.map(::tamperSignature))
        } else {
            validProofs
        }

        return post("/openid4vci/credential") {
            requestId?.let { header(HttpHeaders.XRequestId, it) }
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }
    }

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split('.')
        require(parts.size == 3)
        val replacement = if (parts[2].first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${parts[2].drop(1)}"
    }

    private suspend fun HttpClient.readFirstSseUpdate(
        sessionId: String,
        trigger: suspend () -> Unit,
    ): KtorSessionUpdate =
        withTimeout(15_000.milliseconds) {
            prepareGet("/issuer2/sessions/$sessionId/events") {
                accept(ContentType.Text.EventStream)
            }.execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val channel = response.bodyAsChannel()
                assertEquals("{}", readNextSseData(channel))
                trigger()
                readFirstSseUpdate(channel)
            }
        }

    private suspend fun readFirstSseUpdate(channel: ByteReadChannel): KtorSessionUpdate {
        while (true) {
            val data = readNextSseData(channel)
            if (data.isNotEmpty() && data != "{}") {
                return issuer2TestJson.decodeFromString(data)
            }
        }
    }

    private suspend fun readNextSseData(channel: ByteReadChannel): String {
        while (true) {
            val line = channel.readUTF8Line()
                ?: error("SSE stream closed before an event was received")
            if (line.startsWith("data:")) {
                return line.removePrefix("data:").trim()
            }
        }
    }

    private companion object {
        val walletClientConfig = ClientConfiguration(
            clientId = "issuer2-notification-test",
            redirectUris = listOf("https://wallet.example/callback"),
        )
    }
}
