package id.walt.crypto2.signum

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SignumManagedKeyProviderTest {
    @Test
    fun `direct generation has inferred ManagedKey serialization and an explicit typed API`() = runTest {
        val provider = SignumManagedKeyProvider(FakeBackend())
        val request = GenerateManagedKeyRequest(
            id = KeyId("serializable-signum"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN),
            providerOptions = SignumKeyOptions().encode(),
            metadata = mapOf("tenant" to "example"),
        )

        val generated = provider.generate(request)
        val decoded = Json.decodeFromString<ManagedKey>(Json.encodeToString(generated))
        val typed: SignumManagedKey = provider.generateSignumKey(
            request.copy(id = KeyId("typed-signum")),
        )

        assertEquals(generated.storedKey, decoded.storedKey)
        assertEquals(mapOf("tenant" to "example"), decoded.storedKey.metadata)
        assertEquals(KeyId("typed-signum"), typed.id)
    }

    @Test
    fun `existing platform alias is adopted without replacing key material`() = runTest {
        val backend = FakeBackend(protectionLevel = SignumProtectionLevel.HARDWARE)
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        backend.create("existing-alias", spec, usages, SignumKeyPolicy())
        val provider = SignumManagedKeyProvider(backend)

        val stored = provider.storedKeyForExisting(
            id = KeyId("logical-key"),
            spec = spec,
            usages = usages,
            alias = "existing-alias",
        )
        val restored = assertIs<SignumManagedKey>(
            runtime(backend).restore(
                StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(stored))
            )
        )

        assertEquals(SignumProtectionLevel.HARDWARE, restored.protectionLevel)
        assertNotNull(restored.capabilities.signer).sign(
            "message".encodeToByteArray(),
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
        )
        assertFalse("existing-alias" in backend.deletedAliases)
        assertTrue("existing-alias" in stored.providerData.toByteArray().decodeToString())
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
        assertTrue("existing-alias" in backend.deletedAliases)
    }

    @Test
    fun `missing platform alias cannot be adopted`() = runTest {
        val provider = SignumManagedKeyProvider(FakeBackend())

        assertFailsWith<IllegalStateException> {
            provider.storedKeyForExisting(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                alias = "missing-alias",
            )
        }
    }

    @Test
    fun `descriptor deletion rejects an alias other than the expected mobile alias`() = runTest {
        val backend = FakeBackend()
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        backend.create("mobile-alias", spec, usages, SignumKeyPolicy())
        backend.create("other-alias", spec, usages, SignumKeyPolicy())
        val provider = SignumManagedKeyProvider(backend)
        val stored = provider.storedKeyForExisting(
            id = KeyId("mobile-alias"),
            spec = spec,
            usages = usages,
            alias = "mobile-alias",
        )
        val altered = stored.copy(
            providerData = BinaryData(
                stored.providerData.toByteArray().decodeToString()
                    .replace("mobile-alias", "other-alias")
                    .encodeToByteArray()
            )
        )

        assertFailsWith<IllegalArgumentException> {
            provider.delete(altered, expectedAlias = stored.id.value)
        }
        assertFalse("other-alias" in backend.deletedAliases)
    }

    @Test
    fun `key survives restart with DER conversion and attestation`() = runTest {
        val challenge = BinaryData(byteArrayOf(1, 2, 3))
        val attestation = SignumKeyAttestation("test", BinaryData(byteArrayOf(4, 5, 6)))
        val backend = FakeBackend(
            protectionLevel = SignumProtectionLevel.HARDWARE,
            attestation = attestation,
        )
        val options = SignumKeyOptions(
            alias = "hardware-key",
            policy = SignumKeyPolicy(
                hardware = SignumHardwarePolicy.REQUIRED,
                attestationChallenge = challenge,
            ),
        )
        val generated = runtime(backend).generateManagedKey(
            backend.id,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val restored = assertIs<SignumManagedKey>(
            runtime(backend).restore(
                StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
            )
        )
        assertEquals(SignumProtectionLevel.HARDWARE, restored.protectionLevel)
        assertEquals(attestation, restored.attestation)

        val derAlgorithm = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val der = assertNotNull(restored.capabilities.signer).sign("message".encodeToByteArray(), derAlgorithm)
        assertContentEquals(backend.signature, EcdsaSignatureCodec.derToP1363(der, 32))
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("message".encodeToByteArray(), der, derAlgorithm))
        val jws = CompactJws.sign("jose".encodeToByteArray(), restored, JwsAlgorithm.ES256)
        assertEquals("jose", CompactJws.verify(jws, restored, JwsAlgorithm.ES256).payload.decodeToString())
        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = "cose".encodeToByteArray(),
            key = restored,
        )
        assertTrue(cose.verify(restored, Cose.Algorithm.ES256))
    }

    @Test
    fun `agreement-only key exposes ECDH without signer capability`() = runTest {
        val backend = FakeBackend()
        val key = runtime(backend).generateManagedKey(
            backend.id,
            GenerateManagedKeyRequest(
                id = KeyId("agreement-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
                providerOptions = SignumKeyOptions(
                    policy = SignumKeyPolicy(keyAgreement = true)
                ).encode(),
            ),
        )

        assertEquals(null, key.capabilities.signer)
        assertTrue(key.capabilities.signatureAlgorithms.isEmpty())
        assertEquals(setOf(KeyAgreementAlgorithm.Ecdh), key.capabilities.keyAgreementAlgorithms)
        assertContentEquals(
            ByteArray(32) { 7 },
            assertNotNull(key.capabilities.keyAgreement).generateSharedSecret(
                EncodedKey.SpkiDer(BinaryData(byteArrayOf(1))),
                KeyAgreementAlgorithm.Ecdh,
            ).toByteArray(),
        )
    }

    @Test
    fun `key agreement rejects non EC keys`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            SignumManagedKeyProvider(FakeBackend()).generate(
                GenerateManagedKeyRequest(
                    id = KeyId("rsa-agreement"),
                    spec = KeySpec.Rsa(2048),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                    providerOptions = SignumKeyOptions(
                        policy = SignumKeyPolicy(keyAgreement = true)
                    ).encode(),
                )
            )
        }

        assertTrue(failure.message.orEmpty().contains("requires an EC key"))
    }

    @Test
    fun `signing key does not advertise backend key agreement`() = runTest {
        val backend = FakeBackend()
        val key = runtime(backend).generateManagedKey(
            backend.id,
            GenerateManagedKeyRequest(
                id = KeyId("signing-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                providerOptions = SignumKeyOptions().encode(),
            ),
        )

        assertEquals(null, key.capabilities.keyAgreement)
        assertTrue(key.capabilities.keyAgreementAlgorithms.isEmpty())
    }

    @Test
    fun `required hardware failure deletes the generated alias`() = runTest {
        val backend = FakeBackend(protectionLevel = SignumProtectionLevel.SOFTWARE)

        assertFailsWith<IllegalArgumentException> {
            runtime(backend).generateManagedKey(
                backend.id,
                GenerateManagedKeyRequest(
                    id = KeyId("logical-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    providerOptions = SignumKeyOptions(
                        policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED)
                    ).encode(),
                ),
            )
        }
        assertEquals(listOf("logical-key"), backend.deletedAliases)
    }

    @Test
    fun `self reported hardware without attestation is rejected`() = runTest {
        val backend = FakeBackend(protectionLevel = SignumProtectionLevel.HARDWARE)

        val failure = assertFailsWith<IllegalArgumentException> {
            runtime(backend).generateManagedKey(
                backend.id,
                GenerateManagedKeyRequest(
                    id = KeyId("unattested-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    providerOptions = SignumKeyOptions(
                        policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED)
                    ).encode(),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("attested hardware"))
        assertEquals(listOf("unattested-key"), backend.deletedAliases)
    }

    @Test
    fun `required hardware policy is unknown without attestation evidence`() {
        val policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.REQUIRED)

        assertEquals(SignumProtectionLevel.UNKNOWN, policy.effectiveProtection(null))
        assertEquals(
            SignumProtectionLevel.HARDWARE,
            policy.effectiveProtection(SignumKeyAttestation("test", BinaryData(byteArrayOf(1)))),
        )
    }

    @Test
    fun `backend create failure does not delete a potentially existing alias`() = runTest {
        val backend = FakeBackend(createFailure = IllegalStateException("alias already exists"))

        assertFailsWith<IllegalStateException> {
            runtime(backend).generateManagedKey(
                backend.id,
                GenerateManagedKeyRequest(
                    id = KeyId("existing-alias"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    providerOptions = SignumKeyOptions().encode(),
                ),
            )
        }

        assertFalse("existing-alias" in backend.deletedAliases)
    }

    @Test
    fun `signing cancellation propagates unchanged`() = runTest {
        val cancellation = CancellationException("user cancelled")
        val backend = FakeBackend(signFailure = cancellation)
        val key = runtime(backend).generateManagedKey(
            backend.id,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                providerOptions = SignumKeyOptions().encode(),
            ),
        )

        val thrown = assertFailsWith<CancellationException> {
            assertNotNull(key.capabilities.signer).sign(
                byteArrayOf(1),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            )
        }
        assertEquals(cancellation, thrown)
    }

    private fun runtime(backend: SignumPlatformBackend): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(SignumManagedKeyProvider(backend)),
    )

    private class FakeBackend(
        private val protectionLevel: SignumProtectionLevel = SignumProtectionLevel.UNKNOWN,
        private val attestation: SignumKeyAttestation? = null,
        private val signFailure: Throwable? = null,
        private val createFailure: Throwable? = null,
    ) : SignumPlatformBackend {
        override val id = ProviderId("fake-signum")
        val signature = ByteArray(64) { it.toByte() }
        val deletedAliases = mutableListOf<String>()
        private val keys = mutableMapOf<String, SignumPlatformKey>()

        override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
            spec == KeySpec.Ec(EcCurve.P256)

        override suspend fun create(
            alias: String,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy,
        ): SignumPlatformKey {
            createFailure?.let { throw it }
            return FakeKey(alias, spec).also { keys[alias] = it }
        }

        override suspend fun load(
            alias: String,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy,
        ): SignumPlatformKey? = keys[alias]

        override suspend fun delete(alias: String) {
            deletedAliases += alias
            keys.remove(alias)
        }

        private inner class FakeKey(
            override val alias: String,
            override val spec: KeySpec,
        ) : SignumPlatformKey {
            override val publicKey = EncodedKey.SpkiDer(BinaryData(byteArrayOf(0x30, 1, 2, 3)))
            override val protectionLevel = this@FakeBackend.protectionLevel
            override val attestation = this@FakeBackend.attestation
            override val signatureAlgorithms = setOf(SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
            override val keyAgreementAlgorithms = setOf(KeyAgreementAlgorithm.Ecdh)

            override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
                signFailure?.let { throw it }
                return signature
            }

            override suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean =
                signature.contentEquals(this@FakeBackend.signature)

            override suspend fun generateSharedSecret(
                peerPublicKey: EncodedKey,
                algorithm: KeyAgreementAlgorithm,
            ): BinaryData = BinaryData(ByteArray(32) { 7 })
        }
    }
}
