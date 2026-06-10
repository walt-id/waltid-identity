package id.walt.openid4vci.core

import id.walt.openid4vci.repository.par.InMemoryPARRepository
import id.walt.openid4vci.requests.par.PushedAuthorizationRequest
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DefaultPARProviderTest {

    @Test
    fun `should process PAR request and return response`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo, parExpirySeconds = 90)

        val request = PushedAuthorizationRequest(
            clientId = "test-client",
            responseType = "code",
            redirectUri = "https://example.com/callback",
            scope = "openid",
        )

        val response = provider.processPushedAuthorizationRequest(request)

        assertNotNull(response.requestUri)
        assertTrue(response.requestUri.startsWith("urn:ietf:params:oauth:request_uri:"))
        assertEquals(90, response.expiresIn)
    }

    @Test
    fun `should store PAR entry in repository`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo, parExpirySeconds = 90)

        val request = PushedAuthorizationRequest(clientId = "test-client")

        val response = provider.processPushedAuthorizationRequest(request)

        // Verify entry was stored
        assertEquals(1, repo.size())

        val requestId = response.requestUri.removePrefix("urn:ietf:params:oauth:request_uri:")
        val now = Clock.System.now()
        val entry = repo.findByRequestId(requestId, now)

        assertNotNull(entry)
        assertEquals("test-client", entry.request.clientId)
    }

    @Test
    fun `should validate redirect_uri scheme`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(
            clientId = "test-client",
            redirectUri = "ftp://invalid.com/callback", // Invalid scheme
        )

        assertFailsWith<IllegalArgumentException> {
            provider.processPushedAuthorizationRequest(request)
        }
    }

    @Test
    fun `should validate PKCE code_challenge_method`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(
            clientId = "test-client",
            codeChallenge = "challenge123",
            codeChallengeMethod = "invalid-method",
        )

        assertFailsWith<IllegalArgumentException> {
            provider.processPushedAuthorizationRequest(request)
        }
    }

    @Test
    fun `should accept valid PKCE methods plain and S256`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val plainRequest = PushedAuthorizationRequest(
            clientId = "test-client",
            codeChallenge = "challenge123",
            codeChallengeMethod = "plain",
        )

        val s256Request = PushedAuthorizationRequest(
            clientId = "test-client-2",
            codeChallenge = "challenge456",
            codeChallengeMethod = "S256",
        )

        // Should not throw
        provider.processPushedAuthorizationRequest(plainRequest)
        provider.processPushedAuthorizationRequest(s256Request)

        assertEquals(2, repo.size())
    }

    @Test
    fun `should resolve valid request_uri`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(
            clientId = "test-client",
            responseType = "code",
            redirectUri = "https://example.com/callback",
            scope = "openid",
            state = "state123",
        )

        val response = provider.processPushedAuthorizationRequest(request)

        // Resolve request_uri
        val resolved = provider.resolveRequestUri(response.requestUri, "test-client")

        assertNotNull(resolved)
        assertEquals(listOf("test-client"), resolved["client_id"])
        assertEquals(listOf("code"), resolved["response_type"])
        assertEquals(listOf("https://example.com/callback"), resolved["redirect_uri"])
        assertEquals(listOf("openid"), resolved["scope"])
        assertEquals(listOf("state123"), resolved["state"])
    }

    @Test
    fun `should return null for invalid request_uri format`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val resolved = provider.resolveRequestUri(
            "https://example.com/request/123", // Wrong format
            "test-client"
        )

        assertNull(resolved)
    }

    @Test
    fun `should return null for non-existent request_uri`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val resolved = provider.resolveRequestUri(
            "urn:ietf:params:oauth:request_uri:non-existent",
            "test-client"
        )

        assertNull(resolved)
    }

    @Test
    fun `should fail when client_id mismatch`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(clientId = "original-client")
        val response = provider.processPushedAuthorizationRequest(request)

        // Try to resolve with different client_id
        assertFailsWith<IllegalArgumentException> {
            provider.resolveRequestUri(response.requestUri, "different-client")
        }
    }

    @Test
    fun `should enforce single-use request_uri`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(clientId = "test-client")
        val response = provider.processPushedAuthorizationRequest(request)

        // First resolution should succeed
        val firstResolve = provider.resolveRequestUri(response.requestUri, "test-client")
        assertNotNull(firstResolve)

        // Second resolution should fail (already consumed)
        val secondResolve = provider.resolveRequestUri(response.requestUri, "test-client")
        assertNull(secondResolve)
    }

    // Note: Time-based expiry test removed due to test framework limitations with kotlin.time.Instant
    // Expiry behavior is tested at the repository level in InMemoryPARRepositoryTest

    @Test
    fun `should preserve client authentication metadata`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo)

        val request = PushedAuthorizationRequest(clientId = "test-client")
        val clientAuth = mapOf(
            "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            "client_assertion" to "eyJhbGciOiJSUzI1NiJ9..."
        )

        val response = provider.processPushedAuthorizationRequest(request, clientAuth)

        val requestId = response.requestUri.removePrefix("urn:ietf:params:oauth:request_uri:")
        val now = Clock.System.now()
        val entry = repo.findByRequestId(requestId, now)

        assertNotNull(entry)
        assertEquals(2, entry.clientMetadata.size)
        assertTrue(entry.clientMetadata.containsKey("client_assertion_type"))
    }

    @Test
    fun `should use custom PAR expiry`() = runTest {
        val repo = InMemoryPARRepository()
        val provider = DefaultPARProvider(repo, parExpirySeconds = 60)

        val request = PushedAuthorizationRequest(clientId = "test-client")
        val response = provider.processPushedAuthorizationRequest(request)

        assertEquals(60, response.expiresIn)
    }

    @Test
    fun `should fail to create provider with non-positive expiry`() {
        val repo = InMemoryPARRepository()

        assertFailsWith<IllegalArgumentException> {
            DefaultPARProvider(repo, parExpirySeconds = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            DefaultPARProvider(repo, parExpirySeconds = -10)
        }
    }
}
