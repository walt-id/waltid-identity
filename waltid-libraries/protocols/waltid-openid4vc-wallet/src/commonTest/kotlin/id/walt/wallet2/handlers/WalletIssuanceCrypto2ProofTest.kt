package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.handlers.SignProofTestSupport.CONFIG_ID
import id.walt.wallet2.handlers.SignProofTestSupport.ISSUER
import id.walt.wallet2.handlers.SignProofTestSupport.issuerMetadataClient
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletIssuanceCrypto2ProofTest {
    @Test
    fun `wallet proof uses crypto2 key from the selected store entry`() = runTest {
        val key = FakeSigningKey(KeyId("crypto2-key"))
        val store = Crypto2BackedStore("enterprise.resource.key", key)
        val wallet = Wallet(id = "wallet", keyStores = listOf(store))

        assertEquals(key.id, wallet.findCrypto2Key("enterprise.resource.key", setOf(KeyUsage.SIGN))?.id)
        assertEquals(key.id, wallet.resolveCrypto2Key("enterprise.resource.key", setOf(KeyUsage.SIGN))?.id)
        assertEquals(key.id, wallet.defaultCrypto2Key(setOf(KeyUsage.SIGN))?.id)

        val proof = WalletIssuanceHandler.signProof(
            wallet = wallet,
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
                keyId = "enterprise.resource.key",
            ),
            httpClient = issuerMetadataClient(),
        ).proofJwt

        assertEquals(
            "nonce",
            Json.parseToJsonElement(CompactJws.decodeUnverified(proof).payload.decodeToString())
                .jsonObject["nonce"]?.jsonPrimitive?.content,
        )
        assertEquals(1, key.signCalls)
        assertEquals(setOf(KeyUsage.SIGN), store.requestedUsages)
    }

    @Test
    fun `wallet proof signs with crypto2-only in-memory key`() = runTest {
        val key = FakeSigningKey(KeyId("crypto2-only-proof"))
        val store = InMemoryKeyStore().also { it.addCrypto2Key(key) }

        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "wallet", keyStores = listOf(store)),
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
            ),
            httpClient = issuerMetadataClient(),
        ).proofJwt

        assertEquals(
            "nonce",
            Json.parseToJsonElement(CompactJws.decodeUnverified(proof).payload.decodeToString())
                .jsonObject["nonce"]?.jsonPrimitive?.content,
        )
        assertEquals(1, key.signCalls)
    }

    @Test
    fun `sign-proof rejects unknown credential configuration ids`() = runTest {
        val key = FakeSigningKey(KeyId("crypto2-key"))
        val store = Crypto2BackedStore("enterprise.resource.key", key)

        assertFailsWith<IllegalStateException> {
            WalletIssuanceHandler.signProof(
                wallet = Wallet(id = "wallet", keyStores = listOf(store)),
                request = SignProofRequest(
                    issuerUrl = Url(ISSUER),
                    credentialConfigurationId = "missing-config",
                    nonce = "nonce",
                    keyId = "enterprise.resource.key",
                ),
                httpClient = issuerMetadataClient(),
            )
        }
    }

    @Test
    fun `non-JWT proof metadata is rejected instead of sending JWT`() {
        assertFailsWith<IllegalArgumentException> {
            supportedJwtProofAlgorithms(mapOf("attestation" to ProofType(setOf("ES256"))))
        }
    }

    @Test
    fun `auth-code flow resolves referenced key through crypto2 resolveKeyMaterial`() = runTest {
        val key = FakeSigningKey(KeyId("crypto2-only-auth-code"))
        val store = Crypto2BackedStore("enterprise.resource.key", key)
        val wallet = Wallet(id = "wallet", keyStores = listOf(store))

        // Fail at the first HTTP call so the test only exercises the key-resolution branch.
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { respondError(HttpStatusCode.ServiceUnavailable) }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        assertFailsWith<Throwable> {
            WalletIssuanceHandler.receiveCredentialAuthCodeFlow(
                wallet = wallet,
                code = "auth-code",
                codeVerifier = null,
                credentialIssuerBaseUrl = ISSUER,
                credentialEndpoint = Url("$ISSUER/credential"),
                credentialConfigurationId = CONFIG_ID,
                keyReference = "enterprise.resource.key",
                httpClient = httpClient,
            ).toList()
        }

        // Referenced key must be resolved via crypto2 with SIGN usages, not converted to a
        // legacy DirectSerializedKey.
        assertEquals(setOf(KeyUsage.SIGN), store.requestedUsages)
        assertEquals("enterprise.resource.key", store.lastRequestedKeyId)
    }

    private class FakeSigningKey(
        override val id: KeyId,
    ) : Crypto2Key {
        override val spec = KeySpec.Ec(EcCurve.P256)
        override val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        var signCalls = 0
            private set
        override val capabilities = KeyCapabilities(
            signer = Signer { _, _ ->
                signCalls++
                ByteArray(64)
            },
            publicKeyExporter = PublicKeyExporter {
                EncodedKey.Jwk(
                    data = BinaryData(
                        """{"kty":"EC","crv":"P-256","x":"AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE","y":"AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"}"""
                            .encodeToByteArray(),
                    ),
                    privateMaterial = false,
                )
            },
            supportsSignatureAlgorithm = { it == JwsAlgorithm.ES256.toSignatureAlgorithm() },
        )
    }

    private class Crypto2BackedStore(
        private val storeKeyId: String,
        private val crypto2Key: Crypto2Key,
    ) : WalletKeyStore {
        var requestedUsages: Set<KeyUsage>? = null
        var lastRequestedKeyId: String? = null

        override suspend fun getKey(keyId: String): LegacyKey? = null

        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? {
            requestedUsages = usages
            lastRequestedKeyId = keyId
            return crypto2Key.takeIf { keyId == storeKeyId }
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> = flowOf(WalletKeyInfo(storeKeyId, "P-256"))

        override suspend fun addKey(key: LegacyKey): String = error("Test store is read-only")

        override suspend fun removeKey(keyId: String): Boolean = false
    }
}
