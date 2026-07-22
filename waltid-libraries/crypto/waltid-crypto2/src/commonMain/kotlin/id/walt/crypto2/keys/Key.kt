package id.walt.crypto2.keys

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestValue
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable(with = KeySerializer::class)
interface Key {
    val id: KeyId
    val spec: KeySpec
    val usages: Set<KeyUsage>
    val capabilities: KeyCapabilities
        get() = KeyCapabilities(
            signer = this as? Signer,
            digestSigner = this as? DigestSigner,
            verifier = this as? Verifier,
            encryptor = this as? Encryptor,
            decryptor = this as? Decryptor,
            keyAgreement = this as? KeyAgreement,
            keyWrapper = this as? KeyWrapper,
            keyUnwrapper = this as? KeyUnwrapper,
            deleter = this as? KeyDeleter,
            publicKeyExporter = this as? PublicKeyExporter,
            privateKeyExporter = this as? PrivateKeyExporter,
        )
}

interface StorableKey : Key {
    val storedKey: StoredKey
}

fun interface Signer {
    suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray
}

fun interface DigestSigner {
    suspend fun signDigest(digest: DigestValue, algorithm: SignatureAlgorithm): ByteArray
}

fun interface Verifier {
    suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean
}

fun interface Encryptor {
    suspend fun encrypt(
        plaintext: ByteArray,
        algorithm: AsymmetricEncryptionAlgorithm,
        associatedData: ByteArray?,
    ): AsymmetricCiphertext
}

fun interface Decryptor {
    suspend fun decrypt(
        ciphertext: AsymmetricCiphertext,
        associatedData: ByteArray?,
    ): ByteArray
}

fun interface KeyAgreement {
    suspend fun generateSharedSecret(
        peerPublicKey: EncodedKey,
        algorithm: KeyAgreementAlgorithm,
    ): BinaryData
}

fun interface KeyWrapper {
    suspend fun wrapKey(key: EncodedKeyMaterial, algorithm: KeyWrappingAlgorithm): WrappedKey
}

fun interface KeyUnwrapper {
    suspend fun unwrapKey(wrappedKey: WrappedKey): EncodedKeyMaterial
}

fun interface KeyDeleter {
    suspend fun delete(): KeyDeletionResult
}

fun interface PublicKeyExporter {
    suspend fun exportPublicKey(): EncodedKey

    suspend fun exportPublicKey(format: KeyEncodingFormat): EncodedKey = exportPublicKey().also {
        require(it.encodingFormat == format) { "Public key export format is not supported: $format" }
    }
}

fun interface PrivateKeyExporter {
    suspend fun exportPrivateKey(): EncodedKey

    suspend fun exportPrivateKey(format: KeyEncodingFormat): EncodedKey = exportPrivateKey().also {
        require(it.encodingFormat == format) { "Private key export format is not supported: $format" }
    }
}

interface SigningKey : Key, Signer

interface DigestSigningKey : Key, DigestSigner

interface VerificationKey : Key, Verifier

interface EncryptingKey : Key, Encryptor {
    suspend fun encrypt(plaintext: ByteArray, algorithm: AsymmetricEncryptionAlgorithm): AsymmetricCiphertext =
        encrypt(plaintext, algorithm, null)

    override suspend fun encrypt(
        plaintext: ByteArray,
        algorithm: AsymmetricEncryptionAlgorithm,
        associatedData: ByteArray?,
    ): AsymmetricCiphertext
}

interface DecryptingKey : Key, Decryptor {
    suspend fun decrypt(ciphertext: AsymmetricCiphertext): ByteArray = decrypt(ciphertext, null)

    override suspend fun decrypt(
        ciphertext: AsymmetricCiphertext,
        associatedData: ByteArray?,
    ): ByteArray
}

interface KeyAgreementKey : Key, KeyAgreement

interface KeyWrappingKey : Key, KeyWrapper

interface KeyUnwrappingKey : Key, KeyUnwrapper

data class KeyCapabilities(
    val signer: Signer? = null,
    val digestSigner: DigestSigner? = null,
    val verifier: Verifier? = null,
    val encryptor: Encryptor? = null,
    val decryptor: Decryptor? = null,
    val keyAgreement: KeyAgreement? = null,
    val keyWrapper: KeyWrapper? = null,
    val keyUnwrapper: KeyUnwrapper? = null,
    val deleter: KeyDeleter? = null,
    val publicKeyExporter: PublicKeyExporter? = null,
    val privateKeyExporter: PrivateKeyExporter? = null,
    val signatureAlgorithms: Set<SignatureAlgorithm> = emptySet(),
    val encryptionAlgorithms: Set<AsymmetricEncryptionAlgorithm> = emptySet(),
    val keyWrappingAlgorithms: Set<KeyWrappingAlgorithm> = emptySet(),
    val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm> = emptySet(),
    val supportsSignatureAlgorithm: (SignatureAlgorithm) -> Boolean = { it in signatureAlgorithms },
    val supportsEncryptionAlgorithm: (AsymmetricEncryptionAlgorithm) -> Boolean = { it in encryptionAlgorithms },
    val supportsKeyAgreementAlgorithm: (KeyAgreementAlgorithm) -> Boolean = { it in keyAgreementAlgorithms },
    val supportsKeyWrappingAlgorithm: (KeyWrappingAlgorithm) -> Boolean = { it in keyWrappingAlgorithms },
)

interface PublicKeyAccessible : Key {
    suspend fun publicKey(): Key
}

interface PublicKeyExportable : Key, PublicKeyExporter

interface PrivateKeyExportable : Key, PrivateKeyExporter

/**
 * A software-key view. Deserialization returns a non-operational handle; call
 * [id.walt.crypto2.CryptoRuntime.restore] before using cryptographic capabilities.
 */
@Serializable(with = SoftwareKeySerializer::class)
interface SoftwareKey : StorableKey {
    override val storedKey: StoredKey.Software
    override val id: KeyId get() = storedKey.id
    override val spec: KeySpec get() = storedKey.spec
    override val usages: Set<KeyUsage> get() = storedKey.usages
}

interface SoftwareVerificationKey : SoftwareKey, VerificationKey, PublicKeyAccessible

interface SoftwareSigningKey : SoftwareKey, SigningKey, PublicKeyAccessible, PrivateKeyExportable {
    override suspend fun publicKey(): SoftwareVerificationKey
}

interface SoftwareSigningAndVerificationKey : SoftwareSigningKey, SoftwareVerificationKey

interface SoftwareEncryptionKey : SoftwareKey, EncryptingKey

interface SoftwareDecryptionKey : SoftwareKey, DecryptingKey, PublicKeyAccessible, PrivateKeyExportable {
    override suspend fun publicKey(): SoftwareEncryptionKey
}

interface SoftwareEncryptionAndDecryptionKey : SoftwareEncryptionKey, SoftwareDecryptionKey

interface SoftwareKeyAgreementKey : SoftwareKey, KeyAgreementKey, PublicKeyExportable, PrivateKeyExportable

/**
 * A managed-key descriptor. Deserialization does not contact its provider; call
 * [id.walt.crypto2.CryptoRuntime.restore] to explicitly materialize the key.
 */
@Serializable(with = ManagedKeySerializer::class)
interface ManagedKey : StorableKey {
    override val storedKey: StoredKey.Managed
    override val id: KeyId get() = storedKey.id
    override val spec: KeySpec get() = storedKey.spec
    override val usages: Set<KeyUsage> get() = storedKey.usages
}

interface DeletableKey : Key, KeyDeleter

sealed interface KeyDeletionResult {
    data object Deleted : KeyDeletionResult
    data class Scheduled(val at: Instant) : KeyDeletionResult
}
