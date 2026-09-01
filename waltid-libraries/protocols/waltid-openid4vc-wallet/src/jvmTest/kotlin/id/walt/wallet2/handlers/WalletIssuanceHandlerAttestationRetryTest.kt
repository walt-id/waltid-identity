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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletIssuanceHandlerAttestationRetryTest {

    @Test
    fun dpopNonceRetryReusesAttestationJwtAndMintsFreshPopJti() = runTest {
        val provider = StaticAttestationProvider()
        val attestationJwts = mutableListOf<String>()
        val popJwts = mutableListOf<String>()
        var tokenCalls = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        ISSUER_METADATA -> respondJson(issuerMetadata())
                        AS_METADATA -> respondJson(authorizationServerMetadata())
                        TOKEN_ENDPOINT -> {
                            tokenCalls += 1
                            assertNotNull(request.headers["DPoP"])
                            attestationJwts += requireNotNull(
                                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION]
                            )
                            popJwts += requireNotNull(
                                request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP]
                            )
                            if (tokenCalls == 1) {
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.Unauthorized,
                                    headers = headersOf(
                                        HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"use_dpop_nonce\""),
                                        "DPoP-Nonce" to listOf("server-nonce"),
                                    ),
                                )
                            } else {
                                respondJson("""{"access_token":"access","token_type":"DPoP"}""")
                            }
                        }
                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = WalletIssuanceHandler.exchangeCode(
            wallet = Wallet(id = "attestation-retry", staticKey = JWKKey.generate(KeyType.secp256r1)),
            request = ExchangeCodeRequest(
                code = "auth-code",
                credentialIssuerBaseUrl = ISSUER,
            ),
            attestationAssembler = ClientAttestationAssembler(provider),
            httpClient = client,
            useDpop = true,
        )

        assertEquals("access", result.accessToken)
        assertEquals("DPoP", result.tokenType)
        assertEquals(2, tokenCalls)
        assertEquals(1, provider.calls)
        assertEquals(listOf(ATTESTATION_JWT, ATTESTATION_JWT), attestationJwts)
        assertEquals(2, popJwts.size)
        assertNotEquals(jwtClaim(popJwts[0], "jti"), jwtClaim(popJwts[1], "jti"))
        assertTrue(jwtClaim(popJwts[0], "jti").isNotBlank())
        assertTrue(jwtClaim(popJwts[1], "jti").isNotBlank())
    }

    private fun jwtClaim(jwt: String, name: String): String =
        Json.parseToJsonElement(jwt.split('.')[1].decodeFromBase64Url().decodeToString())
            .jsonObject[name]!!.jsonPrimitive.content

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun issuerMetadata() = """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$ISSUER/credential",
          "credential_configurations_supported":{
            "test-credential":{"format":"jwt_vc_json"}
          }
        }
    """.trimIndent()

    private fun authorizationServerMetadata() = """
        {
          "issuer":"$ISSUER",
          "authorization_endpoint":"$ISSUER/authorize",
          "token_endpoint":"$TOKEN_ENDPOINT",
          "response_types_supported":["code"],
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
        const val ATTESTATION_JWT =
            "eyJhbGciOiJFUzI1NiJ9.e30.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }

    @Suppress("DEPRECATION")
    private class StaticAttestationProvider : WalletAttestationProvider {
        var calls = 0
            private set

        override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
            calls += 1
            return ATTESTATION_JWT
        }
    }
}
