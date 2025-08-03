package id.walt.crypto.keys

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/*
 * ECC vs ECDSA vs EdDSA vs ECDH
 */

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class KeyType(val jwsAlg: String, val jwkKty: String, val jwkCurve: String?, val oid: String?) {
    // EdDSA
    /** EdDSA + Curve25519 */
    Ed25519(jwsAlg = "EdDSA", jwkKty = "OKP", jwkCurve = "Ed25519", oid = null),

    // TODO: Ed448(jwsAlg = "Ed448", oid = null)
    // TODO: X25519(jwsAlg = "X25519", oid = null)
    // TODO: X448(jwsAlg = "X448", oid = null)

    // ECC
    /** SECP P-256K1 - ECDSA + SECG curve secp256k1 (Koblitz curve as used in Bitcoin), OID = 1.3.132.0.10 */
    secp256k1(jwsAlg = "ES256K", jwkKty = "EC", jwkCurve = "secp256k1", oid = "1.3.132.0.10"),

    /** NIST P-256: ECDSA + SECG curve secp256r1 (P-256), OID = 1.2.840.10045.3.1.7 */
    secp256r1(jwsAlg = "ES256", jwkKty = "EC", jwkCurve = "P-256", oid = "1.2.840.10045.3.1.7"),

    // new: larger sizes
    /** NIST P-384, OID = 1.3.132.0.34 */
    secp384r1(jwsAlg = "ES384", jwkKty = "EC", jwkCurve = "P-384", oid = "1.3.132.0.34"),

    /** NIST P-521, OID = 1.3.132.0.35 */
    secp521r1(jwsAlg = "ES512", jwkKty = "EC", jwkCurve = "P-521", oid = "1.3.132.0.35"),

    // RSA

    /*
     * In theory, it is not mandatory to use matching key sizes
     * (RSA2048 -> SHA256, RSA3072 -> SHA384, RSA4096 -> SHA512).
     * However, it is considered industry best practice, as:
     *
     * Using a larger key with a weaker hash (e.g. 4096-bit key with RS256):
     * While the RSA operation would be very secure, the overall security
     * is limited by the strength of the hash function.
     *
     * Using a smaller key with a stronger hash (e.g. 2048-bit key with RS512):
     * Here, the security of the signature is limited by the strength
     * of the RSA key.
     *
     */

    /** RS256 */
    RSA(
        jwsAlg = "RS256",
        jwkKty = "RSA",
        jwkCurve = null,
        oid = null
    ), // WARNING: do not remove this enum name. There might be old keys existing with it.
    RSA3072(jwsAlg = "RS384", jwkKty = "RSA", jwkCurve = null, oid = null),
    RSA4096(jwsAlg = "RS512", jwkKty = "RSA", jwkCurve = null, oid = null)

}

object KeyTypes {
    val EC_KEYS = KeyType.entries.filter { it.jwkKty == "EC" }

    /** kty -> crv mapping */
    val JWK_MAPPING = KeyType.entries.associateBy { it.jwkKty to it.jwkCurve }

    fun getKeyTypeByJwkId(jwkKty: String, jwkCrv: String?): KeyType =
        when (jwkKty) {
            "EC" if jwkCrv == "P-256K" -> KeyType.secp256k1
            else -> JWK_MAPPING[jwkKty to jwkCrv]
                ?: throw IllegalArgumentException("Unknown JWK combination: kty=$jwkKty, crv=$jwkCrv")
        }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
enum class KeyCategory {
    RSA,
    ECC,
    EdDSA
}

/*
 * RS256, RS384, RS512 (RSASSA-PKCS1-v1_5 using SHA-256/384/512)
 * PS256, PS384, PS512 (RSASSA-PSS using SHA-256/384/512 and MGF1 with SHA-256/384/512)
 *
 * ES256, ES384, ES512 (ECDSA     using P-256/384/512 and SHA-256/384/512)
 * ES256K (ECDSA using secp256k1 curve and SHA-256)
 * EdDSA	(EdDSA signature algorithms)
 *
 * Current: ES256, ES256K, RS256, EdDSA EdDSA
 */
