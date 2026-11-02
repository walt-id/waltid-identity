package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.wallet2.data.Wallet
import id.waltid.openid4vci.wallet.attestation.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletAttestationFreshnessTest {
    private val issuer = "https://issuer.example"
    private val metadata = """{"issuer":"https://issuer.example","token_endpoint":"https://issuer.example/token","authorization_endpoint":"https://issuer.example/authorize","pushed_authorization_request_endpoint":"https://issuer.example/par","response_types_supported":["code"],"authorization_details_types_supported":["openid_credential"],"token_endpoint_auth_methods_supported":["attest_jwt_client_auth"],"client_attestation_signing_alg_values_supported":["ES256"],"client_attestation_pop_signing_alg_values_supported":["ES256"]}"""
    private val issuerMetadata = """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential","deferred_credential_endpoint":"https://issuer.example/deferred","credential_configurations_supported":{"identity":{"format":"jwt_vc_json","credential_definition":{"type":["VerifiableCredential","Identity"]}}}}"""
    private fun request(authorized: Boolean) = WalletIssuanceSessionRequest(
        offerJson = Json.parseToJsonElement("""{"credential_issuer":"$issuer","credential_configuration_ids":["identity"],"grants":${if (authorized) "{\"authorization_code\":{}}" else "{\"urn:ietf:params:oauth:grant-type:pre-authorized_code\":{\"pre-authorized_code\":\"code\"}}"}}""").jsonObject,
        clientId = "wallet", redirectUri = Url("openid://callback"))
    private fun claims(request: io.ktor.client.request.HttpRequestData) = Json.parseToJsonElement(CompactJws.decodeUnverified(
        requireNotNull(request.headers["OAuth-Client-Attestation-PoP"])).payload.decodeToString()).jsonObject
    private suspend fun service(http: HttpClient,
        wallet: Wallet? = null,
        store: WalletIssuanceSessionStore? = null) = WalletIssuanceSessionService(
        wallet ?: Wallet("wallet", staticKey = JWKKey.generate(KeyType.secp256r1)), sessionStore = store,
        attestationAssembler = ClientAttestationAssembler(object : WalletAttestationProvider {
            override suspend fun getAttestationJwt(instanceKey: Key, clientId: String) =
                "eyJhbGciOiJFUzI1NiJ9.e30.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        }), httpClient = http)
    private fun client(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpClient(MockEngine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }; engine { addHandler(handler) } }
    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK, challenge: String? = null) =
        respond(body, status, Headers.build { append(HttpHeaders.ContentType, "application/json"); challenge?.let { append("OAuth-Client-Attestation-Challenge", it) } })

    @Test fun retainsChallengeFromAuthorizationServerMetadataResponse() = runTest {
        var tokenChallenge: String? = null
        val http = client { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-credential-issuer" -> jsonResponse(issuerMetadata)
                "/.well-known/oauth-authorization-server" -> jsonResponse(metadata, challenge = "metadata-challenge")
                "/token" -> { tokenChallenge = claims(request)["challenge"]?.jsonPrimitive?.content; jsonResponse("""{"access_token":"access","token_type":"Bearer"}""") }
                "/credential" -> jsonResponse("""{"transaction_id":"tx","interval":5}""", HttpStatusCode.Accepted)
                else -> error("Unexpected request ${request.url}")
            }
        }
        try {
            val engine = service(http)
            assertIs<WalletIssuanceOutcome.Deferred>(engine.continuePreAuthorized(engine.start(request(false)).id))
            assertEquals("metadata-challenge", tokenChallenge)
        } finally { http.close() }
    }

    @Test fun metadataFallbackFreshnessReachesProofsForBothGrants() = runTest {
        for (authorized in listOf(false, true)) {
            var tokenRequests = 0
            var parRequests = 0
            val http = client { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-credential-issuer" -> jsonResponse(issuerMetadata)
                    "/.well-known/oauth-authorization-server" -> respond("{}", HttpStatusCode.NotFound, Headers.build {
                        append("OAuth-Client-Attestation-Challenge", "fallback-challenge")
                        append("DPoP-Nonce", "fallback-nonce")
                    })
                    "/.well-known/openid-configuration" -> jsonResponse(JsonObject(
                        Json.parseToJsonElement(metadata).jsonObject + mapOf(
                            "challenge_endpoint" to JsonPrimitive("$issuer/challenge"),
                            "dpop_signing_alg_values_supported" to buildJsonArray { add("ES256") })).toString())
                    "/par" -> {
                        parRequests++
                        assertEquals("fallback-challenge", claims(request)["challenge"]?.jsonPrimitive?.content)
                        jsonResponse("""{"request_uri":"urn:par:fallback","expires_in":60}""", HttpStatusCode.Created)
                    }
                    "/token" -> {
                        tokenRequests++
                        assertEquals("fallback-challenge", claims(request)["challenge"]?.jsonPrimitive?.content)
                        val dpop = Json.parseToJsonElement(CompactJws.decodeUnverified(
                            requireNotNull(request.headers["DPoP"])).payload.decodeToString()).jsonObject
                        assertEquals("fallback-nonce", dpop["nonce"]?.jsonPrimitive?.content)
                        jsonResponse("""{"access_token":"access","token_type":"DPoP"}""")
                    }
                    "/credential" -> jsonResponse("""{"transaction_id":"tx","interval":5}""", HttpStatusCode.Accepted)
                    else -> error("Unexpected request ${request.url}")
                }
            }
            try {
                val engine = service(http)
                val session = engine.start(request(authorized))
                val outcome = if (authorized) {
                    val authorization = engine.beginAuthorization(session.id)
                    engine.continueAuthorization(WalletIssuanceAuthorizationCallback(
                        session.id, "openid://callback?code=code&state=${authorization.state}"))
                } else engine.continuePreAuthorized(session.id)
                assertIs<WalletIssuanceOutcome.Deferred>(outcome)
                assertEquals(1, tokenRequests)
                assertEquals(if (authorized) 1 else 0, parRequests)
            } finally { http.close() }
        }
    }

    @Test fun latestChallengeIsSharedBetweenSessionsOfSameClientInstance() = runTest {
        val parChallenges = mutableListOf<String?>()
        var tokenChallenge: String? = null
        val http = client { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-credential-issuer" -> jsonResponse(issuerMetadata)
                "/.well-known/oauth-authorization-server" -> jsonResponse(metadata)
                "/par" -> {
                    parChallenges += claims(request)["challenge"]?.jsonPrimitive?.content
                    jsonResponse("""{"request_uri":"urn:par:${parChallenges.size}","expires_in":60}""", HttpStatusCode.Created, "challenge-${parChallenges.size}")
                }
                "/token" -> { tokenChallenge = claims(request)["challenge"]?.jsonPrimitive?.content; jsonResponse("""{"access_token":"access","token_type":"Bearer"}""") }
                "/credential" -> jsonResponse("""{"transaction_id":"tx","interval":5}""", HttpStatusCode.Accepted)
                else -> error("Unexpected request ${request.url}")
            }
        }
        try {
            val wallet = Wallet("wallet", staticKey = JWKKey.generate(KeyType.secp256r1))
            val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
            val engine = service(http, wallet, store)
            val first = engine.start(request(true))
            val auth = engine.beginAuthorization(first.id)
            val second = engine.start(request(true))
            engine.beginAuthorization(second.id)
            assertIs<WalletIssuanceOutcome.Deferred>(service(http, wallet, store).continueAuthorization(WalletIssuanceAuthorizationCallback(
                first.id, "openid://callback?code=code&state=${auth.state}")))
            assertEquals(listOf(null, "challenge-1", "challenge-2"), parChallenges + tokenChallenge)
        } finally { http.close() }
    }

    @Test fun nonceOnlyDiscoveryIsUsedByTheFirstDpopProof() = runTest {
        var discoveries = 0
        val http = client { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-credential-issuer" -> jsonResponse(issuerMetadata)
                "/.well-known/oauth-authorization-server" -> jsonResponse(JsonObject(
                    Json.parseToJsonElement(metadata).jsonObject + mapOf(
                        "challenge_endpoint" to JsonPrimitive("$issuer/challenge"),
                        "dpop_signing_alg_values_supported" to buildJsonArray { add("ES256") })).toString())
                "/challenge" -> {
                    discoveries++
                    respond("{}", HttpStatusCode.OK, headersOf("DPoP-Nonce", "nonce-only"))
                }
                "/token" -> {
                    assertNull(claims(request)["challenge"])
                    val dpop = Json.parseToJsonElement(CompactJws.decodeUnverified(
                        requireNotNull(request.headers["DPoP"])).payload.decodeToString()).jsonObject
                    assertEquals("nonce-only", dpop["nonce"]?.jsonPrimitive?.content)
                    jsonResponse("""{"access_token":"access","token_type":"DPoP"}""")
                }
                "/credential" -> jsonResponse("""{"transaction_id":"tx","interval":5}""", HttpStatusCode.Accepted)
                else -> error("Unexpected request ${request.url}")
            }
        }
        try {
            val engine = service(http)
            assertIs<WalletIssuanceOutcome.Deferred>(engine.continuePreAuthorized(engine.start(request(false)).id))
            assertEquals(1, discoveries)
        } finally { http.close() }
    }

}
