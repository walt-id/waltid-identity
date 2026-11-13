package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import id.waltid.openid4vci.wallet.token.TokenRequestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class WalletManualAttestationTest {
    private val issuer = "https://issuer.example"
    private val headers = mapOf(
        ClientAttestationHeaders.HEADER_ATTESTATION.lowercase() to "manual-wia",
        ClientAttestationHeaders.HEADER_ATTESTATION_POP.uppercase() to "manual-pop",
    )
    private val assembler = ClientAttestationAssembler(object : WalletAttestationProvider {
        @Suppress("DEPRECATION")
        override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String =
            error("Manual authentication must not contact the automatic WIA provider")
    })

    @Test
    fun bothGrantsAndResultOverloadsAcceptManualAuthenticationWithoutAProvider() = runTest {
        assertManualExchange(configuredAssembler = null, methods = "\"attest_jwt_client_auth\"")
    }

    @Test
    fun manualAuthenticationOverridesAutomaticAttestationAndPrivateKeyJwt() = runTest {
        assertManualExchange(configuredAssembler = assembler, methods = "\"attest_jwt_client_auth\",\"private_key_jwt\"")
    }

    @Test
    fun manualTokenExchangeDoesNotRequireAWalletSigningKey() = runTest {
        var keyStoreCalls = 0
        val unavailableStore = object : WalletKeyStore {
            override suspend fun getKey(keyId: String): Key? = error("Unused key lookup")
            override suspend fun listKeys(): Flow<WalletKeyInfo> {
                keyStoreCalls++
                error("Signing key store unavailable")
            }
            override suspend fun addKey(key: Key): String = error("Unused key write")
            override suspend fun removeKey(keyId: String): Boolean = error("Unused key deletion")
        }
        var calls = 0
        client("\"attest_jwt_client_auth\"") { request ->
            calls++
            assertManualRequest(request)
            jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
        }.use {
            val wallets = listOf(Wallet("empty"), Wallet("unavailable", keyStores = listOf(unavailableStore)))
            for (wallet in wallets) for (authorizationCode in listOf(false, true)) for (detailed in listOf(false, true)) {
                assertEquals("access", exchange(wallet, authorizationCode, detailed, it, assembler))
            }
            assertEquals(8, calls)
            assertEquals(0, keyStoreCalls)
        }
    }

    private suspend fun assertManualExchange(configuredAssembler: ClientAttestationAssembler?, methods: String) {
        val wallet = Wallet("manual", staticKey = JWKKey.generate(KeyType.secp256r1))
        for (authorizationCode in listOf(false, true)) for (detailed in listOf(false, true)) {
            var tokenCalls = 0
            client(methods) { request ->
                tokenCalls++
                assertManualRequest(request)
                jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
            }.use {
                val result = exchange(wallet, authorizationCode, detailed, it, configuredAssembler)
                assertEquals("access", result)
                assertEquals(1, tokenCalls)
            }
        }
    }

    @Test
    fun rejectedManualAuthenticationNeverFallsBack() = runTest {
        val wallet = Wallet("manual", staticKey = JWKKey.generate(KeyType.secp256r1))
        for (authorizationCode in listOf(false, true)) {
            var calls = 0
            client("\"attest_jwt_client_auth\",\"private_key_jwt\",\"none\"", anonymous = true) { request ->
                calls++
                assertManualRequest(request)
                jsonResponse("""{"error":"invalid_client"}""", HttpStatusCode.Unauthorized)
            }.use {
                assertEquals("invalid_client", assertFailsWith<TokenRequestException> {
                    exchange(wallet, authorizationCode, true, it, assembler)
                }.oauthError)
                assertEquals(1, calls)
            }
        }
    }

    @Test
    fun noManualHeadersPreservePrivateKeyJwtAndAnonymousAccess() = runTest {
        val wallet = Wallet("alternatives", staticKey = JWKKey.generate(KeyType.secp256r1))
        client("\"private_key_jwt\"") { request ->
            val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertNotNull(form["client_assertion"])
            assertNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION])
            jsonResponse("""{"access_token":"private-key-access","token_type":"Bearer"}""")
        }.use {
            assertEquals("private-key-access", WalletIssuanceHandler.exchangeCode(wallet,
                authorized().copy(tokenRequestHeaders = emptyMap()), httpClient = it).accessToken)
        }
        client("\"attest_jwt_client_auth\"", anonymous = true) { request ->
            val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertNull(form["client_id"])
            assertNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION])
            jsonResponse("""{"access_token":"anonymous-access","token_type":"Bearer"}""")
        }.use {
            assertEquals("anonymous-access", WalletIssuanceHandler.requestToken(wallet,
                preAuthorized().copy(tokenRequestHeaders = emptyMap()), httpClient = it).accessToken)
        }
    }

    @Test
    fun absentManualPairStillRequiresProviderWhenAnonymousAccessIsUnavailable() = runTest {
        client("\"attest_jwt_client_auth\"") { error("Missing authentication must not reach the token endpoint") }.use {
            assertFailsWith<WalletInstanceAttestationRequiredException> {
                WalletIssuanceHandler.requestToken(Wallet("empty"),
                    preAuthorized().copy(tokenRequestHeaders = emptyMap()), httpClient = it)
            }
        }
    }

    @Test
    fun explicitAnonymousModeCannotBeCombinedWithManualAttestation() = runTest {
        client("\"attest_jwt_client_auth\"", anonymous = true) {
            error("Conflicting authentication must not reach the token endpoint")
        }.use {
            assertFailsWith<IllegalArgumentException> {
                WalletIssuanceHandler.requestToken(Wallet("empty"),
                    preAuthorized().copy(anonymousPreAuthorizedCode = true), assembler, it)
            }
        }
    }

    @Test
    fun malformedPairsAreRejectedByWalletlessAndWalletAwareOverloads() = runTest {
        val malformed = mapOf(ClientAttestationHeaders.HEADER_ATTESTATION to "manual-wia")
        val preAuthorized = preAuthorized().copy(tokenRequestHeaders = malformed)
        val authorized = authorized().copy(tokenRequestHeaders = malformed)
        assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.requestToken(preAuthorized) }
        assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.requestTokenDetailed(preAuthorized) }
        assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.exchangeCode(authorized) }
        assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.exchangeCodeDetailed(authorized) }
        val wallet = Wallet("empty")
        HttpClient(MockEngine) { engine { addHandler { error("Malformed input must not perform HTTP requests") } } }.use {
            assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.requestToken(wallet, preAuthorized, assembler, it) }
            assertFailsWith<IllegalArgumentException> { WalletIssuanceHandler.exchangeCode(wallet, authorized, assembler, it) }
        }
    }

    @Test
    fun walletlessOverloadsForwardManualHeadersForBothGrants() = runTest {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${server.address.port}"
        server.createContext("/") { request ->
            val body = when (request.requestURI.path) {
                "/.well-known/openid-credential-issuer" -> issuerMetadata(base)
                "/.well-known/oauth-authorization-server" -> asMetadata(base, "\"attest_jwt_client_auth\"")
                "/token" -> {
                    calls.incrementAndGet()
                    assertEquals("manual-wia", request.requestHeaders.getFirst(ClientAttestationHeaders.HEADER_ATTESTATION))
                    assertEquals("manual-pop", request.requestHeaders.getFirst(ClientAttestationHeaders.HEADER_ATTESTATION_POP))
                    request.requestBody.use { it.readBytes() }
                    """{"access_token":"access","token_type":"Bearer"}"""
                }
                else -> error("Unexpected request ${request.requestURI}")
            }.toByteArray()
            request.responseHeaders.add(HttpHeaders.ContentType, "application/json")
            request.sendResponseHeaders(200, body.size.toLong())
            request.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val preAuthorized = preAuthorized().copy(tokenEndpoint = Url("$base/token"), credentialIssuer = base)
            val authorized = authorized().copy(credentialIssuerBaseUrl = base)
            assertEquals("access", WalletIssuanceHandler.requestToken(preAuthorized).accessToken)
            assertEquals("access", WalletIssuanceHandler.requestTokenDetailed(preAuthorized).accessToken)
            assertEquals("access", WalletIssuanceHandler.exchangeCode(authorized).accessToken)
            assertEquals("access", WalletIssuanceHandler.exchangeCodeDetailed(authorized).accessToken)
            assertEquals(4, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fullPreAuthorizedIssuanceUsesManualHeadersWithoutFallback() = runTest {
        var calls = 0
        val wallet = Wallet("manual-full", staticKey = JWKKey.generate(KeyType.secp256r1))
        client("\"attest_jwt_client_auth\"") { request ->
            calls++
            assertManualRequest(request)
            jsonResponse("""{"error":"invalid_client"}""", HttpStatusCode.Unauthorized)
        }.use {
            val offer = Json.parseToJsonElement("""{
                "credential_issuer":"$issuer","credential_configuration_ids":["identity"],
                "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}
            }""") as kotlinx.serialization.json.JsonObject
            assertFailsWith<TokenRequestException> {
                WalletIssuanceHandler.receiveCredentials(wallet,
                    ReceiveCredentialRequest(offerJson = offer, clientId = "manual-client", tokenRequestHeaders = headers),
                    assembler, httpClient = it)
            }
            assertEquals(1, calls)
            val preview = WalletIssuanceHandler.previewOffer(wallet, ResolveOfferRequest(offerJson = offer), it)
            try {
                assertFailsWith<TokenRequestException> {
                    WalletIssuanceHandler.receiveCredentials(wallet,
                        ReceiveCredentialFromPreviewRequest(preview.previewHandle,
                            clientId = "manual-client", tokenRequestHeaders = headers),
                        assembler, httpClient = it)
                }
                assertEquals(2, calls)
            } finally {
                WalletIssuanceHandler.discardPreview(wallet, preview.previewHandle)
            }
        }
    }

    private fun assertManualRequest(request: HttpRequestData) {
        assertEquals(listOf("manual-wia"), request.headers.getAll(ClientAttestationHeaders.HEADER_ATTESTATION))
        assertEquals(listOf("manual-pop"), request.headers.getAll(ClientAttestationHeaders.HEADER_ATTESTATION_POP))
        val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals("manual-client", form["client_id"])
        assertNull(form["client_assertion"])
        assertNull(form["client_assertion_type"])
    }

    private suspend fun exchange(wallet: Wallet, authorizationCode: Boolean, detailed: Boolean,
        client: HttpClient, configuredAssembler: ClientAttestationAssembler?): String =
        if (authorizationCode) {
            if (detailed) WalletIssuanceHandler.exchangeCodeDetailed(wallet, authorized(), configuredAssembler, client).accessToken
            else WalletIssuanceHandler.exchangeCode(wallet, authorized(), configuredAssembler, client).accessToken
        } else {
            if (detailed) WalletIssuanceHandler.requestTokenDetailed(wallet, preAuthorized(), configuredAssembler, client).accessToken
            else WalletIssuanceHandler.requestToken(wallet, preAuthorized(), configuredAssembler, client).accessToken
        }

    private fun preAuthorized() = RequestTokenRequest(Url("$issuer/token"), "pre-code", credentialIssuer = issuer,
        clientId = "manual-client", tokenRequestHeaders = headers)
    private fun authorized() = ExchangeCodeRequest("code", issuer, clientId = "manual-client", tokenRequestHeaders = headers)

    private fun client(methods: String, anonymous: Boolean = false,
        token: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) = HttpClient(MockEngine) {
        engine { addHandler { request -> when (request.url.toString()) {
            "$issuer/.well-known/openid-credential-issuer" -> jsonResponse(issuerMetadata(issuer))
            "$issuer/.well-known/oauth-authorization-server" -> jsonResponse(asMetadata(issuer, methods, anonymous))
            "$issuer/token" -> token(request)
            else -> error("Unexpected request ${request.url}")
        } } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun issuerMetadata(base: String) = """{"credential_issuer":"$base","credential_endpoint":"$base/credential",
        "credential_configurations_supported":{"identity":{"format":"jwt_vc_json","scope":"identity"}}}"""
    private fun asMetadata(base: String, methods: String, anonymous: Boolean = false) = """{"issuer":"$base","token_endpoint":"$base/token",
        "authorization_endpoint":"$base/authorize","response_types_supported":["code"],
        "token_endpoint_auth_methods_supported":[$methods],"token_endpoint_auth_signing_alg_values_supported":["ES256"],
        "client_attestation_signing_alg_values_supported":["ES256"],"client_attestation_pop_signing_alg_values_supported":["ES256"],
        "pre-authorized_grant_anonymous_access_supported":$anonymous}"""
    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
}
