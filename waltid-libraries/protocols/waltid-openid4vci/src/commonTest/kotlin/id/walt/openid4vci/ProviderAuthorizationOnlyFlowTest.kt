package id.walt.openid4vci

import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.authorization.OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import id.walt.openid4vci.responses.token.TokenResponseOptions
import id.walt.openid4vci.tokens.AccessTokenService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        val authorizationRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid name credential"),
            ),
        )
        assertTrue(authorizationRequestResult.isSuccess())
        val authorizeRequest = (authorizationRequestResult as AuthorizationRequestResult.Success).request
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
        val accessResult = provider.createAccessTokenRequest(
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
        val accessResponse = provider.createAccessTokenResponse(accessRequest)
        assertTrue(accessResponse.isSuccess())
        val tokenResponse = (accessResponse as AccessTokenResponseResult.Success).response
        assertTrue(tokenResponse.accessToken.isNotBlank())

        val preAccessTokenRequestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf("pre-code"),
            ),
        )

        when (preAccessTokenRequestResult) {
            is AccessTokenRequestResult.Success -> {
                val requestWithIssuer = preAccessTokenRequestResult.request.withIssuer(issuerId)
                val preAccessResponse = provider.createAccessTokenResponse(requestWithIssuer)
                val failure = preAccessResponse as? AccessTokenResponseResult.Failure
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
    fun `authorization code access token includes issuance session id from session custom attributes`() = runTest {
        val tokenService = CapturingTokenService()
        val config = createTestConfig(accessTokenService = tokenService)
        val issuerId = "test-issuer"
        val provider = buildOAuth2Provider(
            config = config,
            includeAuthorizationCodeDefaultHandlers = true,
            includePreAuthorizedCodeDefaultHandlers = false,
        )

        val authorizeRequest = (provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid credential"),
            ),
        ) as AuthorizationRequestResult.Success).request.withIssuer(issuerId)
        val session = DefaultSession(subject = "demo-subject")
            .withCustomAttribute("issuance_session_id", "session-123")
        val authorizeResponse =
            provider.createAuthorizationResponse(authorizeRequest, session) as AuthorizationResponseResult.Success

        val accessRequest = (provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("demo-client"),
                "code" to listOf(authorizeResponse.response.code),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        ) as AccessTokenRequestResult.Success).request.withIssuer(issuerId)

        val accessResponse = provider.createAccessTokenResponse(accessRequest)
        assertTrue(accessResponse.isSuccess())
        assertEquals("session-123", tokenService.lastClaims["issuance_session_id"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `authorization code token response includes authorization details from token response options`() = runTest {
        val config = createTestConfig()
        val issuerId = "test-issuer"
        val provider = buildOAuth2Provider(
            config = config,
            includeAuthorizationCodeDefaultHandlers = true,
            includePreAuthorizedCodeDefaultHandlers = false,
        )

        val authorizeRequest = (provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid credential"),
            ),
        ) as AuthorizationRequestResult.Success).request.withIssuer(issuerId)
        val authorizeResponse = provider.createAuthorizationResponse(
            authorizeRequest,
            DefaultSession(subject = "demo-subject"),
        ) as AuthorizationResponseResult.Success

        val accessRequest = (provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("demo-client"),
                "code" to listOf(authorizeResponse.response.code),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        ) as AccessTokenRequestResult.Success).request.withIssuer(issuerId)

        val accessResponse = provider.createAccessTokenResponse(
            accessRequest,
            TokenResponseOptions(
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE,
                        credentialConfigurationId = "identity_credential",
                        credentialIdentifiers = listOf("credential-123"),
                    ),
                ),
            ),
        )

        assertTrue(accessResponse.isSuccess())
        val tokenResponse = (accessResponse as AccessTokenResponseResult.Success).response
        val authorizationDetail = (tokenResponse.extra["authorization_details"] as JsonArray)
            .single()
            .jsonObject
        assertEquals(OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE, authorizationDetail["type"]?.jsonPrimitive?.content)
        assertEquals("identity_credential", authorizationDetail["credential_configuration_id"]?.jsonPrimitive?.content)
        assertEquals(
            "credential-123",
            authorizationDetail["credential_identifiers"]
                ?.let { it as JsonArray }
                ?.single()
                ?.jsonPrimitive
                ?.content,
        )
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

        val authorizationRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )

        assertTrue(authorizationRequestResult.isSuccess())
        val authorizeRequest = (authorizationRequestResult as AuthorizationRequestResult.Success).request

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
        val accessTokenRequestResult = provider.createAccessTokenRequest(
            mapOf("grant_type" to listOf("client_credentials")),
        )
        assertTrue(accessTokenRequestResult is AccessTokenRequestResult.Failure)
        assertEquals("unsupported_grant_type", accessTokenRequestResult.error.error)
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

        val response = provider.createAccessTokenResponse(request)
        assertTrue(response is AccessTokenResponseResult.Failure)
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
        val authorizationRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )
        assertTrue(authorizationRequestResult is AuthorizationRequestResult.Success)
        val authorizeResponse = provider.createAuthorizationResponse(
            authorizationRequestResult.request,
            DefaultSession(subject = "sub"),
        )
        assertTrue(authorizeResponse is AuthorizationResponseResult.Failure)
        assertEquals("unsupported_response_type", authorizeResponse.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `authorization code token request without client authentication fails when TOKEN methods are configured`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig().copy(
                clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                    methods = listOf(TokenTestClientSecretPostAuthenticationMethod),
                    methodsByEndpoint = mapOf(
                        ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                    ),
                ),
            )
        )

        val result = assertIs<AccessTokenRequestResult.Failure>(
            provider.createAccessTokenRequest(
                mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to listOf("demo-client"),
                    "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                )
            )
        )

        assertEquals("invalid_client", result.error.error)
        assertEquals("Client authentication is required for this endpoint", result.error.description)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `pre-authorized token request can omit client authentication when TOKEN methods are not configured`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig().copy(
                clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                    methods = listOf(TokenTestClientSecretPostAuthenticationMethod),
                ),
            )
        )

        val result = assertIs<AccessTokenRequestResult.Success>(
            provider.createAccessTokenRequest(
                mapOf(
                    "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                    "pre-authorized_code" to listOf("pre-authorized-code"),
                )
            )
        )

        assertEquals(null, result.request.authenticatedClient)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `token request uses authenticated client id when client authentication succeeds`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig().copy(
                clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                    methods = listOf(TokenTestClientSecretPostAuthenticationMethod),
                    methodsByEndpoint = mapOf(
                        ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                    ),
                ),
            )
        )

        val result = assertIs<AccessTokenRequestResult.Success>(
            provider.createAccessTokenRequest(
                mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to listOf("demo-client"),
                    "client_secret" to listOf("secret-value"),
                    "code" to listOf("authorization-code"),
                    "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                ),
            )
        )

        assertEquals("demo-client", result.request.client.id)
        assertEquals("demo-client", result.request.authenticatedClient?.id)
    }
}

private class CapturingTokenService : AccessTokenService {
    var lastClaims: Map<String, Any?> = emptyMap()
        private set

    override suspend fun createAccessToken(claims: Map<String, Any?>): String {
        lastClaims = claims
        return "captured-access-token"
    }
}

private object TokenTestClientSecretPostAuthenticationMethod : ClientAuthenticationServiceMethod {
    override val name: String = ClientAuthenticationMethods.CLIENT_SECRET_POST

    @Suppress("UNUSED_PARAMETER")
    override suspend fun authenticate(
        endpoint: ClientAuthenticationEndpoint,
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
        context: ClientAuthenticationContext,
    ): ClientAuthenticationResult =
        ClientAuthenticationResult.Authenticated(
            AuthenticatedClient(
                id = "demo-client",
                authenticationMethod = name,
            )
        )
}
