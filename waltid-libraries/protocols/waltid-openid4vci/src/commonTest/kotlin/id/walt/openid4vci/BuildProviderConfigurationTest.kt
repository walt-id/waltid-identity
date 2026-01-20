package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.defaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.defaultPreAuthorizedCodeRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.handlers.endpoints.authorization.AuthorizationEndpointHandlers
import id.walt.openid4vci.handlers.endpoints.token.TokenEndpointHandlers
import id.walt.openid4vci.validation.AccessTokenRequestValidator
import id.walt.openid4vci.validation.AuthorizationRequestValidator
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import id.walt.openid4vci.handlers.token.TokenEndpointHandler
import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildProviderConfigurationTest {

    @Test
    fun `buildProvider registers default handlers`() {
        val config = createTestConfig(
            authorizeRequestValidator = stubAuthorizeValidator(),
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildOAuth2Provider(config)
        assertIs<OAuth2Provider>(provider)
        assertEquals(1, config.authorizeEndpointHandlers.count())
        assertEquals(2, config.tokenEndpointHandlers.count())
    }

    @Test
    fun `buildProvider surfaces validator failures`() {
        val failingValidator = AuthorizationRequestValidator {
            AuthorizeRequestResult.Failure(OAuthError("invalid_client"))
        }
        val config = createTestConfig(
            authorizeRequestValidator = failingValidator,
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildOAuth2Provider(config)
        val result = provider.createAuthorizeRequest(emptyMap<String, List<String>>())
        assertTrue(result is AuthorizeRequestResult.Failure)
        assertEquals("invalid_client", result.error.error)
    }

    @Test
    fun `buildProvider rejects duplicate grant handlers should fail`() {
        val authorizationCodeRepository = defaultAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = defaultPreAuthorizedCodeRepository()

        assertFailsWith<IllegalStateException> {
            val duplicateGrantHandlerA = DuplicateGrantHandler()
            val duplicateGrantHandlerB = DuplicateGrantHandler()

            val config = OAuth2ProviderConfig(
                authorizeRequestValidator = DefaultAuthorizationRequestValidator(),
                accessRequestValidator = DefaultAccessTokenRequestValidator(),
                authorizeEndpointHandlers = AuthorizationEndpointHandlers(),
                tokenEndpointHandlers = TokenEndpointHandlers().apply {
                    appendForGrant(GrantType.fromValue("custom_grant"), duplicateGrantHandlerA)
                    appendForGrant(GrantType.fromValue("custom_grant"), duplicateGrantHandlerB)
                },
                authorizationCodeRepository = authorizationCodeRepository,
                preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
                tokenService = StubTokenService(),
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
        val authorizationCodeRepository = defaultAuthorizationCodeRepository()
        val preAuthorizedCodeRepository = defaultPreAuthorizedCodeRepository()

        val config = OAuth2ProviderConfig(
            authorizeRequestValidator = DefaultAuthorizationRequestValidator(),
            accessRequestValidator = DefaultAccessTokenRequestValidator(),
            authorizeEndpointHandlers = AuthorizationEndpointHandlers(),
            tokenEndpointHandlers = TokenEndpointHandlers().apply {
                appendForGrant(GrantType.Custom("custom_grant"), CustomGrantHandler())
            },
            authorizationCodeRepository = authorizationCodeRepository,
            preAuthorizedCodeRepository = preAuthorizedCodeRepository,
            preAuthorizedCodeIssuer = DefaultPreAuthorizedCodeIssuer(preAuthorizedCodeRepository),
            tokenService = StubTokenService(),
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
        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessResponseResult =
            AccessResponseResult.Failure(OAuthError("unsupported_grant_type"))
    }

    private class CustomGrantHandler : TokenEndpointHandler {
        override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
            request.grantTypes.contains("custom_grant")
        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): AccessResponseResult =
            AccessResponseResult.Success(request, AccessTokenResponse(accessToken = "custom"))
    }

    private fun stubAuthorizeValidator(): AuthorizationRequestValidator = AuthorizationRequestValidator {
        AuthorizeRequestResult.Failure(OAuthError("unsupported_response_type"))
    }

    private fun stubAccessValidator(): AccessTokenRequestValidator = AccessTokenRequestValidator { _, _ ->
        AccessRequestResult.Failure(OAuthError("unsupported_grant_type"))
    }
}
