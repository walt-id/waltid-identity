package id.walt.issuer2.notifications

import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.ktornotifications.SseNotifier
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.Session
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessTokenResponseHttp
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.TokenResponseOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class TokenEventTest {

    @Test
    fun `missing grant type publishes one generic uncorrelated failure`() = runTest {
        val requestId = "token-missing-grant-type"
        val service = protocolService { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Failure(
                    OAuthError(OAuthErrorCodes.INVALID_REQUEST, "Missing grant_type"),
                )
            }
        }

        val (response, events) = service.processTokenAndCaptureEvents(
            parameters = emptyMap(),
            requestId = requestId,
        )

        assertEquals(400, response.status)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, response.payload["error"]?.jsonPrimitive?.content)
        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, event.error)
        assertEquals("Missing grant_type", event.errorDescription)
    }

    @Test
    fun `known grant request validation failure publishes a grant-specific uncorrelated failure`() = runTest {
        val requestId = "token-dpop-failure"
        val service = protocolService { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Failure(
                    OAuthError(OAuthErrorCodes.INVALID_DPOP_PROOF, "Invalid DPoP proof"),
                )
            }
        }

        val (response, events) = service.processTokenAndCaptureEvents(
            parameters = tokenParameters(GrantType.PreAuthorizedCode),
            requestId = requestId,
        )

        assertEquals(400, response.status)
        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_DPOP_PROOF, event.error)
        assertEquals("Invalid DPoP proof", event.errorDescription)
    }

    @Test
    fun `unknown authorization code publishes an uncorrelated grant failure`() = runTest {
        val requestId = "token-unknown-code"
        val request = tokenRequest(GrantType.AuthorizationCode)
        val service = protocolService { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Success(request)

                override suspend fun createAccessTokenResponse(
                    request: AccessTokenRequest,
                    options: TokenResponseOptions,
                ) = AccessTokenResponseResult.Failure(
                    request.withSession(null),
                    OAuthError(OAuthErrorCodes.INVALID_GRANT, "Authorization code is invalid or has already been used"),
                )
            }
        }

        val (_, events) = service.processTokenAndCaptureEvents(
            parameters = tokenParameters(GrantType.AuthorizationCode),
            requestId = requestId,
        )

        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_AUTHORIZATION_CODE_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(true, event.session.isEmpty())
        assertEquals(OAuthErrorCodes.INVALID_GRANT, event.error)
    }

    @Test
    fun `failure after grant resolution publishes a correlated event`() = runTest {
        val session = issuanceSession("token-tx-code-failure")
        val request = tokenRequest(GrantType.PreAuthorizedCode)
        val service = protocolService(session) { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Success(request)

                override suspend fun createAccessTokenResponse(
                    request: AccessTokenRequest,
                    options: TokenResponseOptions,
                ) = AccessTokenResponseResult.Failure(
                    request.withSession(DefaultSession(subject = session.sessionId)),
                    OAuthError(OAuthErrorCodes.INVALID_GRANT, "tx_code is invalid"),
                )
            }
        }

        val (_, events) = service.processTokenAndCaptureEvents(
            parameters = tokenParameters(GrantType.PreAuthorizedCode),
            requestId = "token-tx-code-request",
        )

        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_PRE_AUTHORIZED_CODE_FAILED.value, event.event)
        assertEquals(session.sessionId, event.target)
        assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
        assertEquals(OAuthErrorCodes.INVALID_GRANT, event.error)
        assertEquals("tx_code is invalid", event.errorDescription)
    }

    @Test
    fun `authorization code success uses the validated grant even without a handled-grant marker`() = runTest {
        val session = issuanceSession("token-authorization-code-success")
        val unresolvedRequest = tokenRequest(GrantType.AuthorizationCode)
        val service = protocolService(session) { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Success(unresolvedRequest)

                override suspend fun createAccessTokenResponse(
                    request: AccessTokenRequest,
                    options: TokenResponseOptions,
                ) = AccessTokenResponseResult.Success(
                    request = request.withSession(DefaultSession(subject = session.sessionId)),
                    response = AccessTokenResponse(accessToken = "access-token"),
                )
            }
        }

        val (response, events) = service.processTokenAndCaptureEvents(
            parameters = tokenParameters(GrantType.AuthorizationCode),
            requestId = "token-authorization-code-request",
        )

        assertEquals(200, response.status)
        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_AUTHORIZATION_CODE_SUCCEEDED.value, event.event)
        assertEquals(session.sessionId, event.target)
        assertEquals(session.sessionId, event.session["sessionId"]?.jsonPrimitive?.content)
        assertNull(event.error)
        assertNull(event.errorDescription)
    }

    @Test
    fun `provider exception publishes one sanitized token failure`() = runTest {
        val requestId = "token-provider-exception"
        val request = tokenRequest(GrantType.RefreshToken)
        val service = protocolService { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ) = AccessTokenRequestResult.Success(request)

                override suspend fun createAccessTokenResponse(
                    request: AccessTokenRequest,
                    options: TokenResponseOptions,
                ): AccessTokenResponseResult = error("Sensitive token issuer failure")
            }
        }

        val (response, events) = service.processTokenAndCaptureEvents(
            parameters = tokenParameters(GrantType.RefreshToken),
            requestId = requestId,
        )

        assertEquals(500, response.status)
        assertEquals("Token request processing failed", response.payload["error_description"]?.jsonPrimitive?.content)
        val event = events.single()
        assertEquals(IssuanceSessionEvent.TOKEN_REQUEST_REFRESH_TOKEN_FAILED.value, event.event)
        assertEquals(requestId, event.target)
        assertEquals(OAuthErrorCodes.SERVER_ERROR, event.error)
        assertEquals("Token request processing failed", event.errorDescription)
    }

    @Test
    fun `cancellation publishes no token event`() = runTest {
        val requestId = "token-cancelled"
        val service = protocolService { delegate ->
            object : OAuth2Provider by delegate {
                override suspend fun createAccessTokenRequest(
                    parameters: Map<String, List<String>>,
                    headers: Map<String, List<String>>,
                    session: Session?,
                    tokenEndpointUri: String?,
                ): AccessTokenRequestResult = throw CancellationException("cancelled")
            }
        }
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)

        assertFailsWith<CancellationException> {
            service.processTokenRequest(
                parameters = tokenParameters(GrantType.PreAuthorizedCode),
                requestId = requestId,
            )
        }
        assertEquals(emptyList(), events.replayCache.filter { it.requestId == requestId })
    }

    private fun tokenParameters(grantType: GrantType): Map<String, List<String>> =
        mapOf("grant_type" to listOf(grantType.value))

    private fun tokenRequest(grantType: GrantType): DefaultAccessTokenRequest =
        DefaultAccessTokenRequest(
            client = DefaultClient(
                id = "wallet-client",
                redirectUris = emptyList(),
                grantTypes = setOf(grantType.value),
                responseTypes = emptySet(),
            ),
            grantTypes = setOf(grantType.value),
            requestForm = tokenParameters(grantType),
        )

    private fun issuanceSession(sessionId: String) = IssuanceSession(
        sessionId = sessionId,
        profileId = "test-profile",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = JsonObject(emptyMap()),
        credentialData = JsonObject(emptyMap()),
        expiresAt = Clock.System.now() + 1.hours,
    )

    private suspend fun OpenId4VciProtocolService.processTokenAndCaptureEvents(
        parameters: Map<String, List<String>>,
        requestId: String,
    ): Pair<AccessTokenResponseHttp, List<KtorSessionUpdate>> {
        val events = SseNotifier.getSseFlow(IssuanceNotificationService.ISSUER_EVENT_STREAM_TARGET)
        val response = processTokenRequest(parameters = parameters, requestId = requestId)
        return response to events.replayCache.filter { it.requestId == requestId }
    }

    private fun protocolService(
        vararg sessions: IssuanceSession,
        oauth2ProviderDecorator: (OAuth2Provider) -> OAuth2Provider,
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
            authorizationCodeRepository = InMemoryAuthorizationCodeRepository(),
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
}
