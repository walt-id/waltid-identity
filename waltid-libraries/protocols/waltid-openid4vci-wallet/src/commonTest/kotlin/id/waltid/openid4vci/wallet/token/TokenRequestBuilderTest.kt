package id.waltid.openid4vci.wallet.token

import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.assertContains
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenRequestBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clientConfig = ClientConfiguration(
        clientId = "test-client",
        redirectUris = listOf("https://wallet.example.com/callback")
    )
    private val tokenEndpoint = "https://auth.example.com/token"

    private fun createMockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine) {
            engine { addHandler(handler) }
            install(ContentNegotiation) { json(json) }
        }
    }

    @Test
    fun testExchangeAuthorizationCodeSuccess() = runTest {
        val mockResponse = """
            {
                "access_token": "test-access-token",
                "token_type": "Bearer",
                "expires_in": 3600,
                "c_nonce": "test-nonce"
            }
        """.trimIndent()

        val client = createMockClient { request ->
            assertEquals(tokenEndpoint, request.url.toString())
            // In some Ktor versions, Content-Type might be in request.body.contentType instead of headers for FormUrlEncoded

            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.exchangeAuthorizationCode(tokenEndpoint, "test-code", "test-verifier")

        assertEquals("test-access-token", response.access_token)
        assertEquals("test-nonce", response.c_nonce)
    }

    @Test
    fun testExchangePreAuthorizedCodeSuccess() = runTest {
        val mockResponse = """
            {
                "access_token": "pre-auth-token",
                "token_type": "Bearer"
            }
        """.trimIndent()

        val client = createMockClient { _ ->
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.exchangePreAuthorizedCode(tokenEndpoint, "pre-auth-code", "123456")

        assertEquals("pre-auth-token", response.access_token)
    }

    @Test
    fun testTokenRequestFailure() = runTest {
        val client = createMockClient { _ ->
            respond(
                content = "Invalid code",
                status = HttpStatusCode.BadRequest
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        assertFailsWith<Exception> {
            builder.exchangeAuthorizationCode(tokenEndpoint, "invalid-code")
        }
    }

    @Test
    fun testExchangePreAuthorizedCodeForwardsAdditionalHeaders() = runTest {
        val client = createMockClient { request ->
            assertEquals("Bearer abc", request.headers[HttpHeaders.Authorization])
            assertEquals("tenant.example.com", request.headers[HttpHeaders.Host])
            respond(
                content = """{"access_token":"pre-auth-token","token_type":"Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = "pre-auth-code",
            additionalHeaders = mapOf(
                HttpHeaders.Authorization to "Bearer abc",
                HttpHeaders.Host to "tenant.example.com",
            ),
        )

        assertEquals("pre-auth-token", response.access_token)
    }

    @Test
    fun testExchangePreAuthorizedCodeRejectsCrossOriginRedirectWithHeaders() = runTest {
        var callCount = 0
        val client = createMockClient { _ ->
            callCount += 1
            when (callCount) {
                1 -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://other.example.com/token")
                )

                else -> error("Cross-origin redirect should not be followed when request headers are present")
            }
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val error = assertFailsWith<IllegalStateException> {
            builder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = "pre-auth-code",
                additionalHeaders = mapOf(HttpHeaders.Authorization to "Bearer abc"),
            )
        }

        assertContains(error.message.orEmpty(), "Cross-origin redirect")
        assertEquals(1, callCount)
    }
}
