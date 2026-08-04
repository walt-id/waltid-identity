package id.walt.wallet2.handlers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WalletIssuanceHandlerNonceTest {

    @Test
    fun tokenResultDescriptionRedactsAccessToken() {
        val result = RequestTokenResult(accessToken = "private-token", expiresIn = 300)

        assertFalse(result.toString().contains("private-token"))
    }

    @Test
    fun requestsNonceOnlyFromIssuerMetadata() = runTest {
        var nonceRequests = 0
        val client = issuerClient(nonceEndpoint = NONCE_ENDPOINT) { request ->
            nonceRequests += 1
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(NONCE_ENDPOINT, request.url.toString())
            """{"c_nonce":"fresh-proof-nonce"}"""
        }

        val result = WalletIssuanceHandler.requestNonce(
            RequestNonceRequest(Url(ISSUER)),
            client,
        )

        assertEquals("fresh-proof-nonce", result.nonce)
        assertEquals(1, nonceRequests)
        assertFalse(result.toString().contains("fresh-proof-nonce"))
    }

    @Test
    fun proofRequiredWithoutNonceEndpointReturnsNoNonce() = runTest {
        val client = issuerClient(nonceEndpoint = null) {
            error("Nonce endpoint must not be called")
        }

        val result = WalletIssuanceHandler.requestNonce(
            RequestNonceRequest(Url(ISSUER)),
            client,
        )

        assertEquals(null, result.nonce)
    }

    private fun issuerClient(
        nonceEndpoint: String?,
        nonceResponse: (io.ktor.client.request.HttpRequestData) -> String,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.toString()) {
                    METADATA_ENDPOINT -> respond(
                        content = issuerMetadata(nonceEndpoint),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )

                    NONCE_ENDPOINT -> respond(
                        content = nonceResponse(request),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun issuerMetadata(nonceEndpoint: String?): String = """
        {
          "credential_issuer": "$ISSUER",
          "credential_endpoint": "$ISSUER/credential",
          ${nonceEndpoint?.let { "\"nonce_endpoint\": \"$it\"," } ?: ""}
          "credential_configurations_supported": {
            "test": {
              "format": "jwt_vc_json",
              "credential_definition": {
                "type": ["VerifiableCredential", "TestCredential"]
              }
            }
          }
        }
    """.trimIndent()

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val METADATA_ENDPOINT = "$ISSUER/.well-known/openid-credential-issuer"
        const val NONCE_ENDPOINT = "$ISSUER/nonce"
    }
}
