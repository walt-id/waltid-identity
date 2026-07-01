package id.walt.openid4vci

import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceMethod
import id.walt.openid4vci.clientauth.attestation.ClientAttestationConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.clientauth.attestation.ClientAttestationVerificationResult
import id.walt.openid4vci.clientauth.attestation.ClientAttestationVerifier
import id.walt.openid4vci.core.DefaultOAuth2Provider
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
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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
        assertEquals(2, config.tokenEndpointHandlers.count())
        assertEquals(0, config.pushedAuthorizationEndpointHandlers.count())
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
    fun `buildProvider uses configured client authentication methods`() = runTest {
        val clientAuthenticationMethod = RecordingClientAuthenticationMethod(
            name = ClientAuthenticationMethods.CLIENT_SECRET_POST,
        )
        val config = createTestConfig(
            accessRequestValidator = stubAccessValidator(),
        ).copy(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(clientAuthenticationMethod),
            ),
        )

        val provider = buildOAuth2Provider(config)
        val result = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("client-id"),
                "client_secret" to listOf("secret"),
            ),
        )

        assertIs<AccessTokenRequestResult.Failure>(result)
        assertEquals(1, clientAuthenticationMethod.calls)
        assertEquals(ClientAuthenticationEndpoint.TOKEN, clientAuthenticationMethod.lastEndpoint)
    }

    @Test
    fun `buildProvider registers default client attestation method when configured`() = runTest {
        val provider = assertIs<DefaultOAuth2Provider>(
            buildOAuth2Provider(
                createTestConfig().copy(
                    authorizationServerIssuer = "https://issuer.example/openid4vci",
                    pushedAuthorizationConfig = PushedAuthorizationConfig(
                        repository = InMemoryPARRepository(),
                    ),
                    clientAttestationConfig = ClientAttestationConfig(NoopClientAttestationVerifier),
                ),
            )
        )

        assertEquals(
            setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
            provider.config.clientAuthenticationServiceConfig
                .allowedMethodsByEndpoint[ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION],
        )
        assertEquals(
            setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
            provider.config.clientAuthenticationServiceConfig
                .allowedMethodsByEndpoint[ClientAuthenticationEndpoint.TOKEN],
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                mapOf(
                    "response_type" to listOf(ResponseType.CODE.value),
                    "client_id" to listOf("demo-client"),
                    "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                ),
                mapOf(
                    ClientAttestationHeaders.CLIENT_ATTESTATION to listOf("jwt"),
                ),
            )
        )

        assertEquals("invalid_client", result.error.error)
        assertEquals("Exactly one OAuth-Client-Attestation-PoP header is required", result.error.description)
    }

    @Test
    fun `buildProvider respects configured client authentication endpoint methods`() {
        val configuredAllowedMethods = mapOf(
            ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.PRIVATE_KEY_JWT),
        )
        val provider = assertIs<DefaultOAuth2Provider>(
            buildOAuth2Provider(
                createTestConfig().copy(
                    clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                        allowedMethodsByEndpoint = configuredAllowedMethods,
                    ),
                    clientAttestationConfig = ClientAttestationConfig(NoopClientAttestationVerifier),
                ),
            )
        )

        assertEquals(
            configuredAllowedMethods,
            provider.config.clientAuthenticationServiceConfig.allowedMethodsByEndpoint,
        )
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
                accessTokenService = StubTokenService(),
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
            accessTokenService = StubTokenService(),
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

    private class RecordingClientAuthenticationMethod(
        override val name: String,
    ) : ClientAuthenticationServiceMethod {
        var calls: Int = 0
            private set
        var lastEndpoint: ClientAuthenticationEndpoint? = null
            private set

        override suspend fun authenticate(
            endpoint: ClientAuthenticationEndpoint,
            parameters: Map<String, List<String>>,
            headers: Map<String, List<String>>,
            context: ClientAuthenticationContext,
        ): ClientAuthenticationResult {
            calls += 1
            lastEndpoint = endpoint
            return ClientAuthenticationResult.Authenticated(
                AuthenticatedClient(
                    id = parameters["client_id"]?.singleOrNull().orEmpty(),
                    authenticationMethod = name,
                ),
            )
        }
    }

    private object NoopPushedAuthorizationHandler : PushedAuthorizationEndpointHandler {
        override suspend fun handlePushedAuthorizationEndpointRequest(
            authorizationRequest: AuthorizationRequest,
            clientAuthentication: Map<String, String>,
        ): PushedAuthorizationResponseResult =
            PushedAuthorizationResponseResult.Failure(OAuthError("server_error"))
    }

    private object NoopClientAttestationVerifier : ClientAttestationVerifier {
        override suspend fun verifyAttestationJwt(
            jwt: String,
            header: JsonObject,
            payload: JsonObject,
        ): ClientAttestationVerificationResult =
            ClientAttestationVerificationResult.Rejected("not used")
    }

    private fun stubAuthorizeValidator(): AuthorizationRequestValidator = AuthorizationRequestValidator {
        AuthorizationRequestResult.Failure(OAuthError("unsupported_response_type"))
    }

    private fun stubAccessValidator(): AccessTokenRequestValidator = AccessTokenRequestValidator { _, _ ->
        AccessTokenRequestResult.Failure(OAuthError("unsupported_grant_type"))
    }
}
