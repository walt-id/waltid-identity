package id.walt.itb

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.wallet2.handlers.WalletIssuanceHandler
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IssuanceProfileTest {
    @Test
    fun `CS01 bound authorization-code proof carries the client id required by the reported ITB flow`() = runTest {
        val fixture = WalletFixtures()
        var proofPayload: JsonObject? = null
        val clientId = "itb-test-wallet"
        val issuer = WalletFixtures.ISSUER
        val holderKey = fixture.issuer.holderCrypto2Key()
        val metadata = """{
          "credential_issuer":"$issuer", "credential_endpoint":"$issuer/credential",
          "nonce_endpoint":"$issuer/nonce",
          "credential_configurations_supported":{"identity":{
            "format":"dc+sd-jwt", "vct":"${WalletFixtures.IDENTITY_VCT}",
            "cryptographic_binding_methods_supported":["jwk"],
            "proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}
          }}
        }"""
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine { addHandler { request: HttpRequestData ->
                val body = when (request.url.encodedPath) {
                    "/.well-known/openid-credential-issuer" -> metadata
                    "/.well-known/oauth-authorization-server", "/.well-known/openid-configuration" ->
                        """{"issuer":"$issuer","token_endpoint":"$issuer/token","authorization_endpoint":"$issuer/authorize","response_types_supported":["code"],"grant_types_supported":["authorization_code"],"token_endpoint_auth_methods_supported":["none"]}"""
                    "/token" -> """{"access_token":"synthetic-token","token_type":"Bearer"}"""
                    "/nonce" -> """{"c_nonce":"itb-proof-nonce"}"""
                    "/credential" -> {
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        val proof = body.getValue("proofs").jsonObject.getValue("jwt").jsonArray.single().jsonPrimitive.content
                        proofPayload = Json.parseToJsonElement(
                            CompactJws.verify(proof, holderKey, JwsAlgorithm.ES256).payload.decodeToString()
                        ).jsonObject
                        buildJsonObject { put("credentials", buildJsonArray {
                            add(buildJsonObject { put("credential", fixture.credential()) })
                        }) }.toString()
                    }
                    else -> error("Unexpected fixture request: ${request.url.encodedPath}")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } }
        }.use { client ->
            val received = WalletIssuanceHandler.receiveCredentialAuthCodeFlow(
                wallet = fixture.wallet(), code = "synthetic-code", codeVerifier = "synthetic-pkce-verifier",
                credentialIssuerBaseUrl = issuer, credentialEndpoint = Url("$issuer/credential"),
                credentialConfigurationId = "identity", clientId = clientId, httpClient = client,
            ).toList()
            assertEquals(1, received.size)
        }
        val proof = assertNotNull(proofPayload, "The real wallet must reach the credential endpoint")
        assertEquals(issuer, proof["aud"]?.jsonPrimitive?.content)
        assertEquals("itb-proof-nonce", proof["nonce"]?.jsonPrimitive?.content)
        // OpenID4VCI makes iss optional here. This pins the stricter observed ITB interoperability
        // requirement, not a new universal issuer validation rule. External fix: WAL-495 / #2246.
        assertEquals(clientId, proof["iss"]?.jsonPrimitive?.content, "WAL-495 / Identity #2246")
    }
}
