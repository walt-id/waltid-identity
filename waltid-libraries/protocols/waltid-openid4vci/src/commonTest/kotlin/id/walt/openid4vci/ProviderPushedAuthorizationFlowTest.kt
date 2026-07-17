package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.clientauth.ClientAuthenticationServiceConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProviderPushedAuthorizationFlowTest {

    @Test
    fun `provider stores PAR and resolves request_uri through authorization endpoint`() = runTest {
        val provider = buildParProvider()
        val pushedParameters = mapOf(
            "response_type" to listOf(ResponseType.CODE.value),
            "client_id" to listOf("demo-client"),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "scope" to listOf("openid credential"),
            "state" to listOf("state-123"),
        )

        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(pushedParameters)
        ).request
        val pushedResponse = assertIs<PushedAuthorizationResponseResult.Success>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        ).response

        assertTrue(pushedResponse.requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals(90, pushedResponse.expiresIn)

        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(pushedParameters, authorizeRequest.requestForm)

        val replay = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        )
        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, replay.error.error)
    }

    @Test
    fun `provider rejects request_uri at PAR endpoint`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                mapOf(
                    "response_type" to listOf(ResponseType.CODE.value),
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:nested"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST, result.error.error)
    }

    @Test
    fun `provider rejects authorize request when PAR client_id does not match`() = runTest {
        val provider = buildParProvider()
        val pushedResponse = pushAuthorizationRequest(
            provider = provider,
            parameters = validPushedParameters(clientId = "original-client"),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("different-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST, result.error.error)
    }

    @Test
    fun `provider rejects invalid request_uri`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("https://client.example/request/123"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider rejects request_uri when PAR is not configured`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("demo-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:missing-config"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider returns server error when PAR endpoint is used without PAR configuration`() = runTest {
        val provider = buildOAuth2Provider(createTestConfig())
        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(validPushedParameters())
        ).request

        val result = assertIs<PushedAuthorizationResponseResult.Failure>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        )

        assertEquals(OAuthErrorCodes.SERVER_ERROR, result.error.error)
        assertEquals("Pushed authorization requests are not configured", result.error.description)
    }

    @Test
    fun `provider rejects expired request_uri`() = runTest {
        val repository = InMemoryPARRepository()
        val provider = buildParProvider(repository)
        val parameters = validPushedParameters(clientId = "expired-client")
        val now = Clock.System.now()

        repository.save(
            DefaultPARRecord(
                requestId = "expired-request",
                requestParameters = parameters,
                createdAt = now - 2.seconds,
                expiresAt = now - 1.seconds,
            )
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("expired-client"),
                    "request_uri" to listOf("urn:ietf:params:oauth:request_uri:expired-request"),
                )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, result.error.error)
    }

    @Test
    fun `provider preserves pushed authorization parameters`() = runTest {
        val provider = buildParProvider()
        val pushedParameters = validPushedParameters(clientId = "multi-client") +
            ("scope" to listOf("openid credential"))

        val pushedResponse = pushAuthorizationRequest(provider, pushedParameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("multi-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(listOf("openid credential"), authorizeRequest.requestForm["scope"])
    }

    @Test
    fun `provider enforces pushed authorization requests when configured`() = runTest {
        val provider = buildParProvider(enforcePushedAuthorizationRequests = true)
        val parameters = validPushedParameters(clientId = "required-par-client")

        val directAuthorize = assertIs<AuthorizationRequestResult.Failure>(
            provider.createAuthorizationRequest(parameters)
        )
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, directAuthorize.error.error)

        val pushedResponse = pushAuthorizationRequest(provider, parameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("required-par-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(parameters, authorizeRequest.requestForm)
    }

    @Test
    fun `provider strips endpoint-only client authentication parameters before storing PAR`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
            ),
        )
        val pushedParameters = validPushedParameters(clientId = "auth-param-client") +
            mapOf(
                "client_secret" to listOf("secret-value"),
            )

        val pushedResponse = pushAuthorizationRequest(provider, pushedParameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("auth-param-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(null, authorizeRequest.requestForm["client_secret"])
        assertEquals("auth-param-client", authorizeRequest.requestForm["client_id"]?.singleOrNull())
    }

    @Test
    fun `provider ignores client authentication parameters when PAR client authentication is disabled`() = runTest {
        val provider = buildParProvider()

        val result = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(
                validPushedParameters(clientId = "ignored-auth-client") +
                    mapOf(
                        "client_secret" to listOf("secret-value"),
                    )
            )
        )

        assertEquals(null, result.request.authenticatedClient)
    }

    @Test
    fun `provider rejects non-configured client authentication method at PAR endpoint`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                ),
            ),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(
                validPushedParameters(clientId = "unsupported-auth-client") +
                    mapOf(
                        "client_secret" to listOf("secret-value"),
                    )
            )
        )

        assertEquals(OAuthErrorCodes.INVALID_CLIENT, result.error.error)
        assertEquals(
            "Client authentication method 'client_secret_post' is not allowed for this endpoint",
            result.error.description,
        )
    }

    @Test
    fun `provider rejects unauthenticated PAR when endpoint client authentication methods are configured`() = runTest {
        val provider = buildParProvider(
            clientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(
                methods = listOf(AcceptingClientSecretPostAuthenticationMethod),
                methodsByEndpoint = mapOf(
                    ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to
                        setOf(ClientAuthenticationMethods.CLIENT_SECRET_POST),
                ),
            ),
        )

        val result = assertIs<AuthorizationRequestResult.Failure>(
            provider.createPushedAuthorizationRequest(validPushedParameters(clientId = "auth-param-client"))
        )

        assertEquals(OAuthErrorCodes.INVALID_CLIENT, result.error.error)
        assertEquals("Client authentication is required for this endpoint", result.error.description)
    }

    @Test
    fun `provider writes PAR responses with no-store headers`() = runTest {
        val provider = buildParProvider()
        val pushedRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createPushedAuthorizationRequest(validPushedParameters())
        ).request
        val pushedResponse = assertIs<PushedAuthorizationResponseResult.Success>(
            provider.createPushedAuthorizationResponse(pushedRequest)
        ).response

        val httpResponse = provider.writePushedAuthorizationResponse(pushedRequest, pushedResponse)

        assertEquals("no-store", httpResponse.headers["Cache-Control"])
        assertEquals("no-cache", httpResponse.headers["Pragma"])
    }

    private fun buildParProvider(
        repository: InMemoryPARRepository = InMemoryPARRepository(),
        enforcePushedAuthorizationRequests: Boolean = false,
        clientAuthenticationServiceConfig: ClientAuthenticationServiceConfig = ClientAuthenticationServiceConfig(),
    ) =
        buildOAuth2Provider(
            createTestConfig().copy(
                pushedAuthorizationConfig = PushedAuthorizationConfig(
                    repository = repository,
                    enforcePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
                ),
                clientAuthenticationServiceConfig = clientAuthenticationServiceConfig,
            )
        )

    private fun validPushedParameters(clientId: String = "demo-client"): Map<String, List<String>> =
        mapOf(
            "response_type" to listOf(ResponseType.CODE.value),
            "client_id" to listOf(clientId),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "scope" to listOf("openid credential"),
            "state" to listOf("state-123"),
        )

    private object AcceptingClientSecretPostAuthenticationMethod : ClientAuthenticationMethod {
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
                    id = "auth-param-client",
                    authenticationMethod = name,
                )
            )
    }

    private suspend fun pushAuthorizationRequest(
        provider: OAuth2Provider,
        parameters: Map<String, List<String>>,
    ) = assertIs<PushedAuthorizationResponseResult.Success>(
        provider.createPushedAuthorizationResponse(
            assertIs<AuthorizationRequestResult.Success>(
                provider.createPushedAuthorizationRequest(parameters)
            ).request
        )
    ).response
}
