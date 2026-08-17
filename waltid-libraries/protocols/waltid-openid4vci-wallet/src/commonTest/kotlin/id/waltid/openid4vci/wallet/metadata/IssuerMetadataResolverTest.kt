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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        assertEquals(issuerUrl, metadata.metadata.credentialIssuer)
        assertEquals(1, metadata.metadata.credentialConfigurationsSupported.size)
    }
    @Test
    fun testResolveCredentialIssuerMetadataWithIssuerPath() = runTest {
        val issuerUrl = "https://example.com/openid4vci"
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
            if (request.url.toString() == "https://example.com/.well-known/openid-credential-issuer/openid4vci") {
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

        assertEquals(issuerUrl, metadata.metadata.credentialIssuer)
        assertEquals("$issuerUrl/credential", metadata.metadata.credentialEndpoint)
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

    @Test
    fun testResolveAuthorizationServerMetadataWithAsPath() = runTest {
        val asUrl = "https://auth.example.com/as"
        val mockResponse = """
            {
                "issuer": "$asUrl",
                "authorization_endpoint": "$asUrl/authorize",
                "token_endpoint": "$asUrl/token",
                "response_types_supported": ["code"]
            }
        """.trimIndent()

        val client = createMockClient { request ->
            if (request.url.toString() == "https://auth.example.com/.well-known/oauth-authorization-server/as") {
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

    @Test
    fun `does not append the default HTTPS port to the well-known URL`() {
        val resolver = IssuerMetadataResolver(createMockClient { respondError(HttpStatusCode.NotFound) })

        assertEquals(
            "https://issuer.example.org/.well-known/oauth-authorization-server/issuer",
            resolver.buildMetadataUrl("https://issuer.example.org/issuer", "/.well-known/oauth-authorization-server"),
        )
        assertEquals(
            "http://issuer.example.org/.well-known/oauth-authorization-server",
            resolver.buildMetadataUrl("http://issuer.example.org", "/.well-known/oauth-authorization-server"),
        )
    }

    @Test
    fun `preserves an explicit non-default port on the well-known URL`() {
        val resolver = IssuerMetadataResolver(createMockClient { respondError(HttpStatusCode.NotFound) })

        assertEquals(
            "https://issuer.example.org:8443/.well-known/oauth-authorization-server/issuer",
            resolver.buildMetadataUrl("https://issuer.example.org:8443/issuer", "/.well-known/oauth-authorization-server"),
        )
        assertEquals(
            "http://issuer.example.org:8080/.well-known/openid-credential-issuer",
            resolver.buildMetadataUrl("http://issuer.example.org:8080", "/.well-known/openid-credential-issuer"),
        )
    }

    @Test
    fun `parse failure surfaces the underlying exception message and cause`() = runTest {
        val issuerUrl = "https://issuer.example.org"
        val metadataUrl = "https://issuer.example.org/.well-known/openid-credential-issuer"
        // Emit an OID4VCI 1.0 metadata document whose only defect is a spec-violating
        // `batch_credential_issuance.batch_size: 1` (the spec requires >= 2). This is exactly
        // the shape shipped by several externally-deployed EU pilot issuers, and is what
        // previously produced a generic "Failed to resolve ... from any of" error.
        val body = """
            {
              "credential_issuer": "$issuerUrl",
              "credential_endpoint": "$issuerUrl/credential",
              "batch_credential_issuance": { "batch_size": 1 },
              "credential_configurations_supported": {
                "test": {
                  "format": "jwt_vc_json",
                  "credential_definition": { "type": ["VerifiableCredential", "TestCredential"] }
                }
              }
            }
        """.trimIndent()

        val client = createMockClient { request ->
            if (request.url.toString() == metadataUrl) {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }

        val failure = assertFailsWith<Exception> {
            IssuerMetadataResolver(client).resolveCredentialIssuerMetadata(issuerUrl)
        }

        assertContains(failure.message ?: "", metadataUrl, message = "Attempted URL must appear in error")
        assertContains(failure.message ?: "", "parse error", message = "Failure kind must be surfaced")
        assertContains(failure.message ?: "", "batch_size", message = "Underlying validation message must reach the caller")
        assertNotNull(failure.cause, "First underlying failure must be chained as cause")
    }

    @Test
    fun `HTTP status failure surfaces status code and body preview`() = runTest {
        val issuerUrl = "https://issuer.example.org"
        val client = createMockClient {
            respond("issuer temporarily unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val failure = assertFailsWith<Exception> {
            IssuerMetadataResolver(client).resolveCredentialIssuerMetadata(issuerUrl)
        }

        val message = failure.message ?: ""
        assertContains(message, "HTTP 503", message = "Status code must be surfaced")
        assertContains(message, "issuer temporarily unavailable", message = "Body preview must be surfaced")
    }

    @Test
    fun `network failure surfaces the underlying exception message`() = runTest {
        val client = createMockClient { throw RuntimeException("connect refused: target down") }

        val failure = assertFailsWith<Exception> {
            IssuerMetadataResolver(client).resolveCredentialIssuerMetadata("https://issuer.example.org")
        }

        val message = failure.message ?: ""
        assertContains(message, "network error", message = "Failure kind must be surfaced")
        assertContains(message, "connect refused: target down", message = "Transport error message must reach the caller")
        assertNotNull(failure.cause, "Network error must be chained as cause")
        assertTrue(
            failure.cause!!.message?.contains("connect refused") == true,
            "Cause must expose the original transport exception",
        )
    }

}
