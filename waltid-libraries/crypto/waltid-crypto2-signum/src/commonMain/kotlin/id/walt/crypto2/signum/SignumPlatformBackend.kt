package id.walt.crypto2.signum

import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.serialization.BinaryData

interface SignumPlatformBackend {
    val id: ProviderId

    fun supports(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean

    suspend fun create(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey

    suspend fun load(
        alias: String,
        spec: KeySpec,
        usages: Set<KeyUsage>,
        policy: SignumKeyPolicy,
    ): SignumPlatformKey?

    suspend fun delete(alias: String)

    /** Deletes using the same namespace/access group as creation. */
    suspend fun delete(alias: String, policy: SignumKeyPolicy) = delete(alias)
}

interface SignumPlatformKey {
    val alias: String
    /** Native generation and private-key import are distinct assurance claims. */
    val origin: SignumKeyOrigin get() = SignumKeyOrigin.UNKNOWN
    /** Observed hardware tier, where the platform exposes it. */
    val securityLevel: SignumSecurityLevel get() = SignumSecurityLevel.UNKNOWN
    val spec: KeySpec
    /** Explicit capability for exportable ordinary-Keychain keys; absent for hardware and Keystore keys. */
    val privateKeyExporter: id.walt.crypto2.keys.PrivateKeyExporter? get() = null
    val publicKey: EncodedKey.SpkiDer
    /** Observed protection backing; policy requests must not be used as evidence. */
    val protectionLevel: SignumProtectionLevel
    val attestation: SignumKeyAttestation?
    val signatureAlgorithms: Set<SignatureAlgorithm>
    val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm>

    suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean
    suspend fun generateSharedSecret(peerPublicKey: EncodedKey, algorithm: KeyAgreementAlgorithm): BinaryData
}

/** Optional native import capability. Enclave generation does not imply support for private import. */
interface SignumPrivateKeyImportBackend {
    fun supportsImport(spec: KeySpec, usages: Set<KeyUsage>, policy: SignumKeyPolicy): Boolean
    suspend fun importPrivateKey(alias: String, material: EncodedKey.Jwk, spec: KeySpec,
                                 usages: Set<KeyUsage>, policy: SignumKeyPolicy): SignumPlatformKey
    suspend fun loadImportedKey(alias: String, spec: KeySpec, usages: Set<KeyUsage>,
                                policy: SignumKeyPolicy): SignumPlatformKey?
    suspend fun deleteImportedKey(alias: String, policy: SignumKeyPolicy)
}
