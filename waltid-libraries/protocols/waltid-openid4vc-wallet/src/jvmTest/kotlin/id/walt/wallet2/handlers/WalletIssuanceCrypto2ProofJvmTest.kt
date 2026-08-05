package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** JVM-only coverage for private legacy JWK import and signing. */
class WalletIssuanceCrypto2ProofJvmTest {
    @Test
    fun `local JWK wallet proof signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "wallet", staticKey = legacyKey),
            request = SignProofRequest(issuerUrl = Url("https://issuer.example"), nonce = "nonce"),
        ).proofJwt
        val decoded = CompactJws.decodeUnverified(proof)
        val bindingJwk = assertNotNull(decoded.protectedHeader["jwk"]).jsonObject
        val verificationKey = runtime.restore(
            V1KeyMigration().migrate(
                recordId = KeyId("proof-verification"),
                serialized = buildJsonObject {
                    put("type", "jwk")
                    put("jwk", bindingJwk)
                },
                usages = setOf(KeyUsage.VERIFY),
            )
        )
        val verified = CompactJws.verify(proof, verificationKey, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject

        assertEquals("openid4vci-proof+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertFalse("d" in bindingJwk)
        assertEquals("https://issuer.example", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("nonce", payload["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun `wallet rejects mismatched legacy and crypto2 store material`() = runTest {
        val store = Crypto2BackedStore(
            storeKeyId = "resource-key",
            legacyKey = JWKKey.generate(KeyType.secp256r1).getPublicKey(),
            crypto2Key = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("different-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.signProof(
                wallet = Wallet(id = "wallet", keyStores = listOf(store)),
                request = SignProofRequest(
                    issuerUrl = Url("https://issuer.example"),
                    nonce = "nonce",
                    keyId = "resource-key",
                ),
            )
        }
    }

    @Test
    fun `public local JWK cannot sign wallet proof`() = runTest {
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()

        assertFailsWith<IllegalArgumentException> {
            WalletIssuanceHandler.signProof(
                wallet = Wallet(id = "wallet", staticKey = publicKey),
                request = SignProofRequest(issuerUrl = Url("https://issuer.example"), nonce = "nonce"),
            )
        }
    }

    @Test
    fun `DID-bound wallet proof uses exact authentication method kid`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "wallet", staticKey = key),
            request = SignProofRequest(issuerUrl = Url("https://issuer.example"), nonce = "nonce", did = did),
        ).proofJwt

        assertEquals(
            "$did#${did.removePrefix("did:key:")}",
            CompactJws.decodeUnverified(proof).protectedHeader["kid"]?.jsonPrimitive?.content,
        )
    }

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private class Crypto2BackedStore(
        private val storeKeyId: String,
        private val legacyKey: Key,
        private val crypto2Key: Crypto2Key,
    ) : WalletKeyStore {
        var requestedUsages: Set<KeyUsage>? = null

        override suspend fun getKey(keyId: String): Key? = legacyKey.takeIf { keyId == storeKeyId }

        override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): Crypto2Key? {
            requestedUsages = usages
            return crypto2Key.takeIf { keyId == storeKeyId }
        }

        override suspend fun listKeys(): Flow<WalletKeyInfo> = flowOf(WalletKeyInfo(storeKeyId, legacyKey.keyType.name))

        override suspend fun addKey(key: Key): String = error("Test store is read-only")

        override suspend fun removeKey(keyId: String): Boolean = false
    }
}
