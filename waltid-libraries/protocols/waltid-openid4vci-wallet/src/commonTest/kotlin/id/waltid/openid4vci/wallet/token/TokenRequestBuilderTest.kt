package id.waltid.openid4vci.wallet.token

import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    private fun HttpRequestData.formParameters(): Parameters =
        parseQueryString(bodyText())

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun testExchangeAuthorizationCodeIgnoresTokenResponseNonceExtension() = runTest {
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
    }

    @Test
    fun testExchangePreAuthorizedCodeSuccess() = runTest {
        val mockResponse = """
            {
                "access_token": "pre-auth-token",
                "token_type": "Bearer"
            }
        """.trimIndent()

        val client = createMockClient { request ->
            val parameters = request.formParameters()
            assertEquals("test-client", parameters["client_id"])
            assertEquals("pre-auth-code", parameters["pre-authorized_code"])

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
    fun testExchangePreAuthorizedCodeAnonymousOmitsClientId() = runTest {
        val client = createMockClient { request ->
            val parameters = request.formParameters()
            assertNull(parameters["client_id"])
            assertEquals("pre-auth-code", parameters["pre-authorized_code"])

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
            anonymous = true,
        )

        assertEquals("pre-auth-token", response.access_token)
    }

    @Test
    fun testTokenRequestFailure() = runTest {
        val client = createMockClient { _ ->
            respond(
                content = "private-error-detail",
                status = HttpStatusCode.BadRequest
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val error = assertFailsWith<Exception> {
            builder.exchangeAuthorizationCode(tokenEndpoint, "invalid-code")
        }
        assertFalse(error.message.orEmpty().contains("private-error-detail"))
        assertNull(error.cause)
    }

    @Test
    fun testMalformedSuccessResponseDoesNotEscapeTokenMaterial() = runTest {
        val client = createMockClient {
            respond(
                content = """{"access_token":"private-token"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val error = assertFailsWith<Exception> {
            TokenRequestBuilder(clientConfig, client)
                .exchangeAuthorizationCode(tokenEndpoint, "test-code")
        }

        assertFalse(error.message.orEmpty().contains("private-token"))
        assertNull(error.cause)
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
    fun testExchangeAuthorizationCodeForwardsAttestationHeaders() = runTest {
        val client = createMockClient { request ->
            assertEquals(
                "attestation.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION],
            )
            assertEquals(
                "pop.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP],
            )
            respond(
                content = """{"access_token":"auth-token","token_type":"Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = "auth-code",
            attestationHeaders = ClientAttestationHeaders(
                attestationJwt = "attestation.jwt",
                popJwt = "pop.jwt",
            ),
        )

        assertEquals("auth-token", response.access_token)
    }

    @Test
    fun testExchangePreAuthorizedCodeForwardsAttestationHeaders() = runTest {
        val client = createMockClient { request ->
            val parameters = request.formParameters()
            assertEquals("test-client", parameters["client_id"])
            assertEquals("pre-auth-code", parameters["pre-authorized_code"])
            assertEquals(
                "attestation.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION],
            )
            assertEquals(
                "pop.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP],
            )
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
            attestationHeaders = ClientAttestationHeaders(
                attestationJwt = "attestation.jwt",
                popJwt = "pop.jwt",
            ),
        )

        assertEquals("pre-auth-token", response.access_token)
    }

    @Test
    fun testExchangePreAuthorizedCodeAnonymousRejectsClientAuthenticationHeaders() = runTest {
        val client = createMockClient {
            error("Request should fail before sending")
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        assertFailsWith<IllegalArgumentException> {
            builder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = "pre-auth-code",
                anonymous = true,
                attestationHeaders = ClientAttestationHeaders(
                    attestationJwt = "attestation.jwt",
                    popJwt = "pop.jwt",
                ),
            )
        }
    }

    @Test
    fun testRefreshAccessTokenForwardsAttestationHeaders() = runTest {
        val client = createMockClient { request ->
            val parameters = request.formParameters()
            assertEquals("refresh_token", parameters["grant_type"])
            assertEquals("refresh-token", parameters["refresh_token"])
            assertEquals("test-client", parameters["client_id"])
            assertEquals(
                "attestation.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION],
            )
            assertEquals(
                "pop.jwt",
                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP],
            )
            respond(
                content = """{"access_token":"refreshed-token","token_type":"Bearer","refresh_token":"rotated-refresh-token"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.refreshAccessToken(
            tokenEndpoint = tokenEndpoint,
            refreshToken = "refresh-token",
            attestationHeaders = ClientAttestationHeaders(
                attestationJwt = "attestation.jwt",
                popJwt = "pop.jwt",
            ),
        )

        assertEquals("refreshed-token", response.access_token)
        assertEquals("rotated-refresh-token", response.refresh_token)
    }

    @Test
    fun testRefreshAccessTokenAnonymousOmitsClientId() = runTest {
        val client = createMockClient { request ->
            val parameters = request.formParameters()
            assertEquals("refresh_token", parameters["grant_type"])
            assertEquals("refresh-token", parameters["refresh_token"])
            assertNull(parameters["client_id"])

            respond(
                content = """{"access_token":"refreshed-token","token_type":"Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        val response = builder.refreshAccessToken(
            tokenEndpoint = tokenEndpoint,
            refreshToken = "refresh-token",
            anonymous = true,
        )

        assertEquals("refreshed-token", response.access_token)
    }

    @Test
    fun testRefreshAccessTokenAnonymousRejectsClientAuthenticationHeaders() = runTest {
        val client = createMockClient {
            error("Request should fail before sending")
        }

        val builder = TokenRequestBuilder(clientConfig, client)
        assertFailsWith<IllegalArgumentException> {
            builder.refreshAccessToken(
                tokenEndpoint = tokenEndpoint,
                refreshToken = "refresh-token",
                anonymous = true,
                attestationHeaders = ClientAttestationHeaders(
                    attestationJwt = "attestation.jwt",
                    popJwt = "pop.jwt",
                ),
            )
        }
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
        val error = assertFailsWith<TokenRequestException> {
            builder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = "pre-auth-code",
                additionalHeaders = mapOf(HttpHeaders.Authorization to "Bearer abc"),
            )
        }

        assertEquals("unsafe_redirect", error.oauthError)
        assertEquals(1, callCount)
    }

    @Test
    fun testDpopNonceChallengeRegeneratesProof() = runTest {
        var callCount = 0
        val proofInputs = mutableListOf<Pair<String, String?>>()
        val client = createMockClient { request ->
            callCount += 1
            assertEquals("proof-$callCount", request.headers["DPoP"])
            if (callCount == 1) {
                respond(
                    content = "{}",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"use_dpop_nonce\""),
                        "DPoP-Nonce" to listOf("server-nonce"),
                    ),
                )
            } else {
                respond(
                    content = """{"access_token":"dpop-token","token_type":"DPoP"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        val response = TokenRequestBuilder(clientConfig, client).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = "auth-code",
            dpopProofFactory = { endpoint, nonce ->
                proofInputs += endpoint to nonce
                "proof-${proofInputs.size}"
            },
        )

        assertEquals("dpop-token", response.access_token)
        assertEquals(
            listOf(tokenEndpoint to null, tokenEndpoint to "server-nonce"),
            proofInputs,
        )
    }
}
