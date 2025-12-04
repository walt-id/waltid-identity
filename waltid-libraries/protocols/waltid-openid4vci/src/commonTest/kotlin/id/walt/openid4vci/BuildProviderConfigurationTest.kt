package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildProvider
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.OAuthError
import id.walt.openid4vci.validation.AccessRequestValidator
import id.walt.openid4vci.validation.AuthorizeRequestValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildProviderConfigurationTest {

    @Test
    fun `buildProvider registers default handlers`() {
        val config = createTestConfig(
            authorizeRequestValidator = stubAuthorizeValidator(),
            accessRequestValidator = stubAccessValidator(),
        )

        val provider = buildProvider(config)
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

        val provider = buildProvider(config)
        val result = provider.createAuthorizeRequest(emptyMap())
        assertTrue(result is AuthorizeRequestResult.Failure)
        assertEquals("invalid_client", result.error.error)
    }

    private fun stubAuthorizeValidator(): AuthorizeRequestValidator = AuthorizeRequestValidator {
        AuthorizeRequestResult.Failure(OAuthError("unsupported_response_type"))
    }

    private fun stubAccessValidator(): AccessRequestValidator = AccessRequestValidator { _, _ ->
        id.walt.openid4vci.core.AccessRequestResult.Failure(OAuthError("unsupported_grant_type"))
    }
}
