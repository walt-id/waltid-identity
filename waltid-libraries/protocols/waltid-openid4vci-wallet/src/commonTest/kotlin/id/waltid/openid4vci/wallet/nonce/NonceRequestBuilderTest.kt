package id.waltid.openid4vci.wallet.nonce

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NonceRequestBuilderTest {
    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    @Test
    fun obtainsNonceAndIgnoresExtensionParameters() = runTest {
        val builder = NonceRequestBuilder(client { request ->
            assertEquals(NONCE_ENDPOINT, request.url.toString())
            respond(
                content = """{"c_nonce":"proof-nonce","c_nonce_expires_in":300}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val response = builder.requestNonce(NONCE_ENDPOINT)

        assertEquals("proof-nonce", response.cNonce)
        assertFalse(response.toString().contains("proof-nonce"))
    }

    @Test
    fun rejectsMissingOrBlankNonce() = runTest {
        for (body in listOf("{}", """{"c_nonce":""}""")) {
            val error = assertFailsWith<NonceRequestException> {
                NonceRequestBuilder(client { respond(body, HttpStatusCode.OK) })
                    .requestNonce(NONCE_ENDPOINT)
            }
            assertEquals(NonceRequestError.INVALID_RESPONSE, error.error)
        }
    }

    @Test
    fun malformedResponseDoesNotEscapeThroughException() = runTest {
        val error = assertFailsWith<NonceRequestException> {
            NonceRequestBuilder(client {
                respond("""{"private":"issuer-secret"}""", HttpStatusCode.OK)
            }).requestNonce(NONCE_ENDPOINT)
        }

        assertEquals(NonceRequestError.INVALID_RESPONSE, error.error)
        assertFalse(error.message.orEmpty().contains("issuer-secret"))
        assertEquals(null, error.cause)
    }

    @Test
    fun reportsIssuerRejectionWithoutRetainingResponseBody() = runTest {
        val error = assertFailsWith<NonceRequestException> {
            NonceRequestBuilder(client {
                respond("""{"error":"secret-detail"}""", HttpStatusCode.BadRequest)
            }).requestNonce(NONCE_ENDPOINT)
        }

        assertEquals(NonceRequestError.ISSUER_RESPONSE, error.error)
        assertEquals(400, error.statusCode)
        assertFalse(error.message.orEmpty().contains("secret-detail"))
    }

    @Test
    fun networkFailureDoesNotRetainSensitiveCause() = runTest {
        val error = assertFailsWith<NonceRequestException> {
            NonceRequestBuilder(client { error("private-network-detail") })
                .requestNonce(NONCE_ENDPOINT)
        }

        assertEquals(NonceRequestError.NETWORK, error.error)
        assertFalse(error.message.orEmpty().contains("private-network-detail"))
        assertEquals(null, error.cause)
    }

    @Test
    fun doesNotFollowRedirects() = runTest {
        for (status in listOf(
            HttpStatusCode.SeeOther,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect,
        )) {
            var calls = 0
            val error = assertFailsWith<NonceRequestException> {
                NonceRequestBuilder(client {
                    calls += 1
                    respond(
                        content = "",
                        status = status,
                        headers = headersOf(HttpHeaders.Location, "$ISSUER/nonce-v2"),
                    )
                }).requestNonce(NONCE_ENDPOINT)
            }

            assertEquals(NonceRequestError.ISSUER_RESPONSE, error.error)
            assertEquals(status.value, error.statusCode)
            assertEquals(1, calls)
        }
    }

    @Test
    fun acceptsHttpEndpoints() = runTest {
        val response = NonceRequestBuilder(client { request ->
            assertEquals("http://127.0.0.1/nonce", request.url.toString())
            respond("""{"c_nonce":"test-nonce"}""", HttpStatusCode.OK)
        }).requestNonce("http://127.0.0.1/nonce")

        assertEquals("test-nonce", response.cNonce)
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val NONCE_ENDPOINT = "$ISSUER/nonce"
    }
}
