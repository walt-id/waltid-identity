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
import kotlinx.serialization.json.jsonObject

suspend fun EncodedKey.toPublicJwk(
    spec: KeySpec,
    provider: CryptographyProvider = CryptographyProvider.Default,
): EncodedKey.Jwk {
    if (this is EncodedKey.Jwk) return publicOnly()
    val encoded = when (spec) {
        is KeySpec.Ec -> convertEcPublic(provider, EC.Curve(spec.curve.name), EC.PublicKey.Format.JWK)
        is KeySpec.Edwards -> decodeEdPublic(provider, spec).encodeToByteArray(EdDSA.PublicKey.Format.JWK)
        is KeySpec.Montgomery -> decodeXdhPublic(provider, spec).encodeToByteArray(XDH.PublicKey.Format.JWK)
        is KeySpec.Rsa -> convertRsaPublic(provider, RSA.PublicKey.Format.JWK)
        is KeySpec.Symmetric -> throw IllegalArgumentException("Symmetric keys have no public JWK")
        is KeySpec.Custom -> throw IllegalArgumentException("Custom key conversion requires its provider")
    }
    return EncodedKey.Jwk(BinaryData(normalizeJwk(encoded)), privateMaterial = false)
}

suspend fun EncodedKey.toPrivateJwk(
    spec: KeySpec,
    provider: CryptographyProvider = CryptographyProvider.Default,
): EncodedKey.Jwk {
    if (this is EncodedKey.Jwk) {
        require(privateMaterial) { "Public JWK cannot be converted to a private key" }
        return this
    }
    require(this is EncodedKey.Pkcs8Der) { "Public SPKI material cannot be converted to a private key" }
    val encoded = when (spec) {
        is KeySpec.Ec -> convertEcPrivate(provider, EC.Curve(spec.curve.name), EC.PrivateKey.Format.JWK)
        is KeySpec.Edwards -> decodeEdPrivate(provider, spec).encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
        is KeySpec.Montgomery -> decodeXdhPrivate(provider, spec).encodeToByteArray(XDH.PrivateKey.Format.JWK)
        is KeySpec.Rsa -> convertRsaPrivate(provider, RSA.PrivateKey.Format.JWK)
        is KeySpec.Symmetric -> throw IllegalArgumentException("Symmetric keys do not use PKCS8")
        is KeySpec.Custom -> throw IllegalArgumentException("Custom key conversion requires its provider")
    }
    return EncodedKey.Jwk(BinaryData(normalizeJwk(encoded)), privateMaterial = true)
}

suspend fun EncodedKey.toSpkiDer(
    spec: KeySpec,
    provider: CryptographyProvider = CryptographyProvider.Default,
): EncodedKey.SpkiDer {
    if (this is EncodedKey.SpkiDer) return this
    val encoded = when (spec) {
        is KeySpec.Ec -> convertEcPublic(provider, EC.Curve(spec.curve.name), EC.PublicKey.Format.DER)
        is KeySpec.Edwards -> decodeEdPublic(provider, spec).encodeToByteArray(EdDSA.PublicKey.Format.DER)
        is KeySpec.Montgomery -> decodeXdhPublic(provider, spec).encodeToByteArray(XDH.PublicKey.Format.DER)
        is KeySpec.Rsa -> convertRsaPublic(provider, RSA.PublicKey.Format.DER)
        is KeySpec.Symmetric -> throw IllegalArgumentException("Symmetric keys have no SPKI encoding")
        is KeySpec.Custom -> throw IllegalArgumentException("Custom key conversion requires its provider")
    }
    return EncodedKey.SpkiDer(BinaryData(encoded))
}

suspend fun EncodedKey.toPkcs8Der(
    spec: KeySpec,
    provider: CryptographyProvider = CryptographyProvider.Default,
): EncodedKey.Pkcs8Der {
    if (this is EncodedKey.Pkcs8Der) return this
    require(this is EncodedKey.Jwk && privateMaterial) { "Private key material is required for PKCS8 export" }
    val encoded = when (spec) {
        is KeySpec.Ec -> convertEcPrivate(provider, EC.Curve(spec.curve.name), EC.PrivateKey.Format.DER)
        is KeySpec.Edwards -> decodeEdPrivate(provider, spec).encodeToByteArray(EdDSA.PrivateKey.Format.DER)
        is KeySpec.Montgomery -> decodeXdhPrivate(provider, spec).encodeToByteArray(XDH.PrivateKey.Format.DER)
        is KeySpec.Rsa -> convertRsaPrivate(provider, RSA.PrivateKey.Format.DER)
        is KeySpec.Symmetric -> throw IllegalArgumentException("Symmetric keys do not use PKCS8")
        is KeySpec.Custom -> throw IllegalArgumentException("Custom key conversion requires its provider")
    }
    return EncodedKey.Pkcs8Der(BinaryData(encoded))
}

fun EncodedKey.Jwk.publicOnly(): EncodedKey.Jwk {
    val privateNames = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")
    val public = Json.parseToJsonElement(data.toByteArray().decodeToString()).jsonObject - privateNames
    return EncodedKey.Jwk(
        data = BinaryData(Json.encodeToString(JsonObject(public)).encodeToByteArray()),
        privateMaterial = false,
    )
}

internal fun EdwardsCurve.toCryptographyCurve(): EdDSA.Curve = when (this) {
    EdwardsCurve.ED25519 -> EdDSA.Curve.Ed25519
    EdwardsCurve.ED448 -> EdDSA.Curve.Ed448
    else -> throw IllegalArgumentException("Unsupported EdDSA curve: $name")
}

internal fun MontgomeryCurve.toCryptographyCurve(): XDH.Curve = when (this) {
    MontgomeryCurve.X25519 -> XDH.Curve.X25519
    MontgomeryCurve.X448 -> XDH.Curve.X448
    else -> throw IllegalArgumentException("Unsupported XDH curve: $name")
}

internal fun normalizeJwk(encoded: ByteArray): ByteArray {
    val json = Json.parseToJsonElement(encoded.decodeToString()).jsonObject.toMutableMap().apply {
        remove("alg")
        remove("use")
        remove("key_ops")
    }
    return Json.encodeToString(JsonObject(json)).encodeToByteArray()
}

private suspend fun EncodedKey.decodeEdPublic(
    provider: CryptographyProvider,
    spec: KeySpec.Edwards,
): EdDSA.PublicKey {
    val algorithm = provider.get(EdDSA)
    val curve = spec.curve.toCryptographyCurve()
    return when (this) {
        is EncodedKey.Jwk -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, publicOnly().data.toByteArray())
        is EncodedKey.SpkiDer -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(EdDSA.PublicKey.Format.DER, data.toByteArray())
        is EncodedKey.Pkcs8Der -> algorithm.privateKeyDecoder(curve)
            .decodeFromByteArray(EdDSA.PrivateKey.Format.DER, data.toByteArray()).getPublicKey()
    }
}

private suspend fun EncodedKey.decodeEdPrivate(
    provider: CryptographyProvider,
    spec: KeySpec.Edwards,
): EdDSA.PrivateKey {
    val decoder = provider.get(EdDSA).privateKeyDecoder(spec.curve.toCryptographyCurve())
    return when (this) {
        is EncodedKey.Jwk -> {
            require(privateMaterial) { "Private JWK material is required" }
            decoder.decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, data.toByteArray())
        }
        is EncodedKey.Pkcs8Der -> decoder.decodeFromByteArray(EdDSA.PrivateKey.Format.DER, data.toByteArray())
        is EncodedKey.SpkiDer -> throw IllegalArgumentException("Public SPKI material cannot be used as a private key")
    }
}

private suspend fun EncodedKey.decodeXdhPublic(
    provider: CryptographyProvider,
    spec: KeySpec.Montgomery,
): XDH.PublicKey {
    val algorithm = provider.get(XDH)
    val curve = spec.curve.toCryptographyCurve()
    return when (this) {
        is EncodedKey.Jwk -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(XDH.PublicKey.Format.JWK, publicOnly().data.toByteArray())
        is EncodedKey.SpkiDer -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(XDH.PublicKey.Format.DER, data.toByteArray())
        is EncodedKey.Pkcs8Der -> algorithm.privateKeyDecoder(curve)
            .decodeFromByteArray(XDH.PrivateKey.Format.DER, data.toByteArray()).getPublicKey()
    }
}

private suspend fun EncodedKey.decodeXdhPrivate(
    provider: CryptographyProvider,
    spec: KeySpec.Montgomery,
): XDH.PrivateKey {
    val decoder = provider.get(XDH).privateKeyDecoder(spec.curve.toCryptographyCurve())
    return when (this) {
        is EncodedKey.Jwk -> {
            require(privateMaterial) { "Private JWK material is required" }
            decoder.decodeFromByteArray(XDH.PrivateKey.Format.JWK, data.toByteArray())
        }
        is EncodedKey.Pkcs8Der -> decoder.decodeFromByteArray(XDH.PrivateKey.Format.DER, data.toByteArray())
        is EncodedKey.SpkiDer -> throw IllegalArgumentException("Public SPKI material cannot be used as a private key")
    }
}

private suspend fun EncodedKey.convertEcPublic(
    provider: CryptographyProvider,
    curve: EC.Curve,
    output: EC.PublicKey.Format,
): ByteArray {
    provider.getOrNull(ECDSA)?.let { return convertEcPublic(it, curve, output) }
    provider.getOrNull(ECDH)?.let { return convertEcPublic(it, curve, output) }
    error("No EC algorithm is available for key conversion")
}

private suspend fun EncodedKey.convertEcPrivate(
    provider: CryptographyProvider,
    curve: EC.Curve,
    output: EC.PrivateKey.Format,
): ByteArray {
    provider.getOrNull(ECDSA)?.let { return convertEcPrivate(it, curve, output) }
    provider.getOrNull(ECDH)?.let { return convertEcPrivate(it, curve, output) }
    error("No EC algorithm is available for key conversion")
}

private suspend fun <PublicK : EC.PublicKey, PrivateK : EC.PrivateKey<PublicK>, PairK : EC.KeyPair<PublicK, PrivateK>>
    EncodedKey.convertEcPublic(
        algorithm: EC<PublicK, PrivateK, PairK>,
        curve: EC.Curve,
        output: EC.PublicKey.Format,
    ): ByteArray {
    val key = when (this) {
        is EncodedKey.Jwk -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(EC.PublicKey.Format.JWK, publicOnly().data.toByteArray())
        is EncodedKey.SpkiDer -> algorithm.publicKeyDecoder(curve)
            .decodeFromByteArray(EC.PublicKey.Format.DER, data.toByteArray())
        is EncodedKey.Pkcs8Der -> algorithm.privateKeyDecoder(curve)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, data.toByteArray()).getPublicKey()
    }
    return key.encodeToByteArray(output)
}

private suspend fun <PublicK : EC.PublicKey, PrivateK : EC.PrivateKey<PublicK>, PairK : EC.KeyPair<PublicK, PrivateK>>
    EncodedKey.convertEcPrivate(
        algorithm: EC<PublicK, PrivateK, PairK>,
        curve: EC.Curve,
        output: EC.PrivateKey.Format,
    ): ByteArray {
    val decoder = algorithm.privateKeyDecoder(curve)
    val key = when (this) {
        is EncodedKey.Jwk -> {
            require(privateMaterial) { "Private JWK material is required" }
            decoder.decodeFromByteArray(EC.PrivateKey.Format.JWK, data.toByteArray())
        }
        is EncodedKey.Pkcs8Der -> decoder.decodeFromByteArray(EC.PrivateKey.Format.DER, data.toByteArray())
        is EncodedKey.SpkiDer -> throw IllegalArgumentException("Public SPKI material cannot be used as a private key")
    }
    return key.encodeToByteArray(output)
}

private suspend fun EncodedKey.convertRsaPublic(
    provider: CryptographyProvider,
    output: RSA.PublicKey.Format,
): ByteArray {
    provider.getOrNull(RSA.PKCS1)?.let { return convertRsaPublic(it, output) }
    provider.getOrNull(RSA.PSS)?.let { return convertRsaPublic(it, output) }
    provider.getOrNull(RSA.OAEP)?.let { return convertRsaPublic(it, output) }
    error("No RSA algorithm is available for key conversion")
}

private suspend fun EncodedKey.convertRsaPrivate(
    provider: CryptographyProvider,
    output: RSA.PrivateKey.Format,
): ByteArray {
    provider.getOrNull(RSA.PKCS1)?.let { return convertRsaPrivate(it, output) }
    provider.getOrNull(RSA.PSS)?.let { return convertRsaPrivate(it, output) }
    provider.getOrNull(RSA.OAEP)?.let { return convertRsaPrivate(it, output) }
    error("No RSA algorithm is available for key conversion")
}

private suspend fun <PublicK : RSA.PublicKey, PrivateK : RSA.PrivateKey<PublicK>, PairK : RSA.KeyPair<PublicK, PrivateK>>
    EncodedKey.convertRsaPublic(
        algorithm: RSA<PublicK, PrivateK, PairK>,
        output: RSA.PublicKey.Format,
    ): ByteArray {
    val key = when (this) {
        is EncodedKey.Jwk -> algorithm.publicKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PublicKey.Format.JWK, publicOnly().data.toByteArray())
        is EncodedKey.SpkiDer -> algorithm.publicKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PublicKey.Format.DER, data.toByteArray())
        is EncodedKey.Pkcs8Der -> algorithm.privateKeyDecoder(SHA256)
            .decodeFromByteArray(RSA.PrivateKey.Format.DER, data.toByteArray()).getPublicKey()
    }
    return key.encodeToByteArray(output)
}

private suspend fun <PublicK : RSA.PublicKey, PrivateK : RSA.PrivateKey<PublicK>, PairK : RSA.KeyPair<PublicK, PrivateK>>
    EncodedKey.convertRsaPrivate(
        algorithm: RSA<PublicK, PrivateK, PairK>,
        output: RSA.PrivateKey.Format,
    ): ByteArray {
    val decoder = algorithm.privateKeyDecoder(SHA256)
    val key = when (this) {
        is EncodedKey.Jwk -> {
            require(privateMaterial) { "Private JWK material is required" }
            decoder.decodeFromByteArray(RSA.PrivateKey.Format.JWK, data.toByteArray())
        }
        is EncodedKey.Pkcs8Der -> decoder.decodeFromByteArray(RSA.PrivateKey.Format.DER, data.toByteArray())
        is EncodedKey.SpkiDer -> throw IllegalArgumentException("Public SPKI material cannot be used as a private key")
    }
    return key.encodeToByteArray(output)
}
