package id.walt.issuer2.openid4vci

import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService
import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.core.DefaultPARProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for PAR endpoint in OSS Issuer2.
 *
 * Tests PAR ingestion, request_uri generation, and basic validation.
 */
class Issuer2PAREndpointTest {

    @Test
    fun `should create PAR with valid parameters`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val parameters = mapOf(
            "client_id" to listOf("test-client"),
            "response_type" to listOf("code"),
            "redirect_uri" to listOf("https://example.com/callback"),
            "scope" to listOf("openid"),
            "state" to listOf("state123"),
        )

        val parRequest = id.walt.openid4vci.requests.par.PushedAuthorizationRequest.fromParameters(parameters)
        val response = provider.processPushedAuthorizationRequest(parRequest)

        assertNotNull(response.requestUri)
        assertTrue(response.requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals(90, response.expiresIn)
    }

    @Test
    fun `should resolve request_uri and retrieve original parameters`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val parameters = mapOf(
            "client_id" to listOf("test-client"),
            "response_type" to listOf("code"),
            "redirect_uri" to listOf("https://example.com/callback"),
            "scope" to listOf("openid profile"),
            "state" to listOf("state456"),
        )

        val parRequest = id.walt.openid4vci.requests.par.PushedAuthorizationRequest.fromParameters(parameters)
        val parResponse = provider.processPushedAuthorizationRequest(parRequest)

        // Resolve request_uri
        val resolved = provider.resolveRequestUri(parResponse.requestUri, "test-client")

        assertNotNull(resolved)
        assertEquals(listOf("test-client"), resolved!!["client_id"])
        assertEquals(listOf("code"), resolved["response_type"])
        assertEquals(listOf("https://example.com/callback"), resolved["redirect_uri"])
        assertEquals(listOf("openid profile"), resolved["scope"])
        assertEquals(listOf("state456"), resolved["state"])
    }

    @Test
    fun `should enforce single-use request_uri`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val parameters = mapOf(
            "client_id" to listOf("test-client"),
            "response_type" to listOf("code"),
        )

        val parRequest = id.walt.openid4vci.requests.par.PushedAuthorizationRequest.fromParameters(parameters)
        val parResponse = provider.processPushedAuthorizationRequest(parRequest)

        // First resolution should succeed
        val firstResolve = provider.resolveRequestUri(parResponse.requestUri, "test-client")
        assertNotNull(firstResolve)

        // Second resolution should fail (already consumed)
        val secondResolve = provider.resolveRequestUri(parResponse.requestUri, "test-client")
        assertEquals(null, secondResolve, "request_uri should be single-use")
    }

    @Test
    fun `should validate PKCE parameters`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val parametersWithPKCE = mapOf(
            "client_id" to listOf("test-client"),
            "code_challenge" to listOf("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
            "code_challenge_method" to listOf("S256"),
        )

        val parRequest = id.walt.openid4vci.requests.par.PushedAuthorizationRequest.fromParameters(parametersWithPKCE)
        val response = provider.processPushedAuthorizationRequest(parRequest)

        assertNotNull(response.requestUri)

        // Verify PKCE params are preserved
        val resolved = provider.resolveRequestUri(response.requestUri, "test-client")
        assertNotNull(resolved)
        assertEquals(listOf("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"), resolved!!["code_challenge"])
        assertEquals(listOf("S256"), resolved["code_challenge_method"])
    }

    @Test
    fun `should extract request_id from request_uri`() {
        val requestUri = "urn:ietf:params:oauth:request_uri:abc123xyz"
        val requestId = PushedAuthorizationResponse.extractRequestId(requestUri)

        assertEquals("abc123xyz", requestId)
    }
}
