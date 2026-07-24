package id.walt.openid4vci.tokens.access

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccessTokenAuthorizationTest {

    @Test
    fun `parses Bearer scheme case-insensitively`() {
        val authorization = parseAccessTokenAuthorization(listOf("bEaReR access-token"))

        assertEquals(AccessTokenAuthorizationScheme.BEARER, authorization.scheme)
        assertEquals("access-token", authorization.token)
    }

    @Test
    fun `parses DPoP scheme case-insensitively`() {
        val authorization = parseAccessTokenAuthorization(listOf("dPoP access-token"))

        assertEquals(AccessTokenAuthorizationScheme.DPOP, authorization.scheme)
        assertEquals("access-token", authorization.token)
    }

    @Test
    fun `rejects duplicate Authorization header values`() {
        assertFailsWith<IllegalArgumentException> {
            parseAccessTokenAuthorization(
                listOf(
                    "Bearer first-token",
                    "Bearer second-token",
                ),
            )
        }
    }

    @Test
    fun `rejects unsupported authorization schemes`() {
        assertFailsWith<IllegalArgumentException> {
            parseAccessTokenAuthorization(listOf("Basic credentials"))
        }
    }

    @Test
    fun `rejects missing or malformed access tokens`() {
        listOf(
            "Bearer",
            "Bearer   ",
            "Bearer token with-whitespace",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                parseAccessTokenAuthorization(listOf(value))
            }
        }
    }
}
