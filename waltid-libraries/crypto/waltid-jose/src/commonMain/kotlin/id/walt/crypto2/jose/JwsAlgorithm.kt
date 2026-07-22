package id.walt.crypto2.jose

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.JWK_ALGORITHM_METADATA_KEY
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey

enum class JwsAlgorithm(val identifier: String) {
    ES256("ES256"),
    ES384("ES384"),
    ES512("ES512"),
    ES256K("ES256K"),
    ED25519("Ed25519"),
    ED448("Ed448"),
    EDDSA("EdDSA"),
    RS256("RS256"),
    RS384("RS384"),
    RS512("RS512"),
    PS256("PS256"),
    PS384("PS384"),
    PS512("PS512"),
    ;

    fun toSignatureAlgorithm(): SignatureAlgorithm = when (this) {
        ES256, ES256K -> SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
        ES384 -> SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384, EcdsaSignatureEncoding.IEEE_P1363)
        ES512 -> SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_512, EcdsaSignatureEncoding.IEEE_P1363)
        ED25519, ED448, EDDSA -> SignatureAlgorithm.EdDsa
        RS256 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        RS384 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_384)
        RS512 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_512)
        PS256 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 32)
        PS384 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_384, saltLengthBytes = 48)
        PS512 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_512, saltLengthBytes = 64)
    }

    companion object {
        val fullySpecified: Set<JwsAlgorithm> = entries.filterTo(mutableSetOf()) { it != EDDSA }

        fun parse(identifier: String): JwsAlgorithm = entries.firstOrNull { it.identifier == identifier }
            ?: throw IllegalArgumentException("Unsupported JWS algorithm: $identifier")
    }
}

fun KeySpec.defaultJwsAlgorithm(): JwsAlgorithm = when (this) {
    KeySpec.Ec(EcCurve.P256) -> JwsAlgorithm.ES256
    KeySpec.Ec(EcCurve.P384) -> JwsAlgorithm.ES384
    KeySpec.Ec(EcCurve.P521) -> JwsAlgorithm.ES512
    KeySpec.Ec(EcCurve.SECP256K1) -> JwsAlgorithm.ES256K
    KeySpec.Edwards(EdwardsCurve.ED25519) -> JwsAlgorithm.ED25519
    KeySpec.Edwards(EdwardsCurve.ED448) -> JwsAlgorithm.ED448
    is KeySpec.Rsa -> when {
        bits >= 4096 -> JwsAlgorithm.RS512
        bits >= 3072 -> JwsAlgorithm.RS384
        else -> JwsAlgorithm.RS256
    }
    else -> throw IllegalArgumentException("Key specification $this does not support JWS signing")
}

fun StoredKey.preferredJwsAlgorithm(): JwsAlgorithm =
    metadata[JWK_ALGORITHM_METADATA_KEY]
        ?.let(JwsAlgorithm::parse)
        ?: spec.defaultJwsAlgorithm()

fun Key.preferredJwsAlgorithm(): JwsAlgorithm =
    (this as? StorableKey)?.storedKey?.preferredJwsAlgorithm() ?: spec.defaultJwsAlgorithm()

fun Key.selectJwsAlgorithm(acceptedAlgorithms: Set<String>?): JwsAlgorithm {
    val preferred = preferredJwsAlgorithm()
    if (acceptedAlgorithms == null || preferred.identifier in acceptedAlgorithms) return preferred
    if ((this as? StorableKey)?.storedKey?.metadata?.containsKey(JWK_ALGORITHM_METADATA_KEY) == true) {
        throw IllegalArgumentException("JWK algorithm ${preferred.identifier} is not accepted")
    }
    return JwsAlgorithm.entries.firstOrNull { candidate ->
        candidate.identifier in acceptedAlgorithms &&
            spec.supportsJwsAlgorithm(candidate) &&
            capabilities.supportsSignatureAlgorithm(candidate.toSignatureAlgorithm())
    } ?: throw IllegalArgumentException("No accepted JWS algorithm is supported by the key")
}

fun KeySpec.supportsJwsAlgorithm(algorithm: JwsAlgorithm): Boolean = when (algorithm) {
    JwsAlgorithm.ES256 -> this == KeySpec.Ec(EcCurve.P256)
    JwsAlgorithm.ES384 -> this == KeySpec.Ec(EcCurve.P384)
    JwsAlgorithm.ES512 -> this == KeySpec.Ec(EcCurve.P521)
    JwsAlgorithm.ES256K -> this == KeySpec.Ec(EcCurve.SECP256K1)
    JwsAlgorithm.ED25519 -> this == KeySpec.Edwards(EdwardsCurve.ED25519)
    JwsAlgorithm.ED448 -> this == KeySpec.Edwards(EdwardsCurve.ED448)
    JwsAlgorithm.EDDSA -> this == KeySpec.Edwards(EdwardsCurve.ED25519) ||
        this == KeySpec.Edwards(EdwardsCurve.ED448)
    JwsAlgorithm.RS256,
    JwsAlgorithm.RS384,
    JwsAlgorithm.RS512,
    JwsAlgorithm.PS256,
    JwsAlgorithm.PS384,
    JwsAlgorithm.PS512,
    -> this is KeySpec.Rsa && bits >= 2048
}
