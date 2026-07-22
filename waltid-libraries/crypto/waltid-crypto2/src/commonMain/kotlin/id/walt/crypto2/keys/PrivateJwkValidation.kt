package id.walt.crypto2.keys

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal suspend fun EncodedKey.Jwk.validatePrivatePublicConsistency(
    spec: KeySpec,
    provider: CryptographyProvider,
) {
    if (!privateMaterial) return
    val claimed = canonicalPublicJwk()
    val derived = derivePublicJwk(spec, provider)
    require(claimed == derived.canonicalPublicJwk()) {
        "Private JWK public members do not match its private material"
    }
}

private suspend fun EncodedKey.Jwk.derivePublicJwk(
    spec: KeySpec,
    provider: CryptographyProvider,
): EncodedKey.Jwk = when (spec) {
    is KeySpec.Ec -> deriveEcPublicJwk(spec, provider)
    is KeySpec.Edwards -> {
        val jwk = parseJwk()
        val key = provider.get(EdDSA).privateKeyDecoder(spec.curve.toCryptographyCurve())
            .decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, jwk.requiredBase64Url("d"))
        EncodedKey.Jwk(
            BinaryData(normalizeJwk(key.getPublicKey().encodeToByteArray(EdDSA.PublicKey.Format.JWK))),
            privateMaterial = false,
        )
    }
    is KeySpec.Montgomery -> {
        val jwk = parseJwk()
        val key = provider.get(XDH).privateKeyDecoder(spec.curve.toCryptographyCurve())
            .decodeFromByteArray(XDH.PrivateKey.Format.RAW, jwk.requiredBase64Url("d"))
        EncodedKey.Jwk(
            BinaryData(normalizeJwk(key.getPublicKey().encodeToByteArray(XDH.PublicKey.Format.JWK))),
            privateMaterial = false,
        )
    }
    is KeySpec.Rsa -> deriveRsaPublicJwk(provider)
    is KeySpec.Symmetric -> throw IllegalArgumentException("Symmetric JWKs have no public component")
    is KeySpec.Custom -> throw IllegalArgumentException("Custom private JWK validation requires its provider")
}

private suspend fun EncodedKey.Jwk.deriveEcPublicJwk(
    spec: KeySpec.Ec,
    provider: CryptographyProvider,
): EncodedKey.Jwk {
    val curve = EC.Curve(spec.curve.name)
    val privateValue = parseJwk().requiredBase64Url("d")
    provider.getOrNull(ECDSA)?.let { return deriveEcPublicJwk(it, curve, privateValue) }
    provider.getOrNull(ECDH)?.let { return deriveEcPublicJwk(it, curve, privateValue) }
    error("No EC algorithm is available for private JWK validation")
}

private suspend fun <PublicK : EC.PublicKey, PrivateK : EC.PrivateKey<PublicK>, PairK : EC.KeyPair<PublicK, PrivateK>>
    deriveEcPublicJwk(
        algorithm: EC<PublicK, PrivateK, PairK>,
        curve: EC.Curve,
        privateValue: ByteArray,
    ): EncodedKey.Jwk {
    val publicKey = algorithm.privateKeyDecoder(curve)
        .decodeFromByteArray(EC.PrivateKey.Format.RAW, privateValue)
        .getPublicKey()
    return EncodedKey.Jwk(
        BinaryData(normalizeJwk(publicKey.encodeToByteArray(EC.PublicKey.Format.JWK))),
        privateMaterial = false,
    )
}

private suspend fun EncodedKey.Jwk.deriveRsaPublicJwk(provider: CryptographyProvider): EncodedKey.Jwk {
    val jwk = parseJwk()
    require("oth" !in jwk) { "Multi-prime RSA private JWK validation is not supported" }
    val n = jwk.requiredBase64UrlUInt("n")
    val e = jwk.requiredBase64UrlUInt("e")
    val d = jwk.requiredBase64UrlUInt("d")
    val p = jwk.requiredBase64UrlUInt("p")
    val q = jwk.requiredBase64UrlUInt("q")
    val dp = jwk.requiredBase64UrlUInt("dp")
    val dq = jwk.requiredBase64UrlUInt("dq")
    val qi = jwk.requiredBase64UrlUInt("qi")
    val derivedN = validateRsaPrivateValues(n, e, d, p, q, dp, dq, qi)
    val sanitized = JsonObject(jwk + ("n" to JsonPrimitive(derivedN.encodeBase64Url())))
    val encoded = normalizeJwk(Json.encodeToString(sanitized).encodeToByteArray())

    provider.getOrNull(RSA.PKCS1)?.let { return deriveRsaPublicJwk(it, encoded) }
    provider.getOrNull(RSA.PSS)?.let { return deriveRsaPublicJwk(it, encoded) }
    provider.getOrNull(RSA.OAEP)?.let { return deriveRsaPublicJwk(it, encoded) }
    error("No RSA algorithm is available for private JWK validation")
}

private suspend fun <PublicK : RSA.PublicKey, PrivateK : RSA.PrivateKey<PublicK>, PairK : RSA.KeyPair<PublicK, PrivateK>>
    deriveRsaPublicJwk(
        algorithm: RSA<PublicK, PrivateK, PairK>,
        encoded: ByteArray,
    ): EncodedKey.Jwk {
    val publicKey = algorithm.privateKeyDecoder(SHA256)
        .decodeFromByteArray(RSA.PrivateKey.Format.JWK, encoded)
        .getPublicKey()
    return EncodedKey.Jwk(
        BinaryData(normalizeJwk(publicKey.encodeToByteArray(RSA.PublicKey.Format.JWK))),
        privateMaterial = false,
    )
}

private fun EncodedKey.Jwk.canonicalPublicJwk(): String {
    val jwk = parseJwk()
    val names = when (jwk.requiredString("kty")) {
        "EC" -> listOf("crv", "kty", "x", "y")
        "OKP" -> listOf("crv", "kty", "x")
        "RSA" -> listOf("e", "kty", "n")
        else -> throw IllegalArgumentException("JWK key type has no public component")
    }
    return names.joinToString(separator = ",", prefix = "{", postfix = "}") { name ->
        "\"$name\":\"${jwk.requiredString(name)}\""
    }
}

private fun validateRsaPrivateValues(
    claimedN: ByteArray,
    e: ByteArray,
    d: ByteArray,
    p: ByteArray,
    q: ByteArray,
    dp: ByteArray,
    dq: ByteArray,
    qi: ByteArray,
): ByteArray {
    require(p.compareUnsigned(ONE) > 0 && q.compareUnsigned(ONE) > 0) { "RSA primes must be greater than one" }
    val pMinusOne = p.subtractOne()
    val qMinusOne = q.subtractOne()
    require(d.modulo(pMinusOne).contentEquals(dp.normalized())) { "RSA dp does not match d and p" }
    require(d.modulo(qMinusOne).contentEquals(dq.normalized())) { "RSA dq does not match d and q" }
    require(qi.multiply(q).modulo(p).contentEquals(ONE)) { "RSA qi does not match p and q" }
    require(d.multiply(e).modulo(pMinusOne).contentEquals(ONE)) { "RSA e is not the inverse of d modulo p-1" }
    require(d.multiply(e).modulo(qMinusOne).contentEquals(ONE)) { "RSA e is not the inverse of d modulo q-1" }
    return p.multiply(q).also {
        require(it.contentEquals(claimedN.normalized())) { "RSA n does not match p and q" }
    }
}

private fun ByteArray.multiply(other: ByteArray): ByteArray {
    val left = normalized()
    val right = other.normalized()
    val result = IntArray(left.size + right.size)
    for (leftIndex in left.indices.reversed()) {
        var carry = 0
        for (rightIndex in right.indices.reversed()) {
            val resultIndex = leftIndex + rightIndex + 1
            val product = (left[leftIndex].toInt() and 0xff) * (right[rightIndex].toInt() and 0xff) +
                result[resultIndex] + carry
            result[resultIndex] = product and 0xff
            carry = product ushr Byte.SIZE_BITS
        }
        result[leftIndex] += carry
    }
    return ByteArray(result.size) { result[it].toByte() }.normalized()
}

private fun ByteArray.modulo(modulus: ByteArray): ByteArray {
    val normalizedModulus = modulus.normalized()
    require(!normalizedModulus.contentEquals(ZERO)) { "RSA modulus cannot be zero" }
    var remainder = ZERO
    normalized().forEach { byte ->
        for (bit in 7 downTo 0) {
            remainder = remainder.shiftLeft((byte.toInt() ushr bit) and 1)
            if (remainder.compareUnsigned(normalizedModulus) >= 0) {
                remainder = remainder.subtract(normalizedModulus)
            }
        }
    }
    return remainder.normalized()
}

private fun ByteArray.shiftLeft(bit: Int): ByteArray {
    val result = ByteArray(size + 1)
    var carry = bit
    for (index in indices.reversed()) {
        val shifted = (this[index].toInt() and 0xff) * 2 + carry
        result[index + 1] = shifted.toByte()
        carry = shifted ushr Byte.SIZE_BITS
    }
    result[0] = carry.toByte()
    return result.normalized()
}

private fun ByteArray.subtractOne(): ByteArray {
    require(compareUnsigned(ONE) > 0) { "Value must be greater than one" }
    return copyOf().also { result ->
        var index = result.lastIndex
        while (result[index] == 0.toByte()) {
            result[index--] = 0xff.toByte()
        }
        result[index] = (result[index].toInt() - 1).toByte()
    }.normalized()
}

private fun ByteArray.subtract(other: ByteArray): ByteArray {
    require(compareUnsigned(other) >= 0) { "Unsigned subtraction underflow" }
    val result = copyOf()
    var leftIndex = result.lastIndex
    var rightIndex = other.lastIndex
    var borrow = 0
    while (leftIndex >= 0) {
        val difference = (result[leftIndex].toInt() and 0xff) -
            (if (rightIndex >= 0) other[rightIndex].toInt() and 0xff else 0) - borrow
        if (difference < 0) {
            result[leftIndex] = (difference + 256).toByte()
            borrow = 1
        } else {
            result[leftIndex] = difference.toByte()
            borrow = 0
        }
        leftIndex--
        rightIndex--
    }
    return result.normalized()
}

private fun ByteArray.compareUnsigned(other: ByteArray): Int {
    val left = normalized()
    val right = other.normalized()
    if (left.size != right.size) return left.size.compareTo(right.size)
    left.indices.forEach { index ->
        val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return 0
}

private fun ByteArray.normalized(): ByteArray {
    val firstNonZero = indexOfFirst { it != 0.toByte() }
    return when (firstNonZero) {
        -1 -> ZERO
        0 -> this
        else -> copyOfRange(firstNonZero, size)
    }
}

private val ZERO = byteArrayOf(0)
private val ONE = byteArrayOf(1)
