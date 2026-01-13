package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.OAuthError
import id.walt.openid4vci.core.OAuth2ProviderConfig
import id.walt.openid4vci.preauthorized.DefaultPreAuthorizedCodeIssuer
import id.walt.openid4vci.repository.authorization.defaultAuthorizationCodeRepository
import id.walt.openid4vci.repository.preauthorized.defaultPreAuthorizedCodeRepository
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.validation.AccessRequestValidator
import id.walt.openid4vci.validation.AuthorizeRequestValidator
import id.walt.openid4vci.validation.DefaultAccessRequestValidator
import id.walt.openid4vci.validation.DefaultAuthorizeRequestValidator
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
        val failingValidator = AuthorizeRequestValidator {
            AuthorizeRequestResult.Failure(OAuthError("invalid_client"))
        }
        val config = createTestConfig(
            authorizeRequestValidator = failingValidator,
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildOAuth2Provider(config)
        val result = provider.createAuthorizeRequest(emptyMap())
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
                authorizeRequestValidator = DefaultAuthorizeRequestValidator(),
                accessRequestValidator = DefaultAccessRequestValidator(),
                authorizeEndpointHandlers = AuthorizeEndpointHandlers(),
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
            authorizeRequestValidator = DefaultAuthorizeRequestValidator(),
            accessRequestValidator = DefaultAccessRequestValidator(),
            authorizeEndpointHandlers = AuthorizeEndpointHandlers(),
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
        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult =
            TokenEndpointResult.Failure("unsupported_grant_type")
    }

    private class CustomGrantHandler : TokenEndpointHandler {
        override fun canHandleTokenEndpointRequest(request: AccessTokenRequest): Boolean =
            request.getGrantTypes().contains("custom_grant")
        override suspend fun handleTokenEndpointRequest(request: AccessTokenRequest): TokenEndpointResult =
            TokenEndpointResult.Success(accessToken = "custom")
    }

    private fun stubAuthorizeValidator(): AuthorizeRequestValidator = AuthorizeRequestValidator {
        AuthorizeRequestResult.Failure(OAuthError("unsupported_response_type"))
    }

    private fun stubAccessValidator(): AccessRequestValidator = AccessRequestValidator { _, _ ->
        AccessRequestResult.Failure(OAuthError("unsupported_grant_type"))
    }
}
