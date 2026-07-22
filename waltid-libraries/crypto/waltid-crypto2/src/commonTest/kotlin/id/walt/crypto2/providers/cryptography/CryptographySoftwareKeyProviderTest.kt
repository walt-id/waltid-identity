package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptographySoftwareKeyProviderTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `P-256 key selects digest and encoding per signature operation`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val message = "message".encodeToByteArray()
        val p1363 = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val der = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384, EcdsaSignatureEncoding.DER)

        listOf(p1363, der).forEach { algorithm ->
            val signature = key.capabilities.signer!!.sign(message, algorithm)
            assertTrue(key.capabilities.verifier!!.verify(message, signature, algorithm))
            assertFalse(key.capabilities.verifier!!.verify("tampered".encodeToByteArray(), signature, algorithm))
        }
        assertTrue(key.capabilities.signer!!.sign(message, der).first() == 0x30.toByte())
    }

    @Test
    fun `Ed25519 key uses curve-neutral EdDSA operation`() = runTest {
        val key = generate(KeySpec.Edwards(EdwardsCurve.ED25519), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val message = byteArrayOf(1, 2, 3)
        val signature = key.capabilities.signer!!.sign(message, SignatureAlgorithm.EdDsa)

        assertTrue(key.capabilities.verifier!!.verify(message, signature, SignatureAlgorithm.EdDsa))
    }

    @Test
    fun `one RSA key supports PKCS1 PSS and OAEP operations`() = runTest {
        val key = generate(
            KeySpec.Rsa(2048),
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT, KeyUsage.DECRYPT),
        )
        val message = "rsa-message".encodeToByteArray()
        val signatures = listOf(
            SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256),
            SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_384, saltLengthBytes = 48),
        )
        signatures.forEach { algorithm ->
            val signature = key.capabilities.signer!!.sign(message, algorithm)
            assertTrue(key.capabilities.verifier!!.verify(message, signature, algorithm))
        }

        val encryption = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)
        val ciphertext = key.capabilities.encryptor!!.encrypt(message, encryption, null)
        assertContentEquals(message, key.capabilities.decryptor!!.decrypt(ciphertext, null))
    }

    @Test
    fun `one P-256 key signs and performs ECDH`() = runTest {
        val first = generate(
            KeySpec.Ec(EcCurve.P256),
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.KEY_AGREEMENT),
        )
        val second = generate(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.KEY_AGREEMENT))
        val firstPublic = assertIs<EncodedKey.Jwk>(first.capabilities.publicKeyExporter!!.exportPublicKey())
        val secondPublic = assertIs<EncodedKey.Jwk>(second.capabilities.publicKeyExporter!!.exportPublicKey())

        val firstSecret = first.capabilities.keyAgreement!!
            .generateSharedSecret(secondPublic, KeyAgreementAlgorithm.Ecdh)
        val secondSecret = second.capabilities.keyAgreement!!
            .generateSharedSecret(firstPublic, KeyAgreementAlgorithm.Ecdh)

        assertContentEquals(firstSecret.toByteArray(), secondSecret.toByteArray())
    }

    @Test
    fun `X25519 key agreement survives serialization restart`() = runTest {
        val spec = KeySpec.Montgomery(MontgomeryCurve.X25519)
        val usages = setOf(KeyUsage.KEY_AGREEMENT)
        if (!supportsPrivateJwkImport(spec, usages)) return@runTest
        val first = restart(generate(spec, usages))
        val second = restart(generate(spec, usages))

        val firstSecret = first.capabilities.keyAgreement!!.generateSharedSecret(
            second.capabilities.publicKeyExporter!!.exportPublicKey(),
            KeyAgreementAlgorithm.Xdh,
        )
        val secondSecret = second.capabilities.keyAgreement!!.generateSharedSecret(
            first.capabilities.publicKeyExporter!!.exportPublicKey(),
            KeyAgreementAlgorithm.Xdh,
        )

        assertContentEquals(firstSecret.toByteArray(), secondSecret.toByteArray())
    }

    @Test
    fun `public-only restoration exposes no private operation or material`() = runTest {
        val privateKey = generate(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val publicMaterial = privateKey.capabilities.publicKeyExporter!!.exportPublicKey()
        val publicStored = privateKey.storedKey.copy(
            usages = setOf(KeyUsage.VERIFY),
            material = publicMaterial,
        )
        val publicKey = runtime.restore(publicStored)

        assertNotNull(publicKey.capabilities.verifier)
        assertTrue(publicKey.capabilities.signer == null)
        assertTrue(publicKey.capabilities.privateKeyExporter == null)
    }

    @Test
    fun `restored key materializes once under concurrent first use`() = runTest {
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        if (!supportsPrivateJwkImport(spec, usages)) return@runTest
        val key = restart(generate(spec, usages))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)

        val results = List(16) { index ->
            async {
                val message = "concurrent-$index".encodeToByteArray()
                val signature = key.capabilities.signer!!.sign(message, algorithm)
                key.capabilities.verifier!!.verify(message, signature, algorithm)
            }
        }.awaitAll()

        assertTrue(results.all { it })
    }

    @Test
    fun `provider advertises exact profile and rejects unsupported digest`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN))
        val unsupported = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA3_256)

        assertFalse(unsupported in key.capabilities.signatureAlgorithms)
        assertFails { key.capabilities.signer!!.sign(byteArrayOf(1), unsupported) }
    }

    @Test
    fun `restoration rejects mismatched key metadata`() = runTest {
        val key = generate(KeySpec.Rsa(2048), setOf(KeyUsage.SIGN))
        val jwk = assertIs<EncodedKey.Jwk>(key.storedKey.material)

        assertFails { runtime.restore(key.storedKey.copy(spec = KeySpec.Rsa(3072))) }
        assertFails { runtime.restore(key.storedKey.copy(material = jwk.copy(privateMaterial = false))) }
    }

    @Test
    fun `RSA OAEP plaintext limit is enforced by provider`() = runTest {
        val key = generate(KeySpec.Rsa(2048), setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT))
        val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)

        val valid = key.capabilities.encryptor!!.encrypt(ByteArray(190), algorithm, null)
        assertContentEquals(ByteArray(190), key.capabilities.decryptor!!.decrypt(valid, null))
        assertFails { key.capabilities.encryptor!!.encrypt(ByteArray(191), algorithm, null) }
        assertFails {
            key.capabilities.decryptor!!.decrypt(
                AsymmetricCiphertext.Opaque(
                    algorithm = algorithm,
                    provider = ProviderId("kms"),
                    keyId = KeyId("key"),
                    blob = BinaryData(byteArrayOf(1)),
                ),
                null,
            )
        }
    }

    @Test
    fun `import rejects malformed or contradictory JWK restrictions`() = runTest {
        val key = generate(KeySpec.Rsa(2048), setOf(KeyUsage.DECRYPT))
        val material = assertIs<EncodedKey.Jwk>(key.storedKey.material)
        val parsed = Json.parseToJsonElement(material.data.toByteArray().decodeToString()).let { it as JsonObject }

        fun restricted(name: String, value: JsonElement): StoredKey.Software {
            val encoded = JsonObject(parsed + (name to value)).toString().encodeToByteArray()
            return key.storedKey.copy(material = EncodedKey.Jwk(BinaryData(encoded), privateMaterial = true))
        }

        assertFails { runtime.restore(restricted("use", JsonPrimitive("sig"))) }
        assertFails { runtime.restore(restricted("key_ops", JsonPrimitive("decrypt"))) }
        assertFails {
            runtime.restore(
                restricted(
                    "key_ops",
                    JsonArray(listOf(JsonPrimitive("decrypt"), JsonPrimitive("decrypt"))),
                ),
            )
        }
    }

    @Test
    fun `private JWK key_ops restricts operations on its derived public component`() = runTest {
        suspend fun restoreWithPrivateOperation(key: SoftwareKey, operation: String) {
            val material = assertIs<EncodedKey.Jwk>(key.storedKey.material)
            val parsed = Json.parseToJsonElement(material.data.toByteArray().decodeToString()) as JsonObject
            val encoded = JsonObject(parsed + ("key_ops" to JsonArray(listOf(JsonPrimitive(operation)))))
                .toString().encodeToByteArray()
            runtime.restore(
                key.storedKey.copy(material = EncodedKey.Jwk(BinaryData(encoded), privateMaterial = true)),
            )
        }

        assertFails {
            restoreWithPrivateOperation(
                generate(KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
                "sign",
            )
        }
        assertFails {
            restoreWithPrivateOperation(
                generate(KeySpec.Rsa(2048), setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT)),
                "decrypt",
            )
        }
    }

    @Test
    fun `operational support does not require a key encoding`() {
        assertTrue(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.SIGN,
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
                ),
            ),
        )
    }

    @Test
    fun `portable profile never advertises secp256k1`() {
        assertFalse(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.SIGN,
                    spec = KeySpec.Ec(EcCurve.SECP256K1),
                    usages = setOf(KeyUsage.SIGN),
                    signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
                ),
            ),
        )
    }

    private suspend fun generate(spec: KeySpec, usages: Set<KeyUsage>): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("test-key"),
                spec = spec,
                usages = usages,
            ),
        )

    private suspend fun restart(key: SoftwareKey): SoftwareKey =
        runtime.restore(StoredKeyCodec.decodeFromString(StoredKeyCodec.encodeToString(key.storedKey))) as SoftwareKey

    private fun supportsPrivateJwkImport(spec: KeySpec, usages: Set<KeyUsage>): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = spec,
            usages = usages,
            keyEncoding = KeyEncodingFormat.JWK,
        ),
    )
}
