package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AccessResponseResult
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.request.AccessTokenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderAuthorizationOnlyFlowTest {

    @OptIn( ExperimentalCoroutinesApi::class)
    @Test
    fun `provider supporting only authorization code flow succeeds and rejects pre-authorized grant`() = runTest {
        val config = createTestConfig()
        val issuerId = "test-issuer"

        val provider = buildOAuth2Provider(
            config = config,
            includeAuthorizationCodeDefaultHandlers = true,
            includePreAuthorizedCodeDefaultHandlers = false,
        )

        // 1) Parse the authorize request received from the wallet.
        val authorizeRequestResult = provider.createAuthorizeRequest(
            mapOf(
                "response_type" to "code",
                "client_id" to "demo-client",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
                "scope" to "openid name credential",
            ),
        )
        assertTrue(authorizeRequestResult.isSuccess())
        val authorizeRequest = (authorizeRequestResult as AuthorizeRequestResult.Success).request.also {
            it.setIssuerId(issuerId)
        }

        // Client constructs its session object after authenticating/authorizing the user.
        val session = DefaultSession(subject = "demo-subject")

        // 2) After authenticating the user, mint the authorize response using the caller-provided session.
        val authorizeResponse = provider.createAuthorizeResponse(authorizeRequest, session)
        assertTrue(authorizeResponse.isSuccess())
        val response = (authorizeResponse as AuthorizeResponseResult.Success).response
        val code = response.parameters.getValue("code")
        // (Optional) Convert the authorize response into an HTTP response via writeAuthorizeResponse/writeAuthorizeError.

        // 3) Parse the token request from the wallet, supplying a fresh session container.
        val accessResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GrantType.AuthorizationCode.value,
                "client_id" to "demo-client",
                "code" to code,
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
        )
        assertTrue(accessResult.isSuccess())
        val accessRequest = (accessResult as AccessRequestResult.Success).request.also {
            it.setIssuerId(issuerId)
        }
        // 4) Produce the token response based on the authorization code session.
        val accessResponse = provider.createAccessResponse(accessRequest)
        assertTrue(accessResponse.isSuccess())
        val tokenResponse = (accessResponse as AccessResponseResult.Success).response
        assertTrue(tokenResponse.accessToken.isNotBlank())

        val preAccessRequestResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GrantType.PreAuthorizedCode.value,
                "pre-authorized_code" to "pre-code",
            ),
        )

        when (preAccessRequestResult) {
            is AccessRequestResult.Success -> {
                val requestWithIssuer = preAccessRequestResult.request.also { it.setIssuerId(issuerId) }
                val preAccessResponse = provider.createAccessResponse(requestWithIssuer)
                val failure = preAccessResponse as? AccessResponseResult.Failure
                    ?: error("Expected pre-authorized flow to be rejected at token endpoint")
                assertEquals("unsupported_grant_type", failure.error.error)
            }

            is AccessRequestResult.Failure -> {
                assertEquals("unsupported_grant_type", preAccessRequestResult.error.error)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `authorize response fails when session has no subject`() = runTest {
        val config = createTestConfig()
        val provider = buildOAuth2Provider(
            config = config,
            includeAuthorizationCodeDefaultHandlers = true,
            includePreAuthorizedCodeDefaultHandlers = false,
        )

        val authorizeRequestResult = provider.createAuthorizeRequest(
            mapOf(
                "response_type" to "code",
                "client_id" to "demo-client",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
        )

        assertTrue(authorizeRequestResult.isSuccess())
        val authorizeRequest = (authorizeRequestResult as AuthorizeRequestResult.Success).request

        val authorizeResponse = provider.createAuthorizeResponse(
            authorizeRequest,
            DefaultSession(subject = "")
        )

        assertTrue(authorizeResponse is AuthorizeResponseResult.Failure)

        val authorizeResponse2 = provider.createAuthorizeResponse(
            authorizeRequest,
            DefaultSession()
        )

        assertTrue(authorizeResponse2 is AuthorizeResponseResult.Failure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `access request rejects unsupported grant_type`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())
        val accessRequestResult = provider.createAccessRequest(
            mapOf("grant_type" to "client_credentials"),
        )
        assertTrue(accessRequestResult is AccessRequestResult.Failure)
        assertEquals("unsupported_grant_type", accessRequestResult.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `access response rejects when no handler supports grant_type`() = runTest {
        val provider = buildOAuth2Provider(
            config = createTestConfig(),
            includeAuthorizationCodeDefaultHandlers = false,
            includePreAuthorizedCodeDefaultHandlers = false,
        )
        val request = AccessTokenRequest().apply { appendGrantType(GrantType.AuthorizationCode.value) }
        val response = provider.createAccessResponse(request)
        assertTrue(response is AccessResponseResult.Failure)
        assertEquals("unsupported_grant_type", response.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `authorize response rejects when no handler supports response_type`() = runTest {
        val provider = buildOAuth2Provider(
            config = createTestConfig(),
            includeAuthorizationCodeDefaultHandlers = false,
            includePreAuthorizedCodeDefaultHandlers = false,
        )
        val authorizeRequestResult = provider.createAuthorizeRequest(
            mapOf(
                "response_type" to "code",
                "client_id" to "demo-client",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
        )
        assertTrue(authorizeRequestResult is AuthorizeRequestResult.Success)
        val authorizeResponse = provider.createAuthorizeResponse(
            authorizeRequestResult.request,
            DefaultSession(subject = "sub"),
        )
        assertTrue(authorizeResponse is AuthorizeResponseResult.Failure)
        assertEquals("unsupported_response_type", authorizeResponse.error.error)
    }
}
