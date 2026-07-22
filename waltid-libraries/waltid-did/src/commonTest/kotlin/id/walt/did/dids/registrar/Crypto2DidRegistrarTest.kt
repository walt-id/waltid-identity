package id.walt.did.dids.registrar

import id.walt.crypto.keys.Key as V1Key
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.PrivateKeyExporter
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.resolver.DidDocumentCrypto2KeyResolver
import id.walt.did.dids.resolver.DidResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Crypto2DidRegistrarTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `built-in did key registry supports Ed25519 P-256 and RSA`() = runTest {
        val cases = listOf(
            KeySpec.Edwards(EdwardsCurve.ED25519) to "did:key:z6Mk",
            KeySpec.Ec(EcCurve.P256) to "did:key:zDna",
            KeySpec.Rsa(2048) to "did:key:z4MX",
        )

        cases.forEachIndexed { index, (spec, prefix) ->
            val key = generate("key-$index", spec)
            val result = DidService.registerByKey("key", key, DidKeyCreateOptions())

            assertTrue(result.did.startsWith(prefix), "Unexpected DID for $spec: ${result.did}")
            assertResolvesToSamePublicKey(result, key)
        }
    }

    @Test
    fun `did jwk uses public material only and resolves back to the same key`() = runTest {
        val key = generate("private-ed25519", KeySpec.Edwards(EdwardsCurve.ED25519))

        val result = Crypto2DidService.registerByKey("jwk", key, DidJwkCreateOptions())
        val documentJwk = result.didDocument.publicJwk()
        val identifierJwk = Json.parseToJsonElement(
            result.did.removePrefix("did:jwk:").decodeFromBase64Url().decodeToString()
        ).jsonObject

        assertTrue(privateJwkMembers.none(documentJwk::containsKey))
        assertTrue(privateJwkMembers.none(identifierJwk::containsKey))
        assertResolvesToSamePublicKey(result, key)
    }

    @Test
    fun `did key JWK JCS preserves option semantics`() = runTest {
        val key = generate("jwk-jcs", KeySpec.Ec(EcCurve.P256))

        val result = Crypto2DidService.registerByKey(
            "key",
            key,
            DidKeyCreateOptions(useJwkJcsPub = true),
        )

        assertEquals(
            MultiCodecUtils.JwkJcsPubMultiCodecKeyCode,
            MultiCodecUtils.getMultiCodecKeyCode(result.did.removePrefix("did:key:")),
        )
        assertResolvesToSamePublicKey(result, key)
    }

    @Test
    fun `managed key registers through public key exporter`() = runTest {
        val generated = generate("managed-source", KeySpec.Ec(EcCurve.P256))
        val publicJwk = generated.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val managed = PublicExportManagedKey(
            StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("managed-key"),
                spec = generated.spec,
                usages = setOf(KeyUsage.VERIFY),
                provider = ProviderId("test-managed"),
                providerSchemaVersion = 1,
                providerData = BinaryData("reference".encodeToByteArray()),
                publicKey = publicJwk,
            )
        )

        val result = Crypto2DidService.registerByKey("key", managed, DidKeyCreateOptions())

        assertTrue(result.did.startsWith("did:key:zDna"))
        assertFalse(managed.privateExportAttempted)
        assertResolvesToSamePublicKey(result, managed)
    }

    @Test
    fun `unsupported raw spec requires JWK JCS`() = runTest {
        val generated = generate("custom-source", KeySpec.Ec(EcCurve.P256))
        val publicJwk = generated.capabilities.publicKeyExporter!!.exportPublicKey()
        val custom = PublicExportKey(KeyId("custom"), KeySpec.Custom("portable-test"), publicJwk)

        val error = assertFailsWith<IllegalArgumentException> {
            Crypto2DidService.registerByKey("key", custom, DidKeyCreateOptions())
        }
        assertTrue(error.message.orEmpty().contains("useJwkJcsPub=true"))

        val result = Crypto2DidService.registerByKey(
            "key",
            custom,
            DidKeyCreateOptions(useJwkJcsPub = true),
        )
        assertEquals(
            MultiCodecUtils.JwkJcsPubMultiCodecKeyCode,
            MultiCodecUtils.getMultiCodecKeyCode(result.did.removePrefix("did:key:")),
        )
    }

    private suspend fun generate(id: String, spec: KeySpec): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun assertResolvesToSamePublicKey(result: DidResult, original: Key) {
        val resolved = DidDocumentCrypto2KeyResolver(FakeResolver(result.didDocument.toJsonObject()))
            .resolveToKeys(result.did)
            .single()
        assertEquals(publicKeyMembers(original.exportPublicJwkObject()), publicKeyMembers(resolved.exportPublicJwkObject()))
    }

    private fun publicKeyMembers(jwk: JsonObject): JsonObject = when (jwk.getValue("kty").jsonPrimitive.content) {
        "OKP" -> JsonObject(jwk.filterKeys { it in setOf("crv", "kty", "x") })
        "EC" -> JsonObject(jwk.filterKeys { it in setOf("crv", "kty", "x", "y") })
        "RSA" -> JsonObject(jwk.filterKeys { it in setOf("e", "kty", "n") })
        else -> error("Unsupported test JWK")
    }

    private fun DidDocument.publicJwk(): JsonObject =
        getValue("verificationMethod").jsonArray.single().jsonObject.getValue("publicKeyJwk").jsonObject

    private class PublicExportManagedKey(
        override val storedKey: StoredKey.Managed,
    ) : ManagedKey, PublicKeyExporter, PrivateKeyExporter {
        var privateExportAttempted: Boolean = false
        override suspend fun exportPublicKey(): EncodedKey = requireNotNull(storedKey.publicKey)
        override suspend fun exportPrivateKey(): EncodedKey {
            privateExportAttempted = true
            error("Registration must not export managed private material")
        }
    }

    private class PublicExportKey(
        override val id: KeyId,
        override val spec: KeySpec,
        private val publicKey: EncodedKey,
    ) : Key, PublicKeyExporter {
        override val usages: Set<KeyUsage> = setOf(KeyUsage.VERIFY)
        override suspend fun exportPublicKey(): EncodedKey = publicKey
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class FakeResolver(private val document: JsonObject) : DidResolver {
        override val name: String = "crypto2-registration-test"
        override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(setOf("key", "jwk"))
        override suspend fun resolve(did: String): Result<JsonObject> = Result.success(document)
        override suspend fun resolveToKey(did: String): Result<V1Key> = Result.failure(NotImplementedError())
        override suspend fun resolveToKeys(did: String): Result<Set<V1Key>> = Result.failure(NotImplementedError())
    }

    private companion object {
        val privateJwkMembers = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")
    }
}
