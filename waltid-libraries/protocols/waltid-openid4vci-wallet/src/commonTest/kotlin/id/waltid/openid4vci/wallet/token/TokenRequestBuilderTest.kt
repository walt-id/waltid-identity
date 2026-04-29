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
}
