package id.walt.wallet2.server.handlers

import id.waltid.openid4vci.wallet.token.TokenRequestException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Wallet2TokenErrorMappingTest {

    @Test
    fun clientTokenFailurePreservesOAuthStatusWithoutResponseBody() {
        val upstream = TokenRequestException(
            statusCode = HttpStatusCode.BadRequest.value,
            oauthError = "invalid_grant",
            cause = IllegalStateException("upstream body must not escape"),
        )

        val mapped = upstream.toWebException()

        assertEquals(HttpStatusCode.BadRequest.value, mapped.status)
        assertEquals("invalid_grant", mapped.message)
        assertSame(upstream, mapped.cause)
    }

    @Test
    fun upstreamServerFailureBecomesBadGateway() {
        val mapped = TokenRequestException(statusCode = 500).toWebException()

        assertEquals(HttpStatusCode.BadGateway.value, mapped.status)
        assertEquals("token_request_failed", mapped.message)
    }

    @Test
    fun transportFailureBecomesBadGateway() {
        val mapped = TokenRequestException(statusCode = 0).toWebException()

        assertEquals(HttpStatusCode.BadGateway.value, mapped.status)
        assertEquals("token_request_failed", mapped.message)
    }
}
