package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.wallet2.data.Wallet
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletIssuanceHandlerAttestationChallengeTest {

    @Test
    fun tokenRequestFetchesChallengeEndpointAndPutsItOnThePop() = runTest {
        var challengeCalls = 0
        val popJwts = mutableListOf<String>()
        val client = httpClient { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> respondJson(issuerMetadata())
                AS_METADATA -> respondJson(authorizationServerMetadata())
                CHALLENGE_ENDPOINT -> {
                    assertEquals(HttpMethod.Post, request.method)
                    challengeCalls += 1
                    respondJson("""{"attestation_challenge":"$CHALLENGE"}""")
                }
                TOKEN_ENDPOINT -> {
                    popJwts += requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP])
                    respondJson("""{"access_token":"access","token_type":"DPoP"}""")
                }
                else -> error("Unexpected request: ${request.method.value} ${request.url}")
            }
        }

        val result = WalletIssuanceHandler.exchangeCode(
            wallet = Wallet(id = "attestation-challenge", staticKey = JWKKey.generate(KeyType.secp256r1)),
            request = ExchangeCodeRequest(
                code = "auth-code",
                credentialIssuerBaseUrl = ISSUER,
            ),
            attestationAssembler = ClientAttestationAssembler(StaticAttestationProvider()),
            httpClient = client,
            useDpop = true,
        )

        assertEquals("access", result.accessToken)
        assertEquals(1, challengeCalls)
        assertEquals(CHALLENGE, jwtClaim(popJwts.single(), "challenge"))
    }

    @Test
    fun parFetchesChallengeEndpointAndPutsItOnThePop() = runTest {
        var challengeCalls = 0
        var parPop: String? = null
        val client = httpClient { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> respondJson(issuerMetadata())
                AS_METADATA -> respondJson(authorizationServerMetadata())
                CHALLENGE_ENDPOINT -> {
                    assertEquals(HttpMethod.Post, request.method)
                    challengeCalls += 1
                    respondJson("""{"attestation_challenge":"$CHALLENGE"}""")
                }
                PAR_ENDPOINT -> {
                    assertEquals(HttpMethod.Post, request.method)
                    parPop = requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP])
                    respond(
                        content = """{"request_uri":"urn:ietf:params:oauth:request_uri:abc","expires_in":60}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected request: ${request.method.value} ${request.url}")
            }
        }

        val result = WalletIssuanceHandler.generateAuthorizationUrl(
            wallet = Wallet(id = "attestation-challenge-par", staticKey = JWKKey.generate(KeyType.secp256r1)),
            request = GenerateAuthorizationUrlRequest(offerJson = offerJson()),
            attestationAssembler = ClientAttestationAssembler(StaticAttestationProvider()),
            httpClient = client,
        )

        assertTrue(result.authorizationUrl.toString().contains("request_uri"))
        assertEquals(1, challengeCalls)
        assertEquals(CHALLENGE, jwtClaim(requireNotNull(parPop), "challenge"))
    }

    private fun jwtClaim(jwt: String, name: String): String =
        Json.parseToJsonElement(jwt.split('.')[1].decodeFromBase64Url().decodeToString())
            .jsonObject[name]!!.jsonPrimitive.content

    private fun httpClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun offerJson() = buildJsonObject {
        put("credential_issuer", ISSUER)
        putJsonArray("credential_configuration_ids") { add("test-credential") }
        putJsonObject("grants") {
            putJsonObject("authorization_code") { put("issuer_state", "issuer-state") }
        }
    }

    private fun issuerMetadata() = """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$ISSUER/credential",
          "credential_configurations_supported":{
            "test-credential":{"format":"jwt_vc_json","scope":"pid"}
          }
        }
    """.trimIndent()

    private fun authorizationServerMetadata() = """
        {
          "issuer":"$ISSUER",
          "authorization_endpoint":"$ISSUER/authorize",
          "token_endpoint":"$TOKEN_ENDPOINT",
          "pushed_authorization_request_endpoint":"$PAR_ENDPOINT",
          "require_pushed_authorization_requests":true,
          "challenge_endpoint":"$CHALLENGE_ENDPOINT",
          "response_types_supported":["code"],
          "code_challenge_methods_supported":["S256"],
          "token_endpoint_auth_methods_supported":["attest_jwt_client_auth"],
          "client_attestation_signing_alg_values_supported":["ES256"],
          "client_attestation_pop_signing_alg_values_supported":["ES256"],
          "dpop_signing_alg_values_supported":["ES256"]
        }
    """.trimIndent()

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val ISSUER_METADATA = "$ISSUER/.well-known/openid-credential-issuer"
        const val AS_METADATA = "$ISSUER/.well-known/oauth-authorization-server"
        const val TOKEN_ENDPOINT = "$ISSUER/token"
        const val PAR_ENDPOINT = "$ISSUER/par"
        const val CHALLENGE_ENDPOINT = "$ISSUER/challenge"
        const val CHALLENGE = "suite-challenge"
        const val ATTESTATION_JWT =
            "eyJhbGciOiJFUzI1NiJ9.e30.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }

    @Suppress("DEPRECATION")
    private class StaticAttestationProvider : WalletAttestationProvider {
        override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String = ATTESTATION_JWT
    }
}
