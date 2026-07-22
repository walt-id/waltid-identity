@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.ExperimentalSerializationApi

fun KeySpec.defaultCoseSignatureAlgorithm(): Int = when (this) {
    KeySpec.Ec(EcCurve.P256) -> Cose.Algorithm.ES256
    KeySpec.Ec(EcCurve.P384) -> Cose.Algorithm.ES384
    KeySpec.Ec(EcCurve.P521) -> Cose.Algorithm.ES512
    KeySpec.Ec(EcCurve.SECP256K1) -> Cose.Algorithm.ES256K
    is KeySpec.Edwards -> Cose.Algorithm.EdDSA
    is KeySpec.Rsa -> when {
        bits >= 4096 -> Cose.Algorithm.RS512
        bits >= 3072 -> Cose.Algorithm.RS384
        else -> Cose.Algorithm.RS256
    }
    else -> throw IllegalArgumentException("Key specification $this does not support COSE signing")
}

fun Set<Int>.acceptsCoseAlgorithm(protectedAlgorithm: Int, p256Key: Boolean): Boolean =
    protectedAlgorithm in this ||
        protectedAlgorithm == Cose.Algorithm.ES256 && p256Key && Cose.Algorithm.ESP256 in this

fun Key.selectCoseSignatureAlgorithm(acceptedAlgorithms: Set<Int>?): Int {
    val preferred = spec.defaultCoseSignatureAlgorithm()
    val p256Key = spec == KeySpec.Ec(EcCurve.P256)
    if (acceptedAlgorithms == null || acceptedAlgorithms.acceptsCoseAlgorithm(preferred, p256Key)) return preferred
    return acceptedAlgorithms.asSequence()
        .map { if (it == Cose.Algorithm.ESP256 && p256Key) Cose.Algorithm.ES256 else it }
        .distinct()
        .firstOrNull { candidate ->
            runCatching {
                validateCoseKeySpec(this, candidate)
                capabilities.signer != null &&
                    capabilities.supportsSignatureAlgorithm(candidate.toCrypto2SignatureAlgorithm())
            }.getOrDefault(false)
        }
        ?: throw IllegalArgumentException("No accepted COSE algorithm is supported by the key")
}

fun Int.toCrypto2SignatureAlgorithm(): SignatureAlgorithm = when (this) {
    Cose.Algorithm.ES256, Cose.Algorithm.ES256K ->
        SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
    Cose.Algorithm.ES384 -> SignatureAlgorithm.Ecdsa(
        DigestAlgorithm.SHA_384,
        EcdsaSignatureEncoding.IEEE_P1363,
    )
    Cose.Algorithm.ES512 -> SignatureAlgorithm.Ecdsa(
        DigestAlgorithm.SHA_512,
        EcdsaSignatureEncoding.IEEE_P1363,
    )
    Cose.Algorithm.EdDSA -> SignatureAlgorithm.EdDsa
    Cose.Algorithm.PS256 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 32)
    Cose.Algorithm.PS384 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_384, saltLengthBytes = 48)
    Cose.Algorithm.PS512 -> SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_512, saltLengthBytes = 64)
    Cose.Algorithm.RS256 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
    Cose.Algorithm.RS384 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_384)
    Cose.Algorithm.RS512 -> SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_512)
    else -> throw IllegalArgumentException("Unsupported COSE signature algorithm: $this")
}

fun Key.toCoseSigner(algorithm: Int): CoseSigner {
    val signatureAlgorithm = algorithm.toCrypto2SignatureAlgorithm()
    validateCoseKeySpec(this, algorithm)
    require(capabilities.supportsSignatureAlgorithm(signatureAlgorithm)) {
        "Key does not support COSE algorithm $algorithm"
    }
    val signer = requireNotNull(capabilities.signer) { "Key does not permit signing" }
    return CoseSigner { data ->
        signer.sign(data, signatureAlgorithm).also { validateCoseSignatureLength(this, algorithm, it) }
    }
}

fun Key.toCoseVerifier(algorithm: Int): CoseVerifier {
    val signatureAlgorithm = algorithm.toCrypto2SignatureAlgorithm()
    validateCoseKeySpec(this, algorithm)
    require(capabilities.supportsSignatureAlgorithm(signatureAlgorithm)) {
        "Key does not support COSE algorithm $algorithm"
    }
    val verifier = requireNotNull(capabilities.verifier) { "Key does not permit verification" }
    return CoseVerifier { data, signature ->
        validateCoseSignatureLength(this, algorithm, signature)
        verifier.verify(data, signature, signatureAlgorithm)
    }
}

suspend fun CoseSign1.verify(
    key: Key,
    allowedAlgorithms: Set<Int>,
    externalAad: ByteArray = byteArrayOf(),
): Boolean {
    require(payload != null) { "Attached COSE verification requires an attached payload" }
    val algorithm = validatedProtectedAlgorithm()
    require(algorithm in allowedAlgorithms) { "COSE algorithm is not allowed" }
    return verify(key.toCoseVerifier(algorithm), externalAad)
}

suspend fun CoseSign1.verify(
    key: Key,
    expectedAlgorithm: Int,
    externalAad: ByteArray = byteArrayOf(),
): Boolean = verify(key, setOf(expectedAlgorithm), externalAad)

suspend fun CoseSign1.verifyDetached(
    key: Key,
    detachedPayload: ByteArray,
    allowedAlgorithms: Set<Int>,
    externalAad: ByteArray = byteArrayOf(),
): Boolean {
    val algorithm = validatedProtectedAlgorithm()
    require(algorithm in allowedAlgorithms) { "COSE algorithm is not allowed" }
    return verifyDetached(key.toCoseVerifier(algorithm), detachedPayload, externalAad)
}

suspend fun CoseSign1.verifyDetached(
    key: Key,
    detachedPayload: ByteArray,
    expectedAlgorithm: Int,
    externalAad: ByteArray = byteArrayOf(),
): Boolean = verifyDetached(key, detachedPayload, setOf(expectedAlgorithm), externalAad)

suspend fun CoseSign1.Companion.createAndSign(
    protectedHeaders: CoseHeaders,
    unprotectedHeaders: CoseHeaders = CoseHeaders(),
    payload: ByteArray,
    key: Key,
    externalAad: ByteArray = byteArrayOf(),
): CoseSign1 {
    validateCoseHeaderSeparation(protectedHeaders, unprotectedHeaders)
    val algorithm = requireNotNull(protectedHeaders.algorithm) { "COSE algorithm must be protected" }
    return createAndSign(
        protectedHeaders,
        unprotectedHeaders,
        payload,
        key.toCoseSigner(algorithm),
        externalAad,
    )
}

suspend fun CoseSign1.Companion.createAndSignDetached(
    protectedHeaders: CoseHeaders,
    unprotectedHeaders: CoseHeaders = CoseHeaders(),
    detachedPayload: ByteArray,
    key: Key,
    externalAad: ByteArray = byteArrayOf(),
): CoseSign1 {
    validateCoseHeaderSeparation(protectedHeaders, unprotectedHeaders)
    val algorithm = requireNotNull(protectedHeaders.algorithm) { "COSE algorithm must be protected" }
    return createAndSignDetached(
        protectedHeaders,
        unprotectedHeaders,
        detachedPayload,
        key.toCoseSigner(algorithm),
        externalAad,
    )
}

fun CoseSign1.protectedAlgorithm(): Int = validatedProtectedAlgorithm()

private fun CoseSign1.validatedProtectedAlgorithm(): Int {
    require(protected.isNotEmpty()) { "COSE algorithm must be protected" }
    val protectedHeaders = coseCompliantCbor.decodeFromByteArray<CoseHeaders>(protected)
    validateCoseHeaderSeparation(protectedHeaders, unprotected)
    return requireNotNull(protectedHeaders.algorithm) {
        "COSE protected algorithm is missing"
    }
}

private fun validateCoseHeaderSeparation(protected: CoseHeaders, unprotected: CoseHeaders) {
    require(unprotected.criticalHeaders == null) { "COSE critical headers must be protected" }
    val protectedLabels = protected.presentLabels()
    val duplicateLabels = protectedLabels intersect unprotected.presentLabels()
    require(duplicateLabels.isEmpty()) { "COSE protected and unprotected headers must be disjoint" }
    protected.criticalHeaders?.let { critical ->
        require(critical.isNotEmpty()) { "COSE critical headers must not be empty" }
        require(Cose.HeaderLabel.CRIT !in critical) { "COSE crit header cannot mark itself critical" }
        require(critical.distinct().size == critical.size) { "COSE critical headers must not contain duplicates" }
        require(critical.all { it in processedCriticalHeaderLabels && it in protectedLabels }) {
            "COSE critical header is unsupported or not protected"
        }
    }
}

private fun CoseHeaders.presentLabels(): Set<Int> = buildSet {
    if (algorithm != null) add(Cose.HeaderLabel.ALG)
    if (criticalHeaders != null) add(Cose.HeaderLabel.CRIT)
    if (contentType != null) add(Cose.HeaderLabel.CONTENT_TYPE)
    if (kid != null) add(Cose.HeaderLabel.KID)
    if (iv != null) add(Cose.HeaderLabel.IV)
    if (partialIv != null) add(Cose.HeaderLabel.PARTIAL_IV)
    if (kidContext != null) add(10)
    if (type != null) add(16)
    if (x5chain != null) add(Cose.HeaderLabel.X5_CHAIN)
    if (x5t != null) add(34)
    if (x5u != null) add(35)
}

private val processedCriticalHeaderLabels = setOf(Cose.HeaderLabel.ALG)

private fun validateCoseKeySpec(key: Key, algorithm: Int) {
    val spec = key.spec
    val valid = when (algorithm) {
        Cose.Algorithm.ES256 -> spec == KeySpec.Ec(EcCurve.P256)
        Cose.Algorithm.ES256K -> spec == KeySpec.Ec(EcCurve.SECP256K1)
        Cose.Algorithm.ES384 -> spec == KeySpec.Ec(EcCurve.P384)
        Cose.Algorithm.ES512 -> spec == KeySpec.Ec(EcCurve.P521)
        Cose.Algorithm.EdDSA -> spec == KeySpec.Edwards(EdwardsCurve.ED25519) ||
            spec == KeySpec.Edwards(EdwardsCurve.ED448)
        Cose.Algorithm.PS256,
        Cose.Algorithm.PS384,
        Cose.Algorithm.PS512,
        Cose.Algorithm.RS256,
        Cose.Algorithm.RS384,
        Cose.Algorithm.RS512,
        -> spec is KeySpec.Rsa && spec.bits >= 2048
        else -> false
    }
    require(valid) { "Key specification $spec is incompatible with COSE algorithm $algorithm" }
}

private fun validateCoseSignatureLength(key: Key, algorithm: Int, signature: ByteArray) {
    val expected = when (algorithm) {
        Cose.Algorithm.ES256, Cose.Algorithm.ES256K -> 64
        Cose.Algorithm.ES384 -> 96
        Cose.Algorithm.ES512 -> 132
        Cose.Algorithm.EdDSA -> when (key.spec) {
            KeySpec.Edwards(EdwardsCurve.ED25519) -> 64
            KeySpec.Edwards(EdwardsCurve.ED448) -> 114
            else -> error("Unsupported EdDSA key specification")
        }
        Cose.Algorithm.PS256,
        Cose.Algorithm.PS384,
        Cose.Algorithm.PS512,
        Cose.Algorithm.RS256,
        Cose.Algorithm.RS384,
        Cose.Algorithm.RS512,
        -> ((key.spec as KeySpec.Rsa).bits + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
        else -> throw IllegalArgumentException("Unsupported COSE signature algorithm: $algorithm")
    }
    require(signature.size == expected) { "COSE signature must be $expected bytes for algorithm $algorithm" }
}
