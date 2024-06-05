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
enum class KeyType {
    // EdDSA
    Ed25519, // EdDSA + Curve25519

    // ECC
    secp256k1, // ECDSA + SECG curve secp256k1 (Koblitz curve as used in Bitcoin)
    secp256r1, // ECDSA + SECG curve secp256r1 ("NIST P-256")

    // RSA
    RSA // RSA
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
