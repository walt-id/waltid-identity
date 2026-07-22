package id.walt.crypto2.jvm

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EncodedKeyMaterial
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.SymmetricKeyType
import id.walt.crypto2.keys.WrappedKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64

class JavaProviderAdapterTest {
    private val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
    private val stored = StoredKey.Managed(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("java-key"),
        spec = KeySpec.Ec(EcCurve.P256),
        usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        provider = ProviderId("java-test"),
        providerSchemaVersion = 1,
        providerData = BinaryData(byteArrayOf(1)),
        publicKey = EncodedKey.Jwk(
            BinaryData(
                """
                {
                  "kty":"EC",
                  "crv":"P-256",
                  "x":"dUpq6W-c6yLniF3k5Lj5j4rFysYXmnHTSK_aOb2JV_A",
                  "y":"urKlUBnHoHIeBP9YuCj9ZwVBewS4yNkaT1J64gQLnEw"
                }
                """.trimIndent().encodeToByteArray(),
            ),
            privateMaterial = false,
        ),
    )

    @Test
    fun `explicit Java provider exposes asynchronous capabilities`() = runTest {
        val javaProvider = TestJavaManagedKeyProvider()
        val runtime = CryptoRuntime(
            softwareProviders = emptyList(),
            managedProviders = listOf(javaProvider.asKotlinProvider()),
        )

        val key = runtime.restore(stored)
        val signer = assertNotNull(key.capabilities.signer)
        val verifier = assertNotNull(key.capabilities.verifier)
        val message = "java-provider".encodeToByteArray()
        val signature = signer.sign(message, algorithm)

        assertContentEquals(message, signature)
        assertTrue(verifier.verify(message, signature, algorithm))
        assertNull(key.capabilities.decryptor)
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(key.capabilities.deleter).delete())
        assertNotNull(key.capabilities.publicKeyExporter)
        assertTrue(
            key.capabilities.supportsSignatureAlgorithm(
                SignatureAlgorithm.Custom("com.example.custom"),
            ),
        )
        assertTrue(key.capabilities.keyAgreementAlgorithms.isEmpty())
        assertTrue(key.capabilities.keyWrappingAlgorithms.isEmpty())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (key.capabilities.signatureAlgorithms as MutableSet).add(SignatureAlgorithm.EdDsa)
        }
        assertTrue(TestJavaManagedKeyProvider.invalidDtosAreRejected())
        assertTrue(TestJavaManagedKeyProvider.ciphertextValueEquality())

        val encryptionKey = runtime.restore(
            stored.copy(
                id = KeyId("java-encryption-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT),
                publicKey = null,
            ),
        )
        val encryptionAlgorithm = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)
        val plaintext = "java-ciphertext".encodeToByteArray()
        val ciphertext = assertNotNull(encryptionKey.capabilities.encryptor)
            .encrypt(plaintext, encryptionAlgorithm, null)
        assertContentEquals(
            plaintext,
            assertNotNull(encryptionKey.capabilities.decryptor).decrypt(ciphertext, null),
        )
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(encryptionKey.capabilities.decryptor).decrypt(
                AsymmetricCiphertext.Opaque(
                    algorithm = encryptionAlgorithm,
                    provider = ProviderId("different-provider"),
                    keyId = encryptionKey.id,
                    blob = BinaryData(byteArrayOf(1)),
                ),
                null,
            )
        }
        val agreementKey = runtime.restore(
            stored.copy(id = KeyId("java-agreement-key"), usages = setOf(KeyUsage.KEY_AGREEMENT)),
        )
        assertTrue(
            KeyAgreementAlgorithm.Named("ECDH", mapOf("profile" to "custom")) in
                agreementKey.capabilities.keyAgreementAlgorithms,
        )

        val wrappingKey = runtime.restore(
            stored.copy(
                id = KeyId("java-wrapping-key"),
                spec = KeySpec.Symmetric(SymmetricKeyType.AES, 256),
                usages = setOf(KeyUsage.WRAP, KeyUsage.UNWRAP),
                publicKey = null,
            ),
        )
        val wrappingAlgorithm = KeyWrappingAlgorithm.BuiltIn("A256KW")
        assertTrue(wrappingAlgorithm in wrappingKey.capabilities.keyWrappingAlgorithms)
        val symmetricJwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"oct","k":"${Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32))}"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = true,
        )
        val wrapped = assertNotNull(wrappingKey.capabilities.keyWrapper).wrapKey(
            EncodedKeyMaterial(KeySpec.Symmetric(SymmetricKeyType.AES, 256), symmetricJwk),
            wrappingAlgorithm,
        )
        assertNotNull(wrappingKey.capabilities.keyUnwrapper).unwrapKey(wrapped)
        val shortSymmetricJwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"oct","k":"${Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(16))}"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = true,
        )
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(wrappingKey.capabilities.keyWrapper).wrapKey(
                EncodedKeyMaterial(KeySpec.Symmetric(SymmetricKeyType.AES, 128), shortSymmetricJwk),
                wrappingAlgorithm,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(wrappingKey.capabilities.keyUnwrapper).unwrapKey(
                (wrapped as WrappedKey.Raw).copy(wrappingKeyId = KeyId("different-key")),
            )
        }
        assertFailsWith<IllegalStateException> {
            signer.sign(byteArrayOf(), algorithm)
        }

        val generated = runtime.generateManagedKey(
            ProviderId("java-test"),
            GenerateManagedKeyRequest(
                id = KeyId("generated-java-key"),
                spec = stored.spec,
                usages = stored.usages,
                providerOptions = BinaryData(byteArrayOf(2)),
            ),
        )
        assertEquals(KeyId("generated-java-key"), generated.id)

        runtime.close()
        assertTrue(javaProvider.isClosed)
    }

    @Test
    fun `ServiceLoader discovers Java providers`() {
        val providers = TestJavaManagedKeyProvider.discoverProviders()

        assertEquals(listOf(ProviderId("java-test")), providers.map { it.id })
    }

    @Test
    fun `adapter exposes only capabilities inside stored policy`() = runTest {
        val runtime = CryptoRuntime(
            softwareProviders = emptyList(),
            managedProviders = listOf(TestJavaManagedKeyProvider().asKotlinProvider()),
        )

        val verificationOnly = runtime.restore(stored.copy(usages = setOf(KeyUsage.VERIFY)))
        assertNull(verificationOnly.capabilities.signer)
        assertNotNull(verificationOnly.capabilities.verifier)
    }

    @Test
    fun `adapter rejects changed managed-key identity`() = runTest {
        val changed = stored.copy(provider = ProviderId("changed-identity"))
        val runtime = CryptoRuntime(
            softwareProviders = emptyList(),
            managedProviders = listOf(ChangedIdentityJavaProvider().asKotlinProvider()),
        )

        assertFailsWith<IllegalArgumentException> {
            runtime.restore(changed)
        }
    }

    @Test
    fun `adapter reports null CompletionStage clearly`() = runTest {
        val nullStored = stored.copy(provider = ProviderId("null-stage"))
        val runtime = CryptoRuntime(
            softwareProviders = emptyList(),
            managedProviders = listOf(NullStageJavaProvider().asKotlinProvider()),
        )

        val failure = assertFailsWith<IllegalArgumentException> { runtime.restore(nullStored) }
        assertTrue(failure.message!!.contains("null CompletionStage"))
    }
}
