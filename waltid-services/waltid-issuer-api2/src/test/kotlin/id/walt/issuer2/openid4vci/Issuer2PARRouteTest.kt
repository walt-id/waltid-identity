package id.walt.issuer2.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.OpenId4VciController
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.notifications.IssuanceSessionEvent
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.issuer2.testsupport.createIssuer2ClientAttestationTestMaterial
import id.walt.issuer2.testsupport.generateIssuer2WalletInstanceKey
import id.walt.issuer2.web.plugins.configureMonitoring
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.par.PARRecord
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Issuer2PARRouteTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Test
    fun `par route returns no-store response and request uri payload`() = testParApplication { client ->
        val requestId = "par-success-request"
        val (response, event) = client.postParAndCaptureEvent(
            requestId = requestId,
        ) {
            setBody(validParForm())
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(requestId, response.headers[HttpHeaders.XRequestId])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", response.headers[HttpHeaders.Pragma])

        val payload = response.body<JsonObject>()
        val requestUri = assertNotNull(payload["request_uri"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals("90", payload["expires_in"]?.jsonPrimitive?.content)

        assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `par validation failure publishes an uncorrelated failure`() = testParApplication { client ->
        val requestId = "par-validation-failure"
        val (response, event) = client.postParAndCaptureEvent(
            requestId = requestId,
        ) {
            setBody(FormDataContent(Parameters.build { append("client_id", "test-client") }))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = response.body<JsonObject>()
        assertEquals("invalid_request", payload["error"]?.jsonPrimitive?.content)
        assertEquals("Missing response_type", payload["error_description"]?.jsonPrimitive?.content)
        assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED.value, event.event)
        assertEquals("invalid_request", event.error)
        assertEquals("Missing response_type", event.errorDescription)
        assertEquals(true, event.session.isEmpty())
    }

    @Test
    fun `par body decoding failure uses the normal failure path`() = testParApplication { client ->
        val requestId = "par-body-decoding-failure"
        val (response, event) = client.postParAndCaptureEvent(
            requestId = requestId,
        ) {
            contentType(ContentType.Text.Plain)
            setBody("client_id=test-client")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = response.body<JsonObject>()
        assertEquals("invalid_request", payload["error"]?.jsonPrimitive?.content)
        assertEquals("Missing client_id", payload["error_description"]?.jsonPrimitive?.content)
        assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals("invalid_request", event.error)
        assertEquals("Missing client_id", event.errorDescription)
        assertEquals(true, event.session.isEmpty())
    }

    @Test
    fun `par success publishes the validated issuance session`() {
        val session = authorizedSession("authorized-session")
        testParApplication(
            issuanceSessionRepository = TestIssuanceSessionRepository(session),
        ) { client ->
            val requestId = "par-correlated-success"
            val (response, event) = client.postParAndCaptureEvent(
                requestId = requestId,
            ) {
                setBody(validParForm(issuerState = session.sessionId))
            }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED.value, event.event)
            assertEquals(session.sessionId, event.target)
            assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
            assertNull(event.error)
            assertNull(event.errorDescription)
        }
    }

    @Test
    fun `par rejects unknown issuer state without session correlation`() = testParApplication { client ->
        val requestId = "par-invalid-issuer-state"
        val (response, event) = client.postParAndCaptureEvent(
            requestId = requestId,
        ) {
            setBody(validParForm(issuerState = "injected-state"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = response.body<JsonObject>()
        assertEquals("invalid_request", payload["error"]?.jsonPrimitive?.content)
        assertEquals("issuer_state is invalid", payload["error_description"]?.jsonPrimitive?.content)
        assertEquals(requestId, event.target)
        assertEquals("invalid_request", event.error)
        assertEquals("issuer_state is invalid", event.errorDescription)
        assertEquals(true, event.session.isEmpty())
    }

    @Test
    fun `par rejects sessions that cannot authorize issuance`() {
        val sessions = listOf(
            authorizedSession("pre-authorized").copy(authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED),
            authorizedSession("inactive").copy(status = IssuanceSessionStatus.SUCCESSFUL),
            authorizedSession("closed").copy(isClosed = true),
            authorizedSession("expired").copy(expiresAt = Instant.DISTANT_PAST),
        )

        testParApplication(
            issuanceSessionRepository = TestIssuanceSessionRepository(*sessions.toTypedArray()),
        ) { client ->
            sessions.forEach { session ->
                val requestId = "par-rejected-${session.sessionId}"
                val (response, event) = client.postParAndCaptureEvent(
                    requestId = requestId,
                ) {
                    setBody(validParForm(issuerState = session.sessionId))
                }

                assertEquals(HttpStatusCode.BadRequest, response.status, session.sessionId)
                val payload = response.body<JsonObject>()
                assertEquals("invalid_request", payload["error"]?.jsonPrimitive?.content, session.sessionId)
                assertEquals(
                    "issuer_state is invalid",
                    payload["error_description"]?.jsonPrimitive?.content,
                    session.sessionId,
                )
                assertEquals("invalid_request", event.error, session.sessionId)
                assertEquals("issuer_state is invalid", event.errorDescription, session.sessionId)
                assertEquals(requestId, event.target, session.sessionId)
                assertEquals(true, event.session.isEmpty(), session.sessionId)
            }
        }
    }

    @Test
    fun `par response failure publishes a correlated server error`() {
        val session = authorizedSession("par-response-failure-session")
        testParApplication(
            issuanceSessionRepository = TestIssuanceSessionRepository(session),
            parRepository = DuplicatePARRepository,
        ) { client ->
            val requestId = "par-response-failure"
            val (response, event) = client.postParAndCaptureEvent(requestId) {
                setBody(validParForm(issuerState = session.sessionId))
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val payload = response.body<JsonObject>()
            assertEquals("server_error", payload["error"]?.jsonPrimitive?.content)
            assertEquals(
                "Unable to store pushed authorization request",
                payload["error_description"]?.jsonPrimitive?.content,
            )
            assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED.value, event.event)
            assertEquals(session.sessionId, event.target)
            assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
            assertEquals("server_error", event.error)
            assertEquals("Unable to store pushed authorization request", event.errorDescription)
        }
    }

    @Test
    fun `unexpected par failure publishes a correlated server error`() {
        val session = authorizedSession("par-unexpected-failure-session")
        testParApplication(
            issuanceSessionRepository = TestIssuanceSessionRepository(session),
            parRepository = FailingPARRepository,
        ) { client ->
            val requestId = "par-unexpected-failure"
            val (response, event) = client.postParAndCaptureEvent(requestId) {
                setBody(validParForm(issuerState = session.sessionId))
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val payload = response.body<JsonObject>()
            assertEquals("server_error", payload["error"]?.jsonPrimitive?.content)
            assertEquals("PAR processing failed", payload["error_description"]?.jsonPrimitive?.content)
            assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED.value, event.event)
            assertEquals(session.sessionId, event.target)
            assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
            assertEquals("server_error", event.error)
            assertEquals("PAR processing failed", event.errorDescription)
        }
    }

    @Test
    fun `nonce route returns a signed no-store nonce`() = testApplication {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        application {
            configureMonitoring()
            install(ServerContentNegotiation) {
                json(json)
            }
            install(Authentication) {
                bearer("auth-oauth") {}
            }
            routing {
                testController(serviceConfig).register(this)
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }
        val events = id.walt.ktornotifications.SseNotifier.getSseFlow(
            IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET,
        )

        val response = client.post("/openid4vci/nonce")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val requestId = assertNotNull(response.headers[HttpHeaders.XRequestId])
        val payload = response.body<JsonObject>()
        val nonce = assertNotNull(payload["c_nonce"]?.jsonPrimitive?.contentOrNull)
        assertTrue(KeyManager.resolveSerializedKey(serviceConfig.ciTokenKey).getPublicKey().verifyJws(nonce).isSuccess)
        val event = events.replayCache.single { it.requestId == requestId }
        assertEquals(IssuanceSessionEvent.NONCE_REQUEST_SUCCEEDED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `token route returns no-store headers on errors`() = testApplication {
        application {
            configureMonitoring()
            install(ServerContentNegotiation) {
                json(json)
            }
            install(Authentication) {
                bearer("auth-oauth") {}
            }
            routing {
                testController().register(this)
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }

        val response = client.post("/openid4vci/token") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "unsupported")
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", response.headers[HttpHeaders.Pragma])
    }

    @Test
    fun `par route accepts client attestation headers`() = testApplication {
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val client = createParTestClient(
            serviceConfig = Issuer2ServiceConfig(
                baseUrl = "http://localhost",
                clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            ),
        )
        val attestationHeaders = clientAttestation.attestationAssembler.buildAttestationHeaders(
            instanceKey = generateIssuer2WalletInstanceKey("par-wallet-instance"),
            clientId = EUDI_WALLET_CLIENT_ID,
            audience = "http://localhost/openid4vci",
        )
        val requestId = "par-client-attestation"
        val (response, event) = client.postParAndCaptureEvent(
            requestId = requestId,
        ) {
            header(ClientAttestationHeaders.CLIENT_ATTESTATION, attestationHeaders.attestationJwt)
            header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, attestationHeaders.popJwt)
            setBody(validParForm(clientId = EUDI_WALLET_CLIENT_ID))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", response.headers[HttpHeaders.Pragma])

        val payload = response.body<JsonObject>()
        val requestUri = assertNotNull(payload["request_uri"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals("90", payload["expires_in"]?.jsonPrimitive?.content)
        assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_SUCCEEDED.value, event.event)
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `par client authentication failure publishes an uncorrelated failure`() = testApplication {
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val client = createParTestClient(
            serviceConfig = Issuer2ServiceConfig(
                baseUrl = "http://localhost",
                clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            ),
        )
        val requestId = "par-client-authentication-failure"
        val (response, event) = client.postParAndCaptureEvent(requestId) {
            setBody(validParForm(clientId = EUDI_WALLET_CLIENT_ID))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val payload = response.body<JsonObject>()
        assertEquals("invalid_client", payload["error"]?.jsonPrimitive?.content)
        assertEquals(
            "Client authentication is required for this endpoint",
            payload["error_description"]?.jsonPrimitive?.content,
        )
        assertEquals(IssuanceSessionEvent.PUSHED_AUTHORIZATION_REQUEST_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals("invalid_client", event.error)
        assertEquals("Client authentication is required for this endpoint", event.errorDescription)
    }

    private fun testParApplication(
        serviceConfig: Issuer2ServiceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost"),
        issuanceSessionRepository: IssuanceSessionRepository = NoopIssuanceSessionRepository,
        parRepository: PARRepository = InMemoryPARRepository(),
        test: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        test(createParTestClient(serviceConfig, issuanceSessionRepository, parRepository))
    }

    private fun ApplicationTestBuilder.createParTestClient(
        serviceConfig: Issuer2ServiceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost"),
        issuanceSessionRepository: IssuanceSessionRepository = NoopIssuanceSessionRepository,
        parRepository: PARRepository = InMemoryPARRepository(),
    ): HttpClient {
        application {
            configureMonitoring()
            install(ServerContentNegotiation) { json(json) }
            install(Authentication) { bearer("auth-oauth") {} }
            routing {
                testController(
                    serviceConfig = serviceConfig,
                    issuanceSessionRepository = issuanceSessionRepository,
                    parRepository = parRepository,
                ).register(this)
            }
        }
        val client = createClient {
            install(ClientContentNegotiation) { json(json) }
        }
        return client
    }

    private fun testController(
        serviceConfig: Issuer2ServiceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost"),
        issuanceSessionRepository: IssuanceSessionRepository = NoopIssuanceSessionRepository,
        parRepository: PARRepository = InMemoryPARRepository(),
    ): OpenId4VciController {
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(issuanceSessionRepository)
        val notificationService = IssuanceNotificationService()
        val openId4VciModule = OpenId4VciModule.create(
            config = serviceConfig,
            authorizationCodeRepository = NoopAuthorizationCodeRepository,
            preAuthorizedCodeRepository = NoopPreAuthorizedCodeRepository,
            parRepository = parRepository,
            refreshTokenRepository = InMemoryRefreshTokenRepository(),
        )
        val metadataService = MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = profileService,
            sessionService = sessionService,
            preAuthorizedGrantAnonymousAccessSupported =
                openId4VciModule.preAuthorizedCodeIssuer.anonymousAccessSupported,
        )

        return OpenId4VciController(
            metadataService = metadataService,
            protocolService = OpenId4VciProtocolService(
                oauth2Provider = openId4VciModule.oauth2Provider,
                sessionService = sessionService,
                profileService = profileService,
                metadataService = metadataService,
                notificationService = notificationService,
                credentialNonceService = openId4VciModule.credentialNonceService,
            ),
            offerService = CredentialOfferService(
                profileService = profileService,
                sessionService = sessionService,
                preAuthorizedCodeIssuer = openId4VciModule.preAuthorizedCodeIssuer,
                config = serviceConfig,
                notificationService = notificationService,
            ),
            notificationService = notificationService,
        )
    }

    private fun validParForm(
        clientId: String = "test-client",
        issuerState: String? = null,
    ) =
        FormDataContent(
            Parameters.build {
                append("client_id", clientId)
                append("response_type", "code")
                append("redirect_uri", "https://wallet.example/callback")
                append("scope", "openid")
                append("state", "state123")
                issuerState?.let { append("issuer_state", it) }
            }
        )

    private suspend fun HttpClient.postParAndCaptureEvent(
        requestId: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): Pair<HttpResponse, KtorSessionUpdate> {
        val events = id.walt.ktornotifications.SseNotifier.getSseFlow(
            IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET,
        )
        val response = post("/openid4vci/par") {
            header(HttpHeaders.XRequestId, requestId)
            configure()
        }

        val matchingEvents = events.replayCache.filter { it.requestId == requestId }
        assertEquals(1, matchingEvents.size, "Expected exactly one PAR event for request $requestId")
        return response to matchingEvents.single()
    }

    private fun authorizedSession(sessionId: String) = IssuanceSession(
        sessionId = sessionId,
        profileId = "identity-profile",
        authenticationMethod = AuthenticationMethod.AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = buildJsonObject { put("type", "jwk") },
        credentialData = buildJsonObject {},
        expiresAt = Instant.DISTANT_FUTURE,
    )

    private class TestIssuanceSessionRepository(vararg initial: IssuanceSession) : IssuanceSessionRepository {
        private val sessions = initial.associateBy { it.sessionId }.toMutableMap()

        override suspend fun save(session: IssuanceSession): IssuanceSession =
            session.also { sessions[it.sessionId] = it }

        override suspend fun get(sessionId: String): IssuanceSession? = sessions[sessionId]
        override suspend fun list(): List<IssuanceSession> = sessions.values.toList()
        override suspend fun remove(sessionId: String) {
            sessions.remove(sessionId)
        }
    }

    private object FailingPARRepository : PARRepository {
        override suspend fun save(record: PARRecord) {
            error("PAR storage failed")
        }

        override suspend fun consume(requestId: String, now: Instant): PARRecord? = null
    }

    private object DuplicatePARRepository : PARRepository {
        override suspend fun save(record: PARRecord): Unit = throw DuplicatePARRecordException()
        override suspend fun consume(requestId: String, now: Instant): PARRecord? = null
    }

    private object NoopAuthorizationCodeRepository : AuthorizationCodeRepository {
        override suspend fun save(record: AuthorizationCodeRecord) = Unit
        override suspend fun consume(code: String): AuthorizationCodeRecord? = null
    }

    private object NoopPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
        override suspend fun save(record: PreAuthorizedCodeRecord) = Unit
        override suspend fun get(code: String): PreAuthorizedCodeRecord? = null
        override suspend fun consume(code: String): PreAuthorizedCodeRecord? = null
    }

    private object NoopIssuanceSessionRepository : IssuanceSessionRepository {
        override suspend fun save(session: IssuanceSession): IssuanceSession = session
        override suspend fun get(sessionId: String): IssuanceSession? = null
        override suspend fun list(): List<IssuanceSession> = emptyList()
        override suspend fun remove(sessionId: String) = Unit
        override suspend fun take(sessionId: String): IssuanceSession? = null
    }

    private companion object {
        const val EUDI_WALLET_CLIENT_ID = "eudiw-abca"
    }
}
