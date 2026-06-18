package id.walt.issuer2.openid4vci

import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.openid4vci.ResponseType
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.openid4vci.responses.par.PushedAuthorizationResponseResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Issuer2PAREndpointTest {

    @Test
    fun `issuer module provider allows direct authorization when PAR is not enforced by default`() = runTest {
        val provider = issuerProvider()
        val parameters = validParameters()

        val result = provider.createAuthorizationRequest(parameters)

        assertTrue(result is AuthorizationRequestResult.Success)
        assertEquals(parameters, (result as AuthorizationRequestResult.Success).request.requestForm)
    }

    @Test
    fun `issuer module provider creates PAR with valid parameters`() = runTest {
        val provider = issuerProvider()

        val response = pushAuthorizationRequest(provider, validParameters())

        assertNotNull(response.requestUri)
        assertTrue(response.requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals(90, response.expiresIn)
    }

    @Test
    fun `issuer module provider resolves request_uri and retrieves original parameters`() = runTest {
        val provider = issuerProvider()
        val parameters = validParameters(
            scope = listOf("openid", "profile"),
            state = "state456",
        )

        val parResponse = pushAuthorizationRequest(provider, parameters)
        val authorizeResult = provider.createAuthorizationRequest(
            mapOf(
                "client_id" to listOf("test-client"),
                "request_uri" to listOf(parResponse.requestUri),
            )
        )

        assertTrue(authorizeResult is AuthorizationRequestResult.Success)
        assertEquals(parameters, (authorizeResult as AuthorizationRequestResult.Success).request.requestForm)
    }

    @Test
    fun `issuer module provider enforces single-use request_uri`() = runTest {
        val provider = issuerProvider()
        val parResponse = pushAuthorizationRequest(provider, validParameters())
        val authorizeParameters = mapOf(
            "client_id" to listOf("test-client"),
            "request_uri" to listOf(parResponse.requestUri),
        )

        assertTrue(provider.createAuthorizationRequest(authorizeParameters) is AuthorizationRequestResult.Success)

        val replay = provider.createAuthorizationRequest(authorizeParameters)
        assertTrue(replay is AuthorizationRequestResult.Failure)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST_URI, (replay as AuthorizationRequestResult.Failure).error.error)
    }

    @Test
    fun `issuer module provider rejects client_id mismatch`() = runTest {
        val provider = issuerProvider()
        val parResponse = pushAuthorizationRequest(provider, validParameters(clientId = "original-client"))

        val result = provider.createAuthorizationRequest(
            mapOf(
                "client_id" to listOf("different-client"),
                "request_uri" to listOf(parResponse.requestUri),
            )
        )

        assertTrue(result is AuthorizationRequestResult.Failure)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, (result as AuthorizationRequestResult.Failure).error.error)
    }

    @Test
    fun `issuer module provider rejects request_uri at PAR endpoint`() = runTest {
        val provider = issuerProvider()

        val result = provider.createPushedAuthorizationRequest(
            validParameters() + ("request_uri" to listOf("urn:ietf:params:oauth:request_uri:nested"))
        )

        assertTrue(result is AuthorizationRequestResult.Failure)
        assertEquals(OAuthErrorCodes.INVALID_REQUEST, (result as AuthorizationRequestResult.Failure).error.error)
    }

    @Test
    fun `issuer module provider enforces PAR when configured`() = runTest {
        val provider = issuerProvider(enforcePushedAuthorizationRequests = true)
        val parameters = validParameters(clientId = "required-par-client")

        val directAuthorize = provider.createAuthorizationRequest(parameters)
        assertTrue(directAuthorize is AuthorizationRequestResult.Failure)
        assertEquals(
            OAuthErrorCodes.INVALID_REQUEST,
            (directAuthorize as AuthorizationRequestResult.Failure).error.error,
        )

        val parResponse = pushAuthorizationRequest(provider, parameters)
        val authorizeResult = provider.createAuthorizationRequest(
            mapOf(
                "client_id" to listOf("required-par-client"),
                "request_uri" to listOf(parResponse.requestUri),
            )
        )

        assertTrue(authorizeResult is AuthorizationRequestResult.Success)
    }

    @Test
    fun `should extract request_id from request_uri`() {
        val requestUri = "urn:ietf:params:oauth:request_uri:abc123xyz"
        val requestId = PushedAuthorizationResponse.extractRequestId(requestUri)

        assertEquals("abc123xyz", requestId)
    }

    private fun issuerProvider(enforcePushedAuthorizationRequests: Boolean = false): OAuth2Provider =
        OpenId4VciModule.create(
            config = Issuer2ServiceConfig(
                baseUrl = "http://localhost",
                enforcePushedAuthorizationRequests = enforcePushedAuthorizationRequests,
            ),
            authorizationCodeRepository = ConfiguredAuthorizationCodeRepository(),
            preAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository(),
            parRepository = InMemoryPARRepository(),
        ).oauth2Provider

    private suspend fun pushAuthorizationRequest(
        provider: OAuth2Provider,
        parameters: Map<String, List<String>>,
    ): PushedAuthorizationResponse {
        val pushedRequest = provider.createPushedAuthorizationRequest(parameters)
        assertTrue(pushedRequest is AuthorizationRequestResult.Success)

        val pushedResponse = provider.createPushedAuthorizationResponse(
            (pushedRequest as AuthorizationRequestResult.Success).request
        )
        assertTrue(pushedResponse is PushedAuthorizationResponseResult.Success)

        return (pushedResponse as PushedAuthorizationResponseResult.Success).response
    }

    private fun validParameters(
        clientId: String = "test-client",
        scope: List<String> = listOf("openid"),
        state: String = "state123",
    ): Map<String, List<String>> =
        mapOf(
            "client_id" to listOf(clientId),
            "response_type" to listOf(ResponseType.CODE.value),
            "redirect_uri" to listOf("https://example.com/callback"),
            "scope" to scope,
            "state" to listOf(state),
        )
}