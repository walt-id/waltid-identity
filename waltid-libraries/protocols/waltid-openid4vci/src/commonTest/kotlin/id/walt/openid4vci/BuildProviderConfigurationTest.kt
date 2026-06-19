package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.InMemoryAuthorizationCodeRepository
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.preauthorized.InMemoryPreAuthorizedCodeRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.repository.refresh.defaultRefreshTokenRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.par.PushedAuthorizationEndpointHandler
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultCredentialRequestValidator
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandler
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildProviderConfigurationTest {

    @Test
    fun `buildProvider registers default handlers`() {
        val config = createTestConfig(
            authorizationRequestValidator = stubAuthorizeValidator(),
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildOAuth2Provider(config)
        assertIs<OAuth2Provider>(provider)
        assertEquals(1, config.authorizationEndpointHandlers.count())
        assertEquals(3, config.tokenEndpointHandlers.count())
    }

    @Test
    fun `buildProvider registers default PAR handler when PAR is configured`() {
        val config = createTestConfig(
            authorizationRequestValidator = stubAuthorizeValidator(),
            accessRequestValidator = stubAccessValidator(),
        ).copy(
            pushedAuthorizationConfig = PushedAuthorizationConfig(
                repository = InMemoryPARRepository(),
            )
        )

        val provider = buildOAuth2Provider(config)
        assertIs<OAuth2Provider>(provider)
        assertEquals(1, config.pushedAuthorizationEndpointHandlers.count())
    }

    @Test
    fun `buildProvider rejects PAR handlers without PAR configuration`() {
        val config = createTestConfig().apply {
            pushedAuthorizationEndpointHandlers.append(NoopPushedAuthorizationHandler)
        }

        val failure = assertFailsWith<IllegalStateException> {
            buildOAuth2Provider(config)
        }

        assertEquals("PAR endpoint handlers require pushedAuthorizationConfig", failure.message)
    }

    @Test
    fun `buildProvider rejects PAR configuration without handlers`() {
        val config = createTestConfig().copy(
            pushedAuthorizationConfig = PushedAuthorizationConfig(
                repository = InMemoryPARRepository(),
            )
        )

        val failure = assertFailsWith<IllegalStateException> {
            buildOAuth2Provider(
                config = config,
                includePushedAuthorizationDefaultHandlers = false,
            )
        }

        assertEquals("PAR is configured but no pushed authorization endpoint handler is registered", failure.message)
    }

    @Test
    fun `buildProvider allows custom PAR handler with PAR configuration`() {
        val config = createTestConfig().copy(
            pushedAuthorizationConfig = PushedAuthorizationConfig(
                repository = InMemoryPARRepository(),
            )
        ).apply {
            pushedAuthorizationEndpointHandlers.append(NoopPushedAuthorizationHandler)
        }

        val provider = buildOAuth2Provider(
            config = config,
            includePushedAuthorizationDefaultHandlers = false,
        )

        assertIs<OAuth2Provider>(provider)
        assertEquals(1, config.pushedAuthorizationEndpointHandlers.count())
    }

    @Test
    fun `buildProvider surfaces validator failures`() = runTest {
        val failingValidator = AuthorizationRequestValidator {
            AuthorizationRequestResult.Failure(OAuthError("invalid_client"))
        }
        val config = createTestConfig(
            authorizationRequestValidator = failingValidator,
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildOAuth2Provider(config)
        val result = provider.createAuthorizationRequest(emptyMap())
        assertTrue(result is AuthorizationRequestResult.Failure)
        assertEquals("invalid_client", result.error.error)
    }

    @Test
    fun `writeAuthorizationError without request returns bad request`() {
        val provider = buildOAuth2Provider(createTestConfig())

        val response = provider.writeAuthorizationError(OAuthError("invalid_request", "Missing response_type"))

        assertEquals(400, response.status)
        assertEquals(null, response.redirectUri)
        assertEquals("Missing response_type", response.body)
    }

    @Test
    fun `writeAccessTokenResponse includes no-store headers`() {
        val provider = buildOAuth2Provider(createTestConfig())
        val request = DefaultAccessTokenRequest(
            client = DefaultClient(
                id = "client-123",
                redirectUris = emptyList(),
                grantTypes = setOf(GrantType.RefreshToken.value),
                responseTypes = emptySet(),
            ),
            grantTypes = setOf(GrantType.RefreshToken.value),
        )

        val response = provider.writeAccessTokenResponse(
            request = request,
            response = AccessTokenResponse(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                scope = "openid email",
            ),
        )

        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals("no-cache", response.headers["Pragma"])
        assertEquals("refresh-token", response.payload["refresh_token"]?.jsonPrimitive?.content)
        assertEquals("openid email", response.payload["scope"]?.jsonPrimitive?.content)
    }

    @Test
    fun `writeAccessTokenError includes no-store headers`() {
        val provider = buildOAuth2Provider(createTestConfig())

        val response = provider.writeAccessTokenError(OAuthError("invalid_request"))

        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals("no-cache", response.headers["Pragma"])
    }

    @Test
    fun `buildProvider rejects duplicate grant handlers should fail`() {
        val authorizationCodeRepository = InMemoryAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository()

        assertFailsWith<IllegalStateException> {
            val duplicateGrantHandlerA = DuplicateGrantHandler()
            val duplicateGrantHandlerB = DuplicateGrantHandler()

            val config = OAuth2ProviderConfig(
                authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
                accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
                authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
                tokenEndpointHandlers = TokenEndpointHandlers().apply {
                    appendForGrant(GrantType.fromValue("custom_grant"), duplicateGrantHandlerA)
                    appendForGrant(GrantType.fromValue("custom_grant"), duplicateGrantHandlerB)
                },
                authorizationCodeRepository = authorizationCodeRepository,
                preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
                accessTokenIssuer = StubTokenIssuer(),
                refreshTokenIssuer = TestRefreshTokenIssuer(),
                refreshTokenVerifier = TestRefreshTokenIssuer(),
                refreshTokenRepository = defaultRefreshTokenRepository(),
                credentialRequestValidator = DefaultCredentialRequestValidator(),
                credentialEndpointHandlers = CredentialEndpointHandlers()
            )

            buildOAuth2Provider(
                config = config,
                includeAuthorizationCodeDefaultHandlers = false,
                includePreAuthorizedCodeDefaultHandlers = false,
            )
        }
    }

    @Test
    fun `buildProvider allows custom grant handlers`() {
        val authorizationCodeRepository = InMemoryAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = InMemoryPreAuthorizedCodeRepository()

        val config = OAuth2ProviderConfig(
            authorizationRequestValidator = DefaultAuthorizationRequestValidator(),
            accessTokenRequestValidator = DefaultAccessTokenRequestValidator(),
            authorizationEndpointHandlers = AuthorizationEndpointHandlers(),
            tokenEndpointHandlers = TokenEndpointHandlers().apply {
                appendForGrant(GrantType.Custom("custom_grant"), CustomGrantHandler())
            },
            authorizationCodeRepository = authorizationCodeRepository,
            preAuthorizedCodeRepository = preAuthorizedCodeRepository,
            preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
            accessTokenIssuer = StubTokenIssuer(),
            refreshTokenIssuer = TestRefreshTokenIssuer(),
            refreshTokenVerifier = TestRefreshTokenIssuer(),
            refreshTokenRepository = defaultRefreshTokenRepository(),
            credentialRequestValidator = DefaultCredentialRequestValidator(),
            credentialEndpointHandlers = CredentialEndpointHandlers()
        )

        assertIs<OAuth2Provider>(
            buildOAuth2Provider(
                config = config,
                includeAuthorizationCodeDefaultHandlers = false,
                includePreAuthorizedCodeDefaultHandlers = false,
            )
        )
    }

    private class DuplicateGrantHandler : TokenEndpointHandler {
        override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean = true
        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessTokenResponseResult =
            AccessTokenResponseResult.Failure(OAuthError("unsupported_grant_type"))
    }

    private class CustomGrantHandler : TokenEndpointHandler {
        override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
            request.grantTypes.contains("custom_grant")

        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessTokenResponseResult =
            AccessTokenResponseResult.Success(request, AccessTokenResponse(accessToken = "custom"))
    }

    private object NoopPushedAuthorizationHandler : PushedAuthorizationEndpointHandler {
        override suspend fun handlePushedAuthorizationEndpointRequest(
            authorizationRequest: AuthorizationRequest,
            clientAuthentication: Map<String, String>,
        ): PushedAuthorizationResponseResult =
            PushedAuthorizationResponseResult.Failure(OAuthError("server_error"))
    }

    private fun stubAuthorizeValidator(): AuthorizationRequestValidator = AuthorizationRequestValidator {
        AuthorizationRequestResult.Failure(OAuthError("unsupported_response_type"))
    }

    private fun stubAccessValidator(): AccessTokenRequestValidator = AccessTokenRequestValidator { _, _ ->
        AccessTokenRequestResult.Failure(OAuthError("unsupported_grant_type"))
    }
}