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
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
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

    @Test
    fun `encryption JWK does not abort signing key resolution`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val signingJwk = generatePublicJwk(runtime, "signing-private")
        val encryptionJwk = JsonObject(
            generatePublicJwk(runtime, "encryption-private")
                .filterKeys { it !in setOf("use", "key_ops", "alg") } +
                    mapOf(
                        "use" to JsonPrimitive("enc"),
                        "alg" to JsonPrimitive("ECDH-ES+A256KW"),
                    )
        )
        val document = didDocument(signingJwk, encryptionJwk)
        val resolver = DidDocumentCrypto2KeyResolver(FakeResolver(document), runtime)

        val legacyKeys = resolver.resolveToKeys(DID)
        val authenticationKeys = resolver.resolveToKeys(
            DID,
            keyId = null,
            relationship = VerificationRelationshipType.Authentication,
        )

        assertEquals(setOf(KeyId(SIGNING_METHOD_ID)), legacyKeys.map { it.id }.toSet())
        assertEquals(setOf(KeyId(SIGNING_METHOD_ID)), authenticationKeys.map { it.id }.toSet())
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveToKeys(
                DID,
                keyId = ENCRYPTION_METHOD_ID,
                relationship = VerificationRelationshipType.KeyAgreement,
            )
        }
    }

    @Test
    fun `kid narrows methods before unrelated malformed method is imported`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val document = buildJsonObject {
            put("id", DID)
            put("verificationMethod", buildJsonArray {
                add(verificationMethod(SIGNING_METHOD_ID, generatePublicJwk(runtime, "signing-private")))
                add(buildJsonObject {
                    put("type", "JsonWebKey2020")
                    put("controller", DID)
                    put("publicKeyJwk", buildJsonObject { put("kty", "unsupported") })
                })
            })
        }

        val keys = DidDocumentCrypto2KeyResolver(FakeResolver(document), runtime).resolveToKeys(
            DID,
            keyId = SIGNING_METHOD_ID,
            relationship = null,
        )

        assertEquals(setOf(KeyId(SIGNING_METHOD_ID)), keys.map { it.id }.toSet())
    }

    @Test
    fun `authentication resolution rejects key agreement method`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val document = didDocument(
            signingJwk = generatePublicJwk(runtime, "signing-private"),
            encryptionJwk = generatePublicJwk(runtime, "agreement-private"),
        )

        assertFailsWith<IllegalArgumentException> {
            DidDocumentCrypto2KeyResolver(FakeResolver(document), runtime).resolveToKeys(
                DID,
                keyId = ENCRYPTION_METHOD_ID,
                relationship = VerificationRelationshipType.Authentication,
            )
        }
    }

    @Test
    fun `embedded authentication method is resolved`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val document = buildJsonObject {
            put("id", DID)
            put("authentication", buildJsonArray {
                add(verificationMethod(SIGNING_METHOD_ID, generatePublicJwk(runtime, "signing-private")))
            })
        }

        val keys = DidDocumentCrypto2KeyResolver(FakeResolver(document), runtime).resolveToKeys(
            DID,
            keyId = SIGNING_METHOD_ID,
            relationship = VerificationRelationshipType.Authentication,
        )

        assertEquals(setOf(KeyId(SIGNING_METHOD_ID)), keys.map { it.id }.toSet())
    }

    private suspend fun generatePublicJwk(runtime: CryptoRuntime, id: String): JsonObject {
        val privateKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwk = privateKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        return Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()).jsonObject
    }

    private fun didDocument(signingJwk: JsonObject, encryptionJwk: JsonObject): JsonObject = buildJsonObject {
        put("id", DID)
        put("verificationMethod", buildJsonArray {
            add(verificationMethod(SIGNING_METHOD_ID, signingJwk))
            add(verificationMethod(ENCRYPTION_METHOD_ID, encryptionJwk))
        })
        put("authentication", buildJsonArray { add(JsonPrimitive(SIGNING_METHOD_ID)) })
        put("keyAgreement", buildJsonArray { add(JsonPrimitive(ENCRYPTION_METHOD_ID)) })
    }

    private fun verificationMethod(id: String, jwk: JsonObject): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "JsonWebKey2020")
        put("controller", DID)
        put("publicKeyJwk", jwk)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class FakeResolver(private val document: JsonObject) : DidResolver {
        override val name: String = "fake"
        override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(setOf("example"))
        override suspend fun resolve(did: String): Result<JsonObject> = Result.success(document)
        override suspend fun resolveToKey(did: String): Result<V1Key> = Result.failure(NotImplementedError())
        override suspend fun resolveToKeys(did: String): Result<Set<V1Key>> = Result.failure(NotImplementedError())
    }

    private companion object {
        const val DID = "did:example:123"
        const val SIGNING_METHOD_ID = "$DID#sig"
        const val ENCRYPTION_METHOD_ID = "$DID#enc"
    }
}
