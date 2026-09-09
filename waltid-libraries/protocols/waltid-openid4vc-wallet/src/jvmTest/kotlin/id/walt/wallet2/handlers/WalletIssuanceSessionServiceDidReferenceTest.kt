package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the three-tier DID resolution chain inside [WalletIssuanceSessionService.start]:
 *   1. inline [WalletIssuanceSessionRequest.did] takes precedence
 *   2. [WalletIssuanceSessionRequest.didReference] is resolved from [Wallet.didStore]
 *   3. falls back to [Wallet.defaultDid] (→ null if no default, meaning JWK binding)
 */
class WalletIssuanceSessionServiceDidReferenceTest {

    @Test
    fun `didReference resolves DID from store and uses it for proof binding`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val holderDid = "did:jwk:ref-holder"
        val holderDidKeyId = "$holderDid#0"
        val didStore = InMemoryDidStore().also { store ->
            store.addDid(
                WalletDidEntry(
                    did = holderDid,
                    document = didDocument(holderDid, holderDidKeyId, key),
                )
            )
        }
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true)
                        .replace(
                            "\"cryptographic_binding_methods_supported\":[\"jwk\"]",
                            "\"cryptographic_binding_methods_supported\":[\"jwk\",\"did:jwk\"]",
                        )
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val proof = extractProofJwt(request)
                    val header = jwtPart(proof, 0)
                    assertEquals(holderDidKeyId, header["kid"]?.jsonPrimitive?.content)
                    assertNull(header["jwk"], "Expected DID binding, not JWK")
                    jsonResponse("""{"transaction_id":"tx-1"}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = key, didStore = didStore),
            httpClient = client,
        )

        val session = service.start(preAuthorizedRequest().copy(didReference = holderDid))
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun `inline did takes precedence over didReference`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val inlineDid = "did:jwk:inline-holder"
        val inlineDidKeyId = "$inlineDid#0"
        val referencedDid = "did:jwk:referenced-holder"

        val didStore = InMemoryDidStore().also { store ->
            // Both DIDs are in the store. The inline DID matches the wallet key; the referenced
            // DID uses a different key so its binding would fail the key-match check.
            store.addDid(WalletDidEntry(did = inlineDid, document = didDocument(inlineDid, inlineDidKeyId, key)))
            val otherKey = JWKKey.generate(KeyType.secp256r1)
            store.addDid(
                WalletDidEntry(
                    did = referencedDid,
                    document = didDocument(referencedDid, "$referencedDid#0", otherKey),
                )
            )
        }
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true)
                        .replace(
                            "\"cryptographic_binding_methods_supported\":[\"jwk\"]",
                            "\"cryptographic_binding_methods_supported\":[\"jwk\",\"did:jwk\"]",
                        )
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val proof = extractProofJwt(request)
                    val header = jwtPart(proof, 0)
                    assertEquals(inlineDidKeyId, header["kid"]?.jsonPrimitive?.content)
                    assertNull(header["jwk"], "Expected inline DID binding, not JWK")
                    jsonResponse("""{"transaction_id":"tx-1"}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = key, didStore = didStore),
            httpClient = client,
        )

        val session = service.start(
            preAuthorizedRequest().copy(
                did = inlineDid,
                didReference = referencedDid,
            )
        )
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    @Test
    fun `didReference not found falls back to default DID resolution`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val emptyDidStore = InMemoryDidStore()
        val client = client { request ->
            when (request.url.toString()) {
                ISSUER_METADATA -> jsonResponse(
                    issuerMetadata(proofRequired = true)
                        .replace(
                            "\"cryptographic_binding_methods_supported\":[\"jwk\"]",
                            "\"cryptographic_binding_methods_supported\":[\"jwk\",\"did:jwk\"]",
                        )
                )
                AS_METADATA -> jsonResponse(authorizationServerMetadata())
                TOKEN_ENDPOINT -> jsonResponse("""{"access_token":"access","token_type":"Bearer"}""")
                NONCE_ENDPOINT -> jsonResponse("""{"c_nonce":"nonce"}""")
                CREDENTIAL_ENDPOINT -> {
                    val proof = extractProofJwt(request)
                    val header = jwtPart(proof, 0)
                    // No staticDid and didReference not found → defaultDid() returns null → JWK binding
                    assertNotNull(header["jwk"], "Expected JWK binding when didReference is absent from store")
                    assertNull(header["kid"]?.jsonPrimitive?.content, "Unexpected DID kid")
                    jsonResponse("""{"transaction_id":"tx-1"}""", HttpStatusCode.Accepted)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet("test", staticKey = key, didStore = emptyDidStore),
            httpClient = client,
        )

        val session = service.start(
            preAuthorizedRequest().copy(didReference = "did:jwk:not-in-store")
        )
        assertIs<WalletIssuanceOutcome.Deferred>(service.continuePreAuthorized(session.id))
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(json) }
    }

    private fun MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun preAuthorizedRequest() = WalletIssuanceSessionRequest(
        offerJson = buildJsonObject {
            put("credential_issuer", ISSUER)
            put("credential_configuration_ids", buildJsonArray {
                add(Json.parseToJsonElement(Json.encodeToString("test-credential")))
            })
            put(
                "grants",
                Json.parseToJsonElement(
                    """{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}"""
                ),
            )
        },
        clientId = "wallet-client",
        redirectUri = Url("wallet.example:/callback"),
    )

    private fun issuerMetadata(proofRequired: Boolean): String {
        val proofConfig = if (proofRequired) {
            ""","cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}"""
        } else {
            ""
        }
        return """
        {
          "credential_issuer":"$ISSUER",
          "credential_endpoint":"$CREDENTIAL_ENDPOINT",
          "nonce_endpoint":"$NONCE_ENDPOINT",
          "deferred_credential_endpoint":"$DEFERRED_ENDPOINT",
          "credential_configurations_supported":{
            "test-credential":{
              "format":"jwt_vc_json"
              $proofConfig
            }
          }
        }
        """.trimIndent()
    }

    private fun authorizationServerMetadata(): String = """
        {
          "issuer":"$ISSUER",
          "token_endpoint":"$TOKEN_ENDPOINT",
          "response_types_supported":["code"],
          "grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
        }
    """.trimIndent()

    private fun extractProofJwt(request: HttpRequestData): String {
        val body = Json.parseToJsonElement(
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        ).jsonObject
        return body["proofs"]!!.jsonObject["jwt"]!!.jsonArray.single().jsonPrimitive.content
    }

    private fun jwtPart(jwt: String, index: Int): JsonObject =
        Json.parseToJsonElement(
            jwt.split('.')[index].decodeFromBase64Url().decodeToString()
        ).jsonObject

    private suspend fun didDocument(did: String, keyId: String, key: JWKKey): JsonObject =
        buildJsonObject {
            put("id", did)
            put("verificationMethod", buildJsonArray {
                add(buildJsonObject {
                    put("id", keyId)
                    put("controller", did)
                    put("type", "JsonWebKey2020")
                    put("publicKeyJwk", Json.parseToJsonElement(key.getPublicKey().exportJWK()))
                })
            })
        }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val ISSUER_METADATA = "$ISSUER/.well-known/openid-credential-issuer"
        const val AS_METADATA = "$ISSUER/.well-known/oauth-authorization-server"
        const val TOKEN_ENDPOINT = "$ISSUER/token"
        const val CREDENTIAL_ENDPOINT = "$ISSUER/credential"
        const val NONCE_ENDPOINT = "$ISSUER/nonce"
        const val DEFERRED_ENDPOINT = "$ISSUER/deferred"
    }
}
