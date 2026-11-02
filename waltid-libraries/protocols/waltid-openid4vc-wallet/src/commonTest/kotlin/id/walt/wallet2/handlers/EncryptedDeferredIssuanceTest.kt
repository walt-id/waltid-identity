package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import id.walt.openid4vci.responses.credential.encryption.Crypto2JweCredentialResponseEncryptor
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vci.wallet.credential.isApplicationJwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptedDeferredIssuanceTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `isolated poll encrypts each deferred request and decrypts its own response`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer-deferred-encryption"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val holderKey = batchTestLegacyKey()
        val credential = batchTestCredential(holderKey)
        val keys = InMemoryKeyStore()
        val store = InMemoryCredentialStore()
        val wallet = Wallet(id = "wallet", keyStores = listOf(keys), credentialStores = listOf(store))
        val request = PollDeferredRequest(
            deferredCredentialEndpoint = Url("$ISSUER/deferred"),
            transactionId = "transaction",
            accessToken = "token",
            credentialIssuerBaseUrl = ISSUER,
            holderBindings = listOf(CredentialHolderBinding(keyId = keys.addKey(holderKey))),
        )
        var polls = 0
        val responseKids = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { httpRequest ->
                    val path = httpRequest.url.encodedPath
                    if (path.endsWith("/openid-credential-issuer")) {
                        return@addHandler respond(
                            issuerMetadataJson(issuerKey.publicEncryptionJwk()),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    check(path.endsWith("/deferred")) { "Unexpected issuer request: ${httpRequest.url}" }
                    val body = httpRequest.body as OutgoingContent.ByteArrayContent
                    assertTrue(body.contentType.isApplicationJwt())
                    val accept = httpRequest.headers[HttpHeaders.Accept].orEmpty().lowercase()
                    assertTrue("application/jwt" in accept && "application/json" in accept)
                    val decrypted = Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(body.bytes().decodeToString())
                    assertEquals("transaction", decrypted.getValue("transaction_id").jsonPrimitive.content)
                    val encryption = CredentialResponseEncryptionParameters.fromJsonObject(
                        decrypted.getValue("credential_response_encryption").jsonObject,
                    )
                    responseKids += encryption.jwk.getValue("kid").jsonPrimitive.content
                    polls++
                    val payload = if (polls == 1) {
                        buildJsonObject {
                            put("transaction_id", "transaction")
                            put("interval", 1)
                        }
                    } else {
                        buildJsonObject {
                            put("credentials", buildJsonArray {
                                add(buildJsonObject { put("credential", credential) })
                            })
                        }
                    }
                    respond(
                        content = Crypto2JweCredentialResponseEncryptor.encrypt(payload, encryption),
                        status = if (polls == 1) HttpStatusCode.Accepted else HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, if (polls == 1) "application/jwt" else "Application/JWT"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        try {
            assertTrue(WalletIssuanceHandler.pollDeferredFlow(wallet, request, httpClient = client).toList().isEmpty())
            val stored = WalletIssuanceHandler.pollDeferredFlow(wallet, request, httpClient = client).toList().single()
            assertEquals(2, polls)
            assertEquals(2, responseKids.distinct().size)
            assertEquals(CredentialParser.detectAndParse(credential).second, stored.credential)
        } finally {
            client.close()
        }
    }

    private suspend fun issuerMetadataJson(issuerJwk: JsonObject) = buildJsonObject {
        put("credential_issuer", ISSUER)
        put("credential_endpoint", "$ISSUER/credential")
        put("deferred_credential_endpoint", "$ISSUER/deferred")
        put("credential_request_encryption", buildJsonObject {
            put("jwks", buildJsonObject { put("keys", buildJsonArray { add(issuerJwk) }) })
            put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
            put("encryption_required", JsonPrimitive(false))
        })
        put("credential_response_encryption", buildJsonObject {
            put("alg_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES)) })
            put("enc_values_supported", buildJsonArray { add(JsonPrimitive(CredentialEncryptionProfile.ENC_A128GCM)) })
            put("encryption_required", JsonPrimitive(false))
        })
        put("credential_configurations_supported", buildJsonObject {
            put("identity", buildJsonObject {
                put("format", "dc+sd-jwt")
                put("vct", "identity")
            })
        })
    }.toString()

    private suspend fun Key.publicEncryptionJwk(): JsonObject = buildJsonObject {
        Jwk.parse(exportPublicJwk()).forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(id.value))
        put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
        put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
    }
}
