package id.walt.x509.iso

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import at.asitplus.signum.indispensable.toX509SignatureAlgorithm
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.x509.CertificateDer
import id.walt.x509.X509ValidityPeriod
import kotlinx.io.bytestring.ByteString

internal suspend fun buildSignumIsoCertificateDer(
    serialNumber: ByteString,
    issuerName: List<RelativeDistinguishedName>,
    subjectName: List<RelativeDistinguishedName>,
    validityPeriod: X509ValidityPeriod,
    subjectPublicKey: Key,
    signingKey: Key,
    extensions: List<X509CertificateExtension>,
): CertificateDer {
    val subjectCryptoPublicKey = subjectPublicKey.toSignumPublicKey()
    val signatureAlgorithm = signingKey.keyType.toSignumSignatureAlgorithm()
    val x509SignatureAlgorithm = signatureAlgorithm.toX509SignatureAlgorithm().getOrThrow()

    return buildSignumX509CertificateDer(
        serialNumber = serialNumber,
        issuerName = issuerName,
        subjectName = subjectName,
        validityPeriod = validityPeriod,
        subjectPublicKey = subjectCryptoPublicKey,
        signatureAlgorithm = x509SignatureAlgorithm,
        extensions = extensions,
        sign = { signingKey.signSignumX509Raw(it) },
    )
}

internal expect suspend fun Key.signSignumX509Raw(data: ByteArray): CryptoSignature

internal expect suspend fun Key.toSignumPublicKey(): CryptoPublicKey

internal fun KeyType.toSignumSignatureAlgorithm(): SignatureAlgorithm =
    when (this) {
        KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
        KeyType.secp384r1 -> SignatureAlgorithm.ECDSAwithSHA384
        KeyType.secp521r1 -> SignatureAlgorithm.ECDSAwithSHA512
        KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
        KeyType.RSA3072 -> SignatureAlgorithm.RSAwithSHA384andPKCS1Padding
        KeyType.RSA4096 -> SignatureAlgorithm.RSAwithSHA512andPKCS1Padding
        else -> error("Unsupported X.509 signing key type: $this")
    }

internal fun KeyType.toSignumCurve(): ECCurve =
    when (this) {
        KeyType.secp256r1 -> ECCurve.SECP_256_R_1
        KeyType.secp384r1 -> ECCurve.SECP_384_R_1
        KeyType.secp521r1 -> ECCurve.SECP_521_R_1
        else -> error("Unsupported EC key type: $this")
    }
