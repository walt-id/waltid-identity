package id.waltid.openid4vci.wallet.metadata

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

class IssuerMetadataResolverTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private fun createMockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Test
    fun testResolveCredentialIssuerMetadataSuccess() = runTest {
        val issuerUrl = "https://example.com"
        val mockResponse = """
            {
                "credential_issuer": "$issuerUrl",
                "credential_endpoint": "$issuerUrl/credential",
                "credential_configurations_supported": {
                    "test_id": {
                        "format": "jwt_vc_json",
                        "credential_definition": {
                            "type": ["VerifiableCredential", "TestCredential"]
                        }
                    }
                }
            }
        """.trimIndent()

        val client = createMockClient { request ->
            if (request.url.toString() == "$issuerUrl/.well-known/openid-credential-issuer") {
                respond(
                    content = mockResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }

        val resolver = IssuerMetadataResolver(client)
        val metadata = resolver.resolveCredentialIssuerMetadata(issuerUrl)

        assertEquals(issuerUrl, metadata.credentialIssuer)
        assertEquals(1, metadata.credentialConfigurationsSupported?.size)
    }

    @Test
    fun testResolveCredentialIssuerMetadataNotFound() = runTest {
        val client = createMockClient { _ ->
            respondError(HttpStatusCode.NotFound)
        }

        val resolver = IssuerMetadataResolver(client)
        assertFailsWith<Exception> {
            resolver.resolveCredentialIssuerMetadata("https://example.com")
        }
    }

    @Test
    fun testResolveAuthorizationServerMetadataSuccess() = runTest {
        val asUrl = "https://auth.example.com"
        val mockResponse = """
            {
                "issuer": "$asUrl",
                "authorization_endpoint": "$asUrl/authorize",
                "token_endpoint": "$asUrl/token",
                "response_types_supported": ["code"]
            }
        """.trimIndent()

        val client = createMockClient { request ->
            if (request.url.toString() == "$asUrl/.well-known/oauth-authorization-server") {
                respond(
                    content = mockResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }

        val resolver = IssuerMetadataResolver(client)
        val metadata = resolver.resolveAuthorizationServerMetadata(asUrl)

        assertEquals(asUrl, metadata.issuer)
        assertEquals("$asUrl/token", metadata.tokenEndpoint)
    }
}
