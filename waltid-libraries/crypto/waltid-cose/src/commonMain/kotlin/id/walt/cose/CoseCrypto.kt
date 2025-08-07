package id.walt.cose

import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyTypes

/** A suspendable interface for a COSE-compatible signer. */
fun interface CoseSigner {
    suspend fun sign(data: ByteArray): ByteArray
}

/** A suspendable interface for a COSE-compatible verifier. */
fun interface CoseVerifier {
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean
}

/** Adapts your [Key] to a [CoseSigner]. */
fun Key.toCoseSigner(algorithm: String? = null): CoseSigner {
    require(this.hasPrivateKey) { "Key must have a private part to be used as a signer." }
    return CoseSigner { dataToSign ->
        val customSignatureScheme = toCustomSignatureScheme(algorithm)
        var signature = signRaw(dataToSign, customSignatureScheme) as ByteArray
        if (keyType in KeyTypes.EC_KEYS) {
            signature = EccUtils.convertDERtoIEEEP1363(signature)
        }

        signature
    }
}

/** not yet supported RSA algorithms */
private fun toCustomSignatureScheme(algorithm: String?) =
    when (algorithm) {
        "PS256" -> "SHA256withRSA/PSS"
        "PS384" -> "SHA384withRSA/PSS"
        "PS512" -> "SHA512withRSA/PSS"
        else -> null
    }


/** Adapt [Key] to a [CoseVerifier]. */
fun Key.toCoseVerifier(algorithm: String? = null): CoseVerifier =
    CoseVerifier { data, signature ->
        var signature = signature
        if (keyType in KeyTypes.EC_KEYS) {
            signature = EccUtils.convertP1363toDER(signature)
        }
        val customSignatureScheme = toCustomSignatureScheme(algorithm)
        this.verifyRaw(signed = signature, detachedPlaintext = data, customSignatureScheme).isSuccess
    }

/** Map [KeyType] to a standard COSE Algorithm ID. */
fun KeyType.toCoseAlgorithm(): Int? = when (this) {
    KeyType.Ed25519 -> Cose.Algorithm.EdDSA
    KeyType.secp256k1 -> Cose.Algorithm.ES256K
    KeyType.secp256r1 -> Cose.Algorithm.ES256
    KeyType.secp384r1 -> Cose.Algorithm.ES384
    KeyType.secp521r1 -> Cose.Algorithm.ES512
    KeyType.RSA -> Cose.Algorithm.RS256
    KeyType.RSA3072 -> Cose.Algorithm.RS384
    KeyType.RSA4096 -> Cose.Algorithm.RS512
}
