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
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialRequestEncryption
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import id.walt.openid4vci.responses.credential.encryption.Crypto2JweCredentialResponseEncryptor
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vci.wallet.credential.CredentialEncryption
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
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptedDeferredIssuanceTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `isolated poll decrypts an encrypted deferred response and sends a json request`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer-deferred-encryption"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val prepared = CredentialEncryption.prepare(
            buildJsonObject { put("credential_configuration_id", "identity") },
            issuerMetadata(issuerKey),
        )
        val responseKey = CredentialEncryption.exportResponseDecryptionKey(requireNotNull(prepared.responseDecryptionKey))
        val encryption = CredentialResponseEncryptionParameters.fromJsonObject(
            Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(prepared.body)
                .getValue("credential_response_encryption").jsonObject,
        )
        val holderKey = batchTestLegacyKey()
        val credential = batchTestCredential(holderKey)
        val keys = InMemoryKeyStore()
        val store = InMemoryCredentialStore()
        val wallet = Wallet(id = "wallet", keyStores = listOf(keys), credentialStores = listOf(store))
        val request = PollDeferredRequest(
            deferredCredentialEndpoint = Url("https://issuer.example/deferred"),
            transactionId = "transaction",
            accessToken = "token",
            holderBindings = listOf(CredentialHolderBinding(keyId = keys.addKey(holderKey))),
            responseDecryptionKey = responseKey,
        )
        var polls = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { httpRequest ->
                    val body = httpRequest.body as OutgoingContent.ByteArrayContent
                    assertEquals(ContentType.Application.Json, body.contentType?.withoutParameters())
                    assertEquals("""{"transaction_id":"transaction"}""", body.bytes().decodeToString())
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
                        headers = headersOf(HttpHeaders.ContentType, "application/jwt"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        try {
            assertTrue(WalletIssuanceHandler.pollDeferredFlow(wallet, request, httpClient = client).toList().isEmpty())
            val stored = WalletIssuanceHandler.pollDeferredFlow(wallet, request, httpClient = client).toList().single()
            assertEquals(2, polls)
            assertEquals(CredentialParser.detectAndParse(credential).second, stored.credential)
        } finally {
            client.close()
        }
    }

    private suspend fun issuerMetadata(issuerKey: Key) = CredentialIssuerMetadata.fromBaseUrl(
        baseUrl = "https://issuer.example",
        credentialConfigurationsSupported = mapOf(
            "identity" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"),
        ),
        credentialRequestEncryption = CredentialRequestEncryption(
            jwks = buildJsonObject { put("keys", buildJsonArray { add(issuerKey.publicEncryptionJwk()) }) },
            encValuesSupported = setOf(CredentialEncryptionProfile.ENC_A128GCM),
            encryptionRequired = false,
        ),
    )

    private suspend fun Key.publicEncryptionJwk(): JsonObject = buildJsonObject {
        Jwk.parse(exportPublicJwk()).forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(id.value))
        put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
        put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
    }
}
