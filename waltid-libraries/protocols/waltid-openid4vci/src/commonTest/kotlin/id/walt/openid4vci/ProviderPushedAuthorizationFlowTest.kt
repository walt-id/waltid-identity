package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.PushedAuthorizationConfig
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.repository.par.PAREntry
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
    fun `provider rejects expired request_uri`() = runTest {
        val repository = InMemoryPARRepository()
        val provider = buildParProvider(repository)
        val parameters = validPushedParameters(clientId = "expired-client")
        val now = Clock.System.now()

        repository.store(
            PAREntry(
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
    fun `provider preserves raw multi-value pushed authorization parameters`() = runTest {
        val provider = buildParProvider()
        val pushedParameters = validPushedParameters(clientId = "multi-client") +
            ("scope" to listOf("openid", "credential"))

        val pushedResponse = pushAuthorizationRequest(provider, pushedParameters)
        val authorizeRequest = assertIs<AuthorizationRequestResult.Success>(
            provider.createAuthorizationRequest(
                mapOf(
                    "client_id" to listOf("multi-client"),
                    "request_uri" to listOf(pushedResponse.requestUri),
                )
            )
        ).request

        assertEquals(listOf("openid", "credential"), authorizeRequest.requestForm["scope"])
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
        val provider = buildParProvider()
        val pushedParameters = validPushedParameters(clientId = "auth-param-client") +
            mapOf(
                "client_secret" to listOf("secret-value"),
                "client_assertion_type" to listOf("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"),
                "client_assertion" to listOf("signed-client-assertion"),
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
        assertEquals(null, authorizeRequest.requestForm["client_assertion_type"])
        assertEquals(null, authorizeRequest.requestForm["client_assertion"])
        assertEquals("auth-param-client", authorizeRequest.requestForm["client_id"]?.singleOrNull())
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
    ) =
        buildOAuth2Provider(
            createTestConfig().copy(
                pushedAuthorizationConfig = PushedAuthorizationConfig(
                    repository = repository,
                    enforcePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
                ),
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
