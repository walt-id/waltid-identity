package id.walt.ades.crypto

import id.walt.ades.x509.X509Certificate
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import kotlin.coroutines.cancellation.CancellationException

/** Signature verification over raw public keys, backed by crypto2 software providers. */
object AdesCrypto {
    private val runtime: CryptoRuntime by lazy { CryptoRuntime(softwareProviders = defaultSoftwareKeyProviders()) }

    suspend fun verificationKey(publicKey: PublicKeyInfo): Key = runtime.restore(
        stored = StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("ades-verify"),
            spec = publicKey.keySpec,
            usages = setOf(KeyUsage.VERIFY),
            material = EncodedKey.SpkiDer(BinaryData(publicKey.encoded)),
        ),
    )

    /**
     * Verifies [signature] over [data]. ECDSA signatures may be given as DER ([derEcdsa] = true, CMS/X.509 style) or
     * P1363 (COSE/JWS/XMLDSig). Malformed signatures yield false rather than an exception.
     */
    suspend fun verify(
        publicKey: PublicKeyInfo,
        algorithm: AdesSignatureAlgorithm,
        data: ByteArray,
        signature: ByteArray,
        derEcdsa: Boolean = false,
    ): Boolean = try {
        val sig = if (algorithm.scheme == SignatureScheme.ECDSA && derEcdsa) {
            EcdsaSignatureCodec.derToP1363(signature, publicKey.ecComponentSize ?: error("Not an EC key"))
        } else signature
        val verifier = verificationKey(publicKey).capabilities.verifier ?: error("Key cannot verify")
        verifier.verify(data, sig, algorithm.crypto2(derEcdsa = false))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }

    suspend fun verify(
        certificate: X509Certificate,
        algorithm: AdesSignatureAlgorithm,
        data: ByteArray,
        signature: ByteArray,
        derEcdsa: Boolean = false,
    ): Boolean = verify(certificate.publicKey, algorithm, data, signature, derEcdsa)
}

/**
 * Signing credentials: any crypto2 [Key] able to sign (software, KMS, PKCS#11, ...) together with its X.509
 * certificate chain (signing certificate first, then issuing CAs; the trust anchor may be included).
 */
class AdesSigningCredentials(
    val key: Key,
    val certificateChain: List<X509Certificate>,
    val algorithm: AdesSignatureAlgorithm = AdesSignatureAlgorithm.defaultFor(certificateChain.first().publicKey.keySpec),
) {
    init {
        require(certificateChain.isNotEmpty()) { "Signing certificate is required" }
        requireNotNull(key.capabilities.signer) { "Key does not permit signing" }
    }

    val signingCertificate: X509Certificate get() = certificateChain.first()

    /** Signs [data]. ECDSA output is P1363 unless [derEcdsa] is requested (CMS). */
    suspend fun sign(data: ByteArray, derEcdsa: Boolean = false): ByteArray {
        val raw = key.capabilities.signer!!.sign(data, algorithm.crypto2(derEcdsa = false))
        return if (algorithm.scheme == SignatureScheme.ECDSA && derEcdsa) {
            EcdsaSignatureCodec.p1363ToDer(raw, signingCertificate.publicKey.ecComponentSize!!)
        } else raw
    }
}
