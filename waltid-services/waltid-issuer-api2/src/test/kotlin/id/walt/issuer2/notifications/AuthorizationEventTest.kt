package id.walt.issuer2.notifications

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.domain.IssuanceRequest
import id.walt.issuer2.domain.IssuanceSessionStatus
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.issuer2.service.openid4vci.encodeExternalLoginAuthorizationParameters
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Covers authorization events across initial request processing and the external identity provider callback.
 * The callback sits behind Ktor's `auth-oauth` provider and needs a live Keycloak, so the protocol service is
 * exercised directly.
 */
class AuthorizationEventTest {

    @Test
    fun `malformed authorization request publishes an uncorrelated failure`() = runTest {
        val requestId = "authorize-validation-failure"
        val service = protocolService()
        val (response, events) = service.processAuthorizationAndCaptureEvents(
            requestId = requestId,
            parameters = validAuthorizationParameters(issuerState = "untrusted-session") - "response_type",
        )

        assertEquals(400, response.status)
        assertEquals("Missing response_type", response.body)
        val event = events.single()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("Missing response_type", event.errorDescription)
    }

    @Test
    fun `unknown issuer state publishes an uncorrelated authorization failure`() = runTest {
        val requestId = "authorize-unknown-issuer-state"
        val service = protocolService()
        val (response, events) = service.processAuthorizationAndCaptureEvents(
            requestId = requestId,
            parameters = validAuthorizationParameters(issuerState = "unknown-session"),
        )

        assertEquals(302, response.status)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, response.parameters["error"])
        assertEquals("issuer_state is invalid", response.parameters["error_description"])
        val event = events.single()
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("issuer_state is invalid", event.errorDescription)
    }

    @Test
    fun `unusable authorization sessions publish correlated failures`() = runTest {
        val sessions = listOf(
            session("pre-authorized").copy(authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED),
            session("inactive").copy(status = IssuanceSessionStatus.SUCCESSFUL),
            session("closed").copy(isClosed = true),
            session("expired").copy(expiresAt = Instant.DISTANT_PAST),
        )
        val service = protocolService(*sessions.toTypedArray())

        sessions.forEach { session ->
            val requestId = "authorize-rejected-${session.sessionId}"
            val (response, events) = service.processAuthorizationAndCaptureEvents(
                requestId = requestId,
                parameters = validAuthorizationParameters(issuerState = session.sessionId),
            )

            assertEquals(302, response.status, session.sessionId)
            assertEquals(OAuthErrorCodes.INVALID_REQUEST, response.parameters["error"], session.sessionId)
            assertEquals("issuer_state is invalid", response.parameters["error_description"], session.sessionId)
            val event = events.single()
            assertEquals(session.sessionId, event.target, session.sessionId)
            assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content, session.sessionId)
            assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error, session.sessionId)
            assertEquals("issuer_state is invalid", event.errorDescription, session.sessionId)
        }
    }

    @Test
    fun `accepted authorization request remains pending without a success event`() = runTest {
        val session = session("pending-authorization")
        val service = protocolService(session)
        val (response, events) = service.processAuthorizationAndCaptureEvents(
            requestId = "authorize-pending",
            parameters = validAuthorizationParameters(issuerState = session.sessionId),
        )

        assertEquals(302, response.status)
        val redirectUri = assertNotNull(response.redirectUri)
        assertEquals(true, redirectUri.contains("/external_login/"))
        assertEquals(emptyList(), events)
    }

    @Test
    fun `failure after session resolution publishes a correlated authorization failure`() = runTest {
        val session = session("authorization-envelope-failure")
        val service = protocolService(session)
        val (response, events) = service.processAuthorizationAndCaptureEvents(
            requestId = "authorize-envelope-failure",
            parameters = validAuthorizationParameters(issuerState = session.sessionId) +
                ("state" to listOf("x".repeat(4096))),
        )

        assertEquals(302, response.status)
        val event = events.single()
        assertEquals(session.sessionId, event.target)
        assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals(
            "External login authorization parameters exceed the maximum encoded length of 4096 characters",
            event.errorDescription,
        )
    }

    @Test
    fun `malformed external login interception publishes an uncorrelated failure`() = runTest {
        val requestId = "external-login-invalid-envelope"
        val service = protocolService()
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

        assertFailsWith<IllegalArgumentException> {
            service.processExternalLoginInterception(
                externalAuthorizationRequest = null,
                authorizationRequestEnvelope = "not-valid-base64",
                requestId = requestId,
            )
        }

        val event = events.replayCache.filter { it.requestId == requestId }.single()
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("Invalid external login authorization parameters", event.errorDescription)
    }

    @Test
    fun `external login interception failure after session resolution is correlated`() = runTest {
        val session = session("external-login-state-failure")
        val requestId = "external-login-state-failure"
        val service = protocolService(session)
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val envelope = (
            validAuthorizationParameters(issuerState = session.sessionId) +
                ("_issuer2_session_id" to listOf(session.sessionId))
            ).encodeExternalLoginAuthorizationParameters()

        assertFailsWith<IllegalArgumentException> {
            service.processExternalLoginInterception(
                externalAuthorizationRequest = "https://identity.example/authorize",
                authorizationRequestEnvelope = envelope,
                requestId = requestId,
            )
        }

        val event = events.replayCache.filter { it.requestId == requestId }.single()
        assertEquals(session.sessionId, event.target)
        assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("Missing state in external authorization request", event.errorDescription)
    }

    @Test
    fun `unusable id_token from the external provider publishes an authorization failure`() = runTest {
        val sessionId = "external-auth-session"
        val service = protocolService(session(sessionId, externalState = STATE))

        // The provider redirected back, but what it returned cannot be decoded. The user authenticated
        // successfully, so this is not a rejected wallet request - see the note on the event name.
        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = "external-id-token-failure",
            authServerState = STATE,
            idToken = "not-a-jws",
        )

        assertEquals(OAuthErrorCodes.SERVER_ERROR, response.parameters["error"])
        assertEquals(
            "Could not process the external identity token",
            response.parameters["error_description"],
        )
        val update = events.single()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED.value, update.event)
        assertEquals(sessionId, update.target)
        assertEquals(OAuthErrorCodes.SERVER_ERROR, update.error)
        assertEquals("Could not process the external identity token", update.errorDescription)
    }

    @Test
    fun `invalid external claims mapping publishes a sanitized server failure`() = runTest {
        val sessionId = "external-claims-mapping-failure"
        val service = protocolService(
            session(
                sessionId = sessionId,
                externalState = STATE,
                idTokenClaimsMapping = mapOf("$.missing" to "$.credentialSubject.id"),
            )
        )
        val idToken = JWKKey.generate(KeyType.secp256r1).signJws(
            buildJsonObject { put("sub", JsonPrimitive("jane@walt.id")) }.toString().encodeToByteArray(),
        )

        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = "external-claims-mapping-failure",
            authServerState = STATE,
            idToken = idToken,
        )

        assertEquals(OAuthErrorCodes.SERVER_ERROR, response.parameters["error"])
        assertEquals(
            "Could not map external identity claims to credential data",
            response.parameters["error_description"],
        )
        val update = events.single()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED.value, update.event)
        assertEquals(sessionId, update.target)
        assertEquals(OAuthErrorCodes.SERVER_ERROR, update.error)
        assertEquals(
            "Could not map external identity claims to credential data",
            update.errorDescription,
        )
    }

    @Test
    fun `authorization request reconstruction exception publishes a sanitized correlated failure`() = runTest {
        val sessionId = "authorization-request-reconstruction-failure"
        val service = protocolService(
            session(sessionId, externalState = STATE),
            oauth2ProviderDecorator = { delegate ->
                object : OAuth2Provider by delegate {
                    override suspend fun createAuthorizationRequest(
                        parameters: Map<String, List<String>>,
                    ): AuthorizationRequestResult = error("Sensitive provider failure")
                }
            },
        )

        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = "authorization-request-reconstruction-failure",
            authServerState = STATE,
            idToken = "not-used",
        )

        assertEquals(400, response.status)
        assertEquals("Could not restore the authorization request", response.body)
        val update = events.single()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_FAILED.value, update.event)
        assertEquals(sessionId, update.target)
        assertEquals(OAuthErrorCodes.SERVER_ERROR, update.error)
        assertEquals("Could not restore the authorization request", update.errorDescription)
    }

    @Test
    fun `completed external authentication publishes successful authorization request`() = runTest {
        val sessionId = "authorized-session"
        val service = protocolService(session(sessionId, externalState = STATE))

        val idToken = JWKKey.generate(KeyType.secp256r1).signJws(
            buildJsonObject { put("sub", JsonPrimitive("jane@walt.id")) }.toString().encodeToByteArray(),
        )
        val (_, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = "external-authorization-success",
            authServerState = STATE,
            idToken = idToken,
        )

        val event = events.single()
        assertEquals(IssuanceSessionEvent.AUTHORIZATION_REQUEST_SUCCEEDED.value, event.event)
        assertEquals(sessionId, event.target)
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `authorization code storage failure remains correlated and retryable`() = runTest {
        val sessionId = "authorization-code-storage-failure"
        val service = protocolService(
            session(sessionId, externalState = STATE),
            authorizationCodeRepository = FailingAuthorizationCodeRepository,
        )
        val idToken = JWKKey.generate(KeyType.secp256r1).signJws(
            buildJsonObject { put("sub", JsonPrimitive("jane@walt.id")) }.toString().encodeToByteArray(),
        )

        repeat(2) { attempt ->
            val requestId = "authorization-code-storage-failure-$attempt"
            val (response, events) = service.processExternalCallbackAndCaptureEvents(
                requestId = requestId,
                authServerState = STATE,
                idToken = idToken,
            )

            assertEquals(302, response.status)
            assertEquals(OAuthErrorCodes.SERVER_ERROR, response.parameters["error"])
            val event = events.single()
            assertEquals(sessionId, event.target)
            assertEquals(sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
            assertEquals(OAuthErrorCodes.SERVER_ERROR, event.error)
            assertEquals("Authorization code storage failed", event.errorDescription)
        }
    }

    @Test
    fun `closed session cannot complete the external authorization callback`() = runTest {
        val session = session("closed-callback-session", externalState = STATE).copy(isClosed = true)
        val requestId = "closed-callback-session"
        val service = protocolService(session)
        val (_, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = requestId,
            authServerState = STATE,
            idToken = "not-used",
        )

        val event = events.single()
        assertEquals(session.sessionId, event.target)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("issuer_state is invalid", event.errorDescription)
    }

    @Test
    fun `unknown external state publishes an uncorrelated failure`() = runTest {
        val sessionId = "unmatched-session"
        val service = protocolService(session(sessionId, externalState = STATE))
        val requestId = "unknown-external-state"
        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = requestId,
            authServerState = "some-other-state",
            idToken = "not-a-jws",
        )

        assertEquals(400, response.status)
        val event = events.single()
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("No issuance session found for external OAuth state", event.errorDescription)
    }

    @Test
    fun `missing callback state publishes an uncorrelated failure`() = runTest {
        val requestId = "missing-callback-state"
        val service = protocolService()
        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = requestId,
            authServerState = null,
            idToken = "not-used",
        )

        assertEquals(400, response.status)
        val event = events.single()
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("state parameter is missing in the callback request", event.errorDescription)
    }

    @Test
    fun `missing id token publishes a correlated callback failure`() = runTest {
        val session = session("missing-id-token", externalState = STATE)
        val requestId = "missing-id-token"
        val service = protocolService(session)
        val (response, events) = service.processExternalCallbackAndCaptureEvents(
            requestId = requestId,
            authServerState = STATE,
            idToken = null,
        )

        assertEquals(302, response.status)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, response.parameters["error"])
        val event = events.single()
        assertEquals(session.sessionId, event.target)
        assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("id_token is missing in the callback request", event.errorDescription)
    }

    private fun session(
        sessionId: String,
        externalState: String? = null,
        idTokenClaimsMapping: Map<String, String>? = null,
    ) = IssuanceSession(
        sessionId = sessionId,
        profileId = "test-profile",
        authenticationMethod = AuthenticationMethod.AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = JsonObject(emptyMap()),
        credentialData = buildJsonObject { },
        idTokenClaimsMapping = idTokenClaimsMapping,
        expiresAt = Clock.System.now() + 1.hours,
        externalAuthorizationState = externalState,
        authorizationRequest = mapOf(
            "response_type" to listOf("code"),
            "client_id" to listOf("demo-client"),
            "redirect_uri" to listOf("https://wallet.example/callback"),
        ),
    )

    private fun validAuthorizationParameters(issuerState: String): Map<String, List<String>> =
        mapOf(
            "response_type" to listOf("code"),
            "client_id" to listOf("demo-client"),
            "redirect_uri" to listOf("https://wallet.example/callback"),
            "state" to listOf("wallet-state"),
            "issuer_state" to listOf(issuerState),
        )

    private suspend fun OpenId4VciProtocolService.processAuthorizationAndCaptureEvents(
        requestId: String,
        parameters: Map<String, List<String>>,
    ): Pair<AuthorizationResponseHttp, List<KtorSessionUpdate>> {
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val response = processAuthorizeRequest(parameters, requestId)
        return response to events.replayCache.filter { it.requestId == requestId }
    }

    private suspend fun OpenId4VciProtocolService.processExternalCallbackAndCaptureEvents(
        requestId: String,
        authServerState: String?,
        idToken: String?,
    ): Pair<AuthorizationResponseHttp, List<KtorSessionUpdate>> {
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val response = processExternalAuthorizationCallback(authServerState, idToken, requestId)
        return response to events.replayCache.filter { it.requestId == requestId }
    }

    private fun protocolService(
        vararg sessions: IssuanceSession,
        authorizationCodeRepository: AuthorizationCodeRepository = InMemoryAuthorizationCodeRepository(),
        oauth2ProviderDecorator: (OAuth2Provider) -> OAuth2Provider = { it },
    ): OpenId4VciProtocolService {
        val serviceConfig = Issuer2ServiceConfig(baseUrl = "http://localhost")
        val metadataConfig = Issuer2MetadataConfig()
        val profileService = CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(),
            metadataConfig = metadataConfig,
        )
        val sessionService = IssuanceSessionService(InMemorySessionRepository(sessions.toList()))
        val module = OpenId4VciModule.create(
            config = serviceConfig,
            authorizationCodeRepository = authorizationCodeRepository,
            preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository(),
            parRepository = InMemoryPARRepository(),
            refreshTokenRepository = InMemoryRefreshTokenRepository(),
        )
        val metadataService = MetadataService(
            serviceConfig = serviceConfig,
            metadataConfig = metadataConfig,
            profileService = profileService,
            sessionService = sessionService,
            preAuthorizedGrantAnonymousAccessSupported = module.preAuthorizedCodeIssuer.anonymousAccessSupported,
        )
        return OpenId4VciProtocolService(
            oauth2Provider = oauth2ProviderDecorator(module.oauth2Provider),
            sessionService = sessionService,
            profileService = profileService,
            metadataService = metadataService,
            notificationService = IssuanceNotificationService(),
            credentialNonceService = module.credentialNonceService,
        )
    }

    private class InMemorySessionRepository(initial: List<IssuanceSession>) : IssuanceSessionRepository {
        private val sessions = initial.associateBy { it.sessionId }.toMutableMap()

        override suspend fun save(session: IssuanceSession): IssuanceSession =
            session.also { sessions[it.sessionId] = it }

        override suspend fun get(sessionId: String): IssuanceSession? = sessions[sessionId]
        override suspend fun list(): List<IssuanceSession> = sessions.values.toList()
        override suspend fun remove(sessionId: String) {
            sessions.remove(sessionId)
        }
    }

    private object FailingAuthorizationCodeRepository : AuthorizationCodeRepository {
        override suspend fun save(record: AuthorizationCodeRecord) {
            error("Authorization code storage failed")
        }

        override suspend fun consume(code: String): AuthorizationCodeRecord? = null
    }

    private companion object {
        const val STATE = "external-auth-state"
    }
}
