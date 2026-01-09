package id.walt.cose

import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyTypes
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import kotlinx.serialization.json.jsonPrimitive

/** A suspendable interface for a COSE-compatible signer. */
fun interface CoseSigner {
    suspend fun sign(data: ByteArray): ByteArray
}

/** A suspendable interface for a COSE-compatible verifier. */
fun interface CoseVerifier {
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean
}

/** Adapts your [Key] to a [CoseSigner]. 
 * 
 * @param algorithm Optional algorithm string for custom signature schemes (e.g., "PS256", "PS384", "PS512")
 * @return A CoseSigner that can sign data using this key
 * @throws IllegalArgumentException if the key cannot sign (signRaw() will throw if unsupported)
 */
fun Key.toCoseSigner(algorithm: String? = null): CoseSigner {
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

/** Converts a [Key] to a [CoseKey] by extracting JWK parameters and mapping them to COSE format.
 * 
 * This function works with both standard keys and cloud provider keys (AWS, Azure, etc.).
 * It extracts the public key information from the JWK representation and optionally includes
 * the private key if available.
 * 
 * @return A CoseKey representation of this key
 * @throws IllegalArgumentException if the key type or curve is unsupported
 */
suspend fun Key.toCoseKey(): CoseKey {
    val jwk = exportJWKObject()
    val kty = jwk["kty"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("JWK missing kty")
    val crv = jwk["crv"]?.jsonPrimitive?.content
    
    // Map JWK key type to COSE key type
    val coseKty = when (kty) {
        "EC" -> Cose.KeyTypes.EC2
        "OKP" -> Cose.KeyTypes.OKP
        else -> throw IllegalArgumentException("Unsupported JWK key type: $kty")
    }
    
    // Map JWK curve to COSE curve
    val coseCrv = when (crv) {
        "P-256" -> Cose.EllipticCurves.P_256
        "P-384" -> Cose.EllipticCurves.P_384
        "P-521" -> Cose.EllipticCurves.P_521
        "Ed25519" -> Cose.EllipticCurves.Ed25519
        "Ed448" -> Cose.EllipticCurves.Ed448
        "secp256k1" -> Cose.EllipticCurves.secp256k1
        else -> throw IllegalArgumentException("Unsupported JWK curve: $crv")
    }
    
    // Extract x, y coordinates from JWK
    val x = jwk["x"]?.jsonPrimitive?.content?.base64UrlDecode()
        ?: throw IllegalArgumentException("JWK missing x coordinate")
    val y = if (coseKty == Cose.KeyTypes.EC2) {
        jwk["y"]?.jsonPrimitive?.content?.base64UrlDecode()
            ?: throw IllegalArgumentException("JWK missing y coordinate for EC key")
    } else null
    
    // Extract private key if present (optional - works with cloud keys that don't expose private key)
    val d = jwk["d"]?.jsonPrimitive?.content?.base64UrlDecode()
    
    // Extract kid if present
    val kid = jwk["kid"]?.jsonPrimitive?.content?.encodeToByteArray()
    
    return CoseKey(
        kty = coseKty,
        kid = kid,
        crv = coseCrv,
        x = x,
        y = y,
        d = d
    )
}
