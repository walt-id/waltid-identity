package id.walt.did.dids.resolver

import id.walt.crypto.keys.Key as V1Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Crypto2DidKeyResolverTest {
    @Test
    fun `crypto2 DID service resolves publicKeyJwk verification keys`() = runTest {
        val provider = CryptographySoftwareKeyProvider()
        val runtime = CryptoRuntime(listOf(provider))
        val privateKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("private"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwk = privateKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val document = buildJsonObject {
            put("id", "did:example:123")
            put("verificationMethod", buildJsonArray {
                add(buildJsonObject {
                    put("id", "did:example:123#key-1")
                    put("type", "JsonWebKey2020")
                    put("controller", "did:example:123")
                    put("publicKeyJwk", Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()))
                })
            })
        }
        val previousResolver = DidService.resolverMethods["example"]
        DidService.registerResolverForMethod("example", FakeResolver(document))
        val key = try {
            Crypto2DidService.resolveToKeys("did:example:123").getOrThrow().single()
        } finally {
            previousResolver?.let { DidService.registerResolverForMethod("example", it) }
                ?: DidService.resolverMethods.remove("example")
        }
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val message = "did".encodeToByteArray()
        val signature = privateKey.capabilities.signer!!.sign(message, algorithm)

        assertEquals(KeyId("did:example:123#key-1"), key.id)
        assertTrue(key.capabilities.verifier!!.verify(message, signature, algorithm))
    }

    @Test
    fun `documents without public JWK methods fail`() = runTest {
        val document = buildJsonObject {
            put("id", "did:key:z6Mk")
            put("verificationMethod", buildJsonArray {
                add(buildJsonObject {
                    put("id", "did:key:z6Mk#z6Mk")
                    put("publicKeyMultibase", "z6Mk")
                })
            })
        }
        assertFailsWith<IllegalArgumentException> {
            DidDocumentCrypto2KeyResolver(FakeResolver(document)).resolveToKeys("did:key:z6Mk")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class FakeResolver(private val document: JsonObject) : DidResolver {
        override val name: String = "fake"
        override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(setOf("example"))
        override suspend fun resolve(did: String): Result<JsonObject> = Result.success(document)
        override suspend fun resolveToKey(did: String): Result<V1Key> = Result.failure(NotImplementedError())
        override suspend fun resolveToKeys(did: String): Result<Set<V1Key>> = Result.failure(NotImplementedError())
    }
}
