package id.walt.openid4vci

import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizeRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
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
        val authorizeRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf("code"),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid name credential"),
            ),
        )
        assertTrue(authorizeRequestResult.isSuccess())
        val authorizeRequest = (authorizeRequestResult as AuthorizeRequestResult.Success).request
            .withIssuer(issuerId)

        // Client constructs its session object after authenticating/authorizing the user.
        val session = DefaultSession(subject = "demo-subject")

        // 2) After authenticating the user, mint the authorize response using the caller-provided session.
        val authorizeResponse = provider.createAuthorizationResponse(authorizeRequest, session)
        assertTrue(authorizeResponse.isSuccess())
        val response = (authorizeResponse as AuthorizationResponseResult.Success).response
        val code = response.code
        // (Optional) Convert the authorize response into an HTTP response via writeAuthorizeResponse/writeAuthorizeError.

        // 3) Parse the token request from the wallet, supplying a fresh session container.
        val accessResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("demo-client"),
                "code" to listOf(code),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )
        assertTrue(accessResult.isSuccess())
        val accessRequest = (accessResult as AccessTokenRequestResult.Success).request.withIssuer(issuerId)
        // 4) Produce the token response based on the authorization code session.
        val accessResponse = provider.createAccessResponse(accessRequest)
        assertTrue(accessResponse.isSuccess())
        val tokenResponse = (accessResponse as AccessResponseResult.Success).response
        assertTrue(tokenResponse.accessToken.isNotBlank())

        val preAccessTokenRequestResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf("pre-code"),
            ),
        )

        when (preAccessTokenRequestResult) {
            is AccessTokenRequestResult.Success -> {
                val requestWithIssuer = preAccessTokenRequestResult.request.withIssuer(issuerId)
                val preAccessResponse = provider.createAccessResponse(requestWithIssuer)
                val failure = preAccessResponse as? AccessResponseResult.Failure
                    ?: error("Expected pre-authorized flow to be rejected at token endpoint")
                assertEquals("unsupported_grant_type", failure.error.error)
            }

            is AccessTokenRequestResult.Failure -> {
                assertEquals("unsupported_grant_type", preAccessTokenRequestResult.error.error)
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

        val authorizeRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf("code"),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )

        assertTrue(authorizeRequestResult.isSuccess())
        val authorizeRequest = (authorizeRequestResult as AuthorizeRequestResult.Success).request

        val authorizeResponse = provider.createAuthorizationResponse(
            authorizeRequest,
            DefaultSession(subject = "")
        )

        assertTrue(authorizeResponse is AuthorizationResponseResult.Failure)

        val authorizeResponse2 = provider.createAuthorizationResponse(
            authorizeRequest,
            DefaultSession()
        )

        assertTrue(authorizeResponse2 is AuthorizationResponseResult.Failure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `access request rejects unsupported grant_type`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())
        val AccessTokenRequestResult = provider.createAccessRequest(
            mapOf("grant_type" to listOf("client_credentials")),
        )
        assertTrue(AccessTokenRequestResult is AccessTokenRequestResult.Failure)
        assertEquals("unsupported_grant_type", AccessTokenRequestResult.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `access response rejects when no handler supports grant_type`() = runTest {
        val provider = buildOAuth2Provider(
            config = createTestConfig(),
            includeAuthorizationCodeDefaultHandlers = false,
            includePreAuthorizedCodeDefaultHandlers = false,
        )

        val request = DefaultAccessTokenRequest(
            client = DefaultClient(
                id = "test-client",
                redirectUris = emptyList(),
                grantTypes = setOf(GrantType.AuthorizationCode.value),
                responseTypes = setOf("code"),
            ),
            grantTypes = mutableSetOf(GrantType.AuthorizationCode.value),
            requestForm = mutableMapOf(),
        )

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
        val authorizeRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf("code"),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )
        assertTrue(authorizeRequestResult is AuthorizeRequestResult.Success)
        val authorizeResponse = provider.createAuthorizationResponse(
            authorizeRequestResult.request,
            DefaultSession(subject = "sub"),
        )
        assertTrue(authorizeResponse is AuthorizationResponseResult.Failure)
        assertEquals("unsupported_response_type", authorizeResponse.error.error)
    }
}
