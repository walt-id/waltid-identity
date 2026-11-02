package id.walt.wallet2.mobile

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.stores.inmemory.*
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Public SDK operations over the real session engine and Crypto2 signer. */
class MobileWalletAttestationChallengeTest {
    @Test fun bothGrantsAndDiscoveryModesUseFreshProofsAndRestoreParState() = runTest {
        for (authorized in listOf(false, true)) for (discovery in listOf(false, true)) {
            val keys = InMemoryMobileWalletKeyStore()
            val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(KeyId("instance"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
            keys.addCrypto2Key(key)
            val records = InMemoryIssuanceSessionStore()
            val dids = InMemoryDidStore()
            val credentials = InMemoryCredentialStore()
            val forms = mutableMapOf<String, MutableList<String>>()
            val pops = mutableMapOf<String, MutableList<JsonObject>>()
            var discoveries = 0
            var attesterCalls = 0
            val attestation = CompactJws.sign(
                payload = "{}".encodeToByteArray(), key = key, algorithm = JwsAlgorithm.ES256)
            val provider = object : WalletAttestationProvider {
                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override suspend fun getAttestationJwt(instanceKey: id.walt.crypto.keys.Key, clientId: String): String =
                    error("Mobile attestation must use the Crypto2 public-key API")

                override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String {
                    attesterCalls++
                    return attestation
                }
            }
            val http = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                engine { addHandler { request ->
                    val path = request.url.encodedPath
                    val headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, "application/json") }
                    var status = HttpStatusCode.OK
                    val body = when (path) {
                        "/.well-known/openid-credential-issuer" -> """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential","deferred_credential_endpoint":"https://issuer.example/deferred","credential_configurations_supported":{"identity":{"format":"jwt_vc_json","credential_definition":{"type":["VerifiableCredential","Identity"]}}}}"""
                        "/.well-known/oauth-authorization-server" -> buildJsonObject {
                            put("issuer", "https://issuer.example")
                            put("token_endpoint", "https://issuer.example/token")
                            put("authorization_endpoint", "https://issuer.example/authorize")
                            put("pushed_authorization_request_endpoint", "https://issuer.example/par")
                            put("require_pushed_authorization_requests", true)
                            put("response_types_supported", buildJsonArray { add("code") })
                            put("authorization_details_types_supported", buildJsonArray { add("openid_credential") })
                            put("token_endpoint_auth_methods_supported", buildJsonArray { add("attest_jwt_client_auth") })
                            put("client_attestation_signing_alg_values_supported", buildJsonArray { add("ES256") })
                            put("client_attestation_pop_signing_alg_values_supported", buildJsonArray { add("ES256") })
                            if (discovery) put("challenge_endpoint", "https://issuer.example/challenge")
                        }.toString()
                        "/challenge" -> { discoveries++; """{"attestation_challenge":"endpoint-challenge"}""" }
                        "/par", "/token" -> {
                            forms.getOrPut(path) { mutableListOf() } += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            val claims = Json.parseToJsonElement(CompactJws.decodeUnverified(
                                assertNotNull(request.headers["OAuth-Client-Attestation-PoP"])).payload.decodeToString()).jsonObject
                            pops.getOrPut(path) { mutableListOf() } += claims
                            assertEquals("https://issuer.example", claims["aud"]?.jsonPrimitive?.content)
                            assertEquals(attestation, request.headers["OAuth-Client-Attestation"])
                            val attempts = forms.getValue(path).size
                            if (!discovery && attempts == 1) {
                                if (path == "/token" && authorized) assertEquals("after-par", claims["challenge"]?.jsonPrimitive?.content)
                                status = HttpStatusCode.BadRequest
                                headers.append("OAuth-Client-Attestation-Challenge", "retry-$path")
                                """{"error":"use_attestation_challenge"}"""
                            } else {
                                assertEquals(if (discovery) {
                                    if (path == "/token" && authorized) "after-par" else "endpoint-challenge"
                                } else "retry-$path", claims["challenge"]?.jsonPrimitive?.content)
                                if (path == "/par") {
                                    status = HttpStatusCode.Created
                                    headers.append("OAuth-Client-Attestation-Challenge", "after-par")
                                    headers.append("DPoP-Nonce", "separate-as-nonce")
                                    """{"request_uri":"urn:test:request","expires_in":60}"""
                                } else """{"access_token":"access","token_type":"Bearer"}"""
                            }
                        }
                        "/credential" -> { status = HttpStatusCode.Accepted; """{"transaction_id":"deferred","interval":5}""" }
                        else -> error("Unexpected request ${request.url}")
                    }
                    respond(body, status, headers.build())
                } }
            }
            fun openWallet() = MobileWallet(walletId = "challenge", keyStore = keys, didStore = dids,
                credentialStore = credentials, issuanceSessionStore = records, issuanceHttpClient = http, attestationProvider = provider)
            repeat(2) { iteration ->
                forms.clear()
                pops.clear()
                val wallet = openWallet()
                val grants = if (authorized) """{"authorization_code":{}}""" else
                    """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"single-use-code"}}"""
                val session = wallet.startIssuance(MobileWalletIssuanceRequest(
                    offer = MobileWalletCredentialOffer.InlineJson("""{"credential_issuer":"https://issuer.example","credential_configuration_ids":["identity"],"grants":$grants}"""),
                    clientId = "wallet", redirectUri = "openid://callback", keyId = "instance"))
                val outcome = if (authorized) {
                    val authorization = wallet.beginAuthorizationIssuance(session.id)
                    assertNotNull(Url(authorization.url).parameters["request_uri"])
                    // A new SDK object must recover freshness and PKCE from the persisted session.
                    openWallet().continueAuthorizationIssuance(session.id, "openid://callback?code=code&state=${authorization.state}")
                } else wallet.continuePreAuthorizedIssuance(session.id)
                assertIs<WalletIssuanceOutcome.Deferred>(outcome)
                assertEquals(if (discovery) iteration + 1 else 0, discoveries)
                assertEquals((if (authorized) 2 else 1) * (iteration + 1), attesterCalls)
                forms.forEach { (path, requests) ->
                    assertEquals(if (discovery) 1 else 2, requests.size)
                    assertEquals(1, requests.distinct().size)
                    assertEquals(requests.size, pops.getValue(path).map { it["jti"] }.distinct().size)
                }
            }
            http.close()
        }
    }
}
