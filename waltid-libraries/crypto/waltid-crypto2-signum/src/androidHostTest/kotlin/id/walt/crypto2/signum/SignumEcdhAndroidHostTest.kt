package id.walt.crypto2.signum

import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.supreme.sign.EphemeralKey
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.toPublicJwk
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

class SignumEcdhAndroidHostTest {
    @Test
    fun `agreement-only keys accept JWK and SPKI peers and derive equal secrets`() = runTest {
        val backend = EphemeralBackend()
        val generatedFirst = generateAgreementKey(backend, "first", EcCurve.P256)
        val first = SignumManagedKeyProvider(backend).restore(generatedFirst.storedKey)
        val second = generateAgreementKey(backend, "second", EcCurve.P256)
        val firstAgreement = assertNotNull(first.capabilities.keyAgreement)
        val secondAgreement = assertNotNull(second.capabilities.keyAgreement)
        val firstSpki = assertNotNull(first.capabilities.publicKeyExporter).exportPublicKey()
        val secondSpki = assertNotNull(second.capabilities.publicKeyExporter).exportPublicKey()
        val secondJwk = secondSpki.toPublicJwk(second.spec)

        val spkiSecret = firstAgreement.generateSharedSecret(secondSpki, KeyAgreementAlgorithm.Ecdh)
        val jwkSecret = firstAgreement.generateSharedSecret(secondJwk, KeyAgreementAlgorithm.Ecdh)
        val reverseSecret = secondAgreement.generateSharedSecret(firstSpki, KeyAgreementAlgorithm.Ecdh)

        assertContentEquals(spkiSecret.toByteArray(), jwkSecret.toByteArray())
        assertContentEquals(spkiSecret.toByteArray(), reverseSecret.toByteArray())
        assertNull(first.capabilities.signer)
        assertTrue(first.capabilities.signatureAlgorithms.isEmpty())
        assertEquals(setOf(KeyAgreementAlgorithm.Ecdh), first.capabilities.keyAgreementAlgorithms)
    }

    @Test
    fun `ECDH rejects wrong curves and key formats`() = runTest {
        val backend = EphemeralBackend()
        val p256 = generateAgreementKey(backend, "p256", EcCurve.P256)
        val p384 = generateAgreementKey(backend, "p384", EcCurve.P384)
        val agreement = assertNotNull(p256.capabilities.keyAgreement)
        val p384Spki = assertNotNull(p384.capabilities.publicKeyExporter).exportPublicKey()
        val p384Jwk = p384Spki.toPublicJwk(p384.spec)

        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(p384Spki, KeyAgreementAlgorithm.Ecdh)
        }
        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(p384Jwk, KeyAgreementAlgorithm.Ecdh)
        }
        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(
                EncodedKey.Pkcs8Der(BinaryData(byteArrayOf(1))),
                KeyAgreementAlgorithm.Ecdh,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(
                EncodedKey.SpkiDer(BinaryData(byteArrayOf(1))),
                KeyAgreementAlgorithm.Ecdh,
            )
        }
    }

    private suspend fun generateAgreementKey(
        backend: SignumPlatformBackend,
        id: String,
        curve: EcCurve,
    ): SignumManagedKey = SignumManagedKeyProvider(backend).generateSignumKey(
        GenerateManagedKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(curve),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
            providerOptions = SignumKeyOptions(
                policy = SignumKeyPolicy(keyAgreement = true),
            ).encode(),
        )
    )

    private class EphemeralBackend : SignumPlatformBackend {
        override val id = ProviderId("ephemeral-signum")
        private val keys = mutableMapOf<String, SignumPlatformKey>()

        override fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean =
            spec is KeySpec.Ec && usages == setOf(KeyUsage.KEY_AGREEMENT) && policy.keyAgreement

        override suspend fun create(
            alias: String,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy,
        ): SignumPlatformKey {
            require(supports(spec, usages, policy))
            val ecSpec = spec as KeySpec.Ec
            val key = assertNotNull(
                EphemeralKey {
                    ec { curve = ecSpec.curve.toSignumCurve() }
                }.getOrNull() as? EphemeralKey.EC
            )
            val signer = key.signer().getOrThrow()
            return object : SignumPlatformKey {
                override val alias = alias
                override val spec = ecSpec
                override val publicKey = EncodedKey.SpkiDer(BinaryData(key.publicKey.encodeToTlv().derEncoded))
                override val protectionLevel = SignumProtectionLevel.SOFTWARE
                override val attestation: SignumKeyAttestation? = null
                override val signatureAlgorithms = setOf(
                    SignatureAlgorithm.Ecdsa(ecSpec.curve.digestAlgorithm())
                )
                override val keyAgreementAlgorithms = setOf(KeyAgreementAlgorithm.Ecdh)

                override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray =
                    error("Agreement-only fake key must not sign")

                override suspend fun verify(
                    data: ByteArray,
                    signature: ByteArray,
                    algorithm: SignatureAlgorithm,
                ): Boolean = error("Agreement-only fake key must not verify")

                override suspend fun generateSharedSecret(
                    peerPublicKey: EncodedKey,
                    algorithm: KeyAgreementAlgorithm,
                ): BinaryData {
                    require(algorithm == KeyAgreementAlgorithm.Ecdh)
                    return BinaryData(signer.keyAgreement(peerPublicKey.toSignumEcdhPeer(ecSpec)).getOrThrow())
                }
            }.also { keys[alias] = it }
        }

        override suspend fun load(
            alias: String,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy,
        ): SignumPlatformKey? = keys[alias]

        override suspend fun delete(alias: String) {
            keys.remove(alias)
        }
    }
}

private fun EcCurve.toSignumCurve(): ECCurve = when (this) {
    EcCurve.P256 -> ECCurve.SECP_256_R_1
    EcCurve.P384 -> ECCurve.SECP_384_R_1
    EcCurve.P521 -> ECCurve.SECP_521_R_1
    else -> error("Unsupported test curve: $name")
}

private fun EcCurve.digestAlgorithm(): DigestAlgorithm = when (this) {
    EcCurve.P256 -> DigestAlgorithm.SHA_256
    EcCurve.P384 -> DigestAlgorithm.SHA_384
    EcCurve.P521 -> DigestAlgorithm.SHA_512
    else -> error("Unsupported test curve: $name")
}
