package id.walt.openid4vci.responses.par

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PushedAuthorizationResponseTest {

    @Test
    fun `should create PAR response with valid request_uri`() {
        val response = PushedAuthorizationResponse(
            requestUri = "urn:ietf:params:oauth:request_uri:abc123",
            expiresIn = 90
        )

        assertEquals("urn:ietf:params:oauth:request_uri:abc123", response.requestUri)
        assertEquals(90, response.expiresIn)
    }

    @Test
    fun `should fail when request_uri is blank`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationResponse(
                requestUri = "",
                expiresIn = 90
            )
        }
    }

    @Test
    fun `should fail when request_uri does not follow RFC 9126 format`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationResponse(
                requestUri = "https://example.com/request/123",
                expiresIn = 90
            )
        }
    }

    @Test
    fun `should fail when expires_in is zero`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationResponse(
                requestUri = "urn:ietf:params:oauth:request_uri:abc123",
                expiresIn = 0
            )
        }
    }

    @Test
    fun `should fail when expires_in is negative`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationResponse(
                requestUri = "urn:ietf:params:oauth:request_uri:abc123",
                expiresIn = -10
            )
        }
    }

    @Test
    fun `should create PAR response with generated request_uri`() {
        val response = PushedAuthorizationResponse.create(
            requestId = "unique-request-123",
            expiresIn = 60
        )

        assertEquals("urn:ietf:params:oauth:request_uri:unique-request-123", response.requestUri)
        assertEquals(60, response.expiresIn)
    }

    @Test
    fun `should create PAR response with default expiry`() {
        val response = PushedAuthorizationResponse.create(
            requestId = "unique-request-456"
        )

        assertNotNull(response.requestUri)
        assertEquals(90, response.expiresIn) // Default per RFC 9126
    }

    @Test
    fun `should fail to create PAR response with blank request ID`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationResponse.create(
                requestId = "",
                expiresIn = 90
            )
        }
    }

    @Test
    fun `should extract request ID from valid request_uri`() {
        val requestUri = "urn:ietf:params:oauth:request_uri:abc123xyz"
        val requestId = PushedAuthorizationResponse.extractRequestId(requestUri)

        assertEquals("abc123xyz", requestId)
    }

    @Test
    fun `should return null when extracting from invalid request_uri`() {
        val requestUri = "https://example.com/request/123"
        val requestId = PushedAuthorizationResponse.extractRequestId(requestUri)

        assertNull(requestId)
    }

    @Test
    fun `should return null when extracting from empty request_uri`() {
        val requestId = PushedAuthorizationResponse.extractRequestId("")
        assertNull(requestId)
    }

    @Test
    fun `should handle request_uri with special characters in request ID`() {
        val response = PushedAuthorizationResponse.create(
            requestId = "request-123_abc.xyz",
            expiresIn = 90
        )

        val extractedId = PushedAuthorizationResponse.extractRequestId(response.requestUri)
        assertEquals("request-123_abc.xyz", extractedId)
    }
}
