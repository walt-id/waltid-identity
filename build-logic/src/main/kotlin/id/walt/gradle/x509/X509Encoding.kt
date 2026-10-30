package id.walt.gradle.x509

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

internal fun pem(certificates: List<X509CertificateHolder>) = certificates.joinToString("") { certificate ->
    val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
    "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
}

/**
 * RFC 7517 JWK of the certificate's key; `kid` is the RFC 7638 thumbprint and `x5c` carries the chain.
 */
internal fun jwk(generated: GeneratedCertificate, includePrivate: Boolean): Map<String, Any> {
    val publicKey = generated.keyPair.public
    val privateKey = generated.keyPair.private
    val publicMembers: Map<String, String> = when (publicKey) {
        is ECPublicKey -> {
            val size = (publicKey.params.curve.field.fieldSize + 7) / 8
            mapOf(
                "kty" to "EC",
                "crv" to generated.keyAlgorithm.id,
                "x" to base64Url(publicKey.w.affineX.unsigned(size)),
                "y" to base64Url(publicKey.w.affineY.unsigned(size)),
            )
        }
        is RSAPublicKey -> mapOf(
            "kty" to "RSA",
            "n" to base64Url(publicKey.modulus.unsigned()),
            "e" to base64Url(publicKey.publicExponent.unsigned()),
        )
        else -> mapOf(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to base64Url(SubjectPublicKeyInfo.getInstance(publicKey.encoded).publicKeyData.bytes),
        )
    }
    val privateMembers: Map<String, String> = if (!includePrivate) emptyMap() else when (privateKey) {
        is ECPrivateKey -> mapOf("d" to base64Url(privateKey.s.unsigned((privateKey.params.curve.field.fieldSize + 7) / 8)))
        is RSAPrivateCrtKey -> mapOf(
            "d" to privateKey.privateExponent,
            "p" to privateKey.primeP,
            "q" to privateKey.primeQ,
            "dp" to privateKey.primeExponentP,
            "dq" to privateKey.primeExponentQ,
            "qi" to privateKey.crtCoefficient,
        ).mapValues { base64Url(it.value.unsigned()) }
        else -> mapOf(
            "d" to base64Url(ASN1OctetString.getInstance(PrivateKeyInfo.getInstance(privateKey.encoded).parsePrivateKey()).octets),
        )
    }
    return publicMembers + privateMembers + mapOf(
        "kid" to thumbprint(publicMembers),
        "alg" to generated.keyAlgorithm.jwsAlgorithm,
        "x5c" to generated.chain.map { Base64.getEncoder().encodeToString(it.encoded) },
    )
}

/** RFC 7638: SHA-256 over the required public members in lexicographic order, without whitespace. */
internal fun thumbprint(publicMembers: Map<String, String>): String {
    val canonical = publicMembers.toSortedMap().entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" }
    return base64Url(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()))
}

internal fun toJson(value: Map<String, Any>): String = buildString {
    append("{\n")
    value.entries.forEachIndexed { index, (key, member) ->
        append("  ").append(jsonString(key)).append(": ")
        when (member) {
            is List<*> -> append(member.joinToString(",\n    ", "[\n    ", "\n  ]") { jsonString(it.toString()) })
            else -> append(jsonString(member.toString()))
        }
        append(if (index < value.size - 1) ",\n" else "\n")
    }
    append("}\n")
}

private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** Big-endian magnitude without sign byte, left-padded to [size] when given (JWK EC coordinates). */
private fun BigInteger.unsigned(size: Int? = null): ByteArray {
    val bytes = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    return if (size == null || bytes.size >= size) bytes else ByteArray(size - bytes.size) + bytes
}
