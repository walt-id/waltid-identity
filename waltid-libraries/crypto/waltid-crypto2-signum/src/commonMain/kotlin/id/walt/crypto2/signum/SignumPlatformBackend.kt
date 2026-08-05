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
}

interface SignumPlatformKey {
    val alias: String
    val spec: KeySpec
    val publicKey: EncodedKey.SpkiDer
    val protectionLevel: SignumProtectionLevel
    val attestation: SignumKeyAttestation?
    val signatureAlgorithms: Set<SignatureAlgorithm>
    val keyAgreementAlgorithms: Set<KeyAgreementAlgorithm>

    suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray
    suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean
    suspend fun generateSharedSecret(peerPublicKey: EncodedKey, algorithm: KeyAgreementAlgorithm): BinaryData
}
