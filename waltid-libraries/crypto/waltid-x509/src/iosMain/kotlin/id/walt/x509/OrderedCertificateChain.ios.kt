package id.walt.x509

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.TagClass
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.requireSupported
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import kotlin.time.Instant

internal actual class PlatformX509Certificate private constructor(
    private val certificate: X509Certificate,
) {
    actual val subjectKeyIdentifier: ByteArray?
        get() = certificate.subjectKeyIdentifier()

    actual val authorityKeyIdentifier: ByteArray?
        get() = certificate.authorityKeyIdentifier()

    actual val subjectAlternativeDnsNames: List<String>
        get() = certificate.tbsCertificate.subjectAlternativeNames?.dnsNames.orEmpty()

    actual fun hasIssuerNameMatching(issuer: PlatformX509Certificate): Boolean =
        certificate.tbsCertificate.issuerName == issuer.certificate.tbsCertificate.subjectName

    actual fun verifySignedBy(issuer: PlatformX509Certificate) {
        val signatureAlgorithm = certificate.signatureAlgorithm
        signatureAlgorithm.requireSupported()
        val verifier = signatureAlgorithm.algorithm
            .verifierFor(issuer.certificate.decodedPublicKey.getOrThrow())
            .getOrThrow()

        verifier.verify(
            data = SignatureInput(certificate.rawTbsCertificate.derEncoded),
            sig = certificate.decodedSignature.getOrThrow(),
        ).getOrThrow()
    }

    actual fun isSelfSigned(): Boolean =
        certificate.tbsCertificate.issuerName == certificate.tbsCertificate.subjectName &&
                runCatching { verifySignedBy(this) }.isSuccess

    actual fun checkValidityAt(instant: Instant) {
        val validFrom = certificate.tbsCertificate.validFrom.instant
        val validUntil = certificate.tbsCertificate.validUntil.instant
        if (instant !in validFrom..validUntil) {
            throw IllegalArgumentException("certificate validity is $validFrom to $validUntil")
        }
    }

    actual companion object {
        actual fun parse(der: CertificateDer): PlatformX509Certificate {
            val certificate = X509Certificate.decodeFromByteArray(der.bytes.toByteArray())
                ?: throw IllegalArgumentException("Invalid X.509 DER certificate")
            return PlatformX509Certificate(certificate)
        }
    }
}

private val subjectKeyIdentifierOid = ObjectIdentifier("2.5.29.14")
private val authorityKeyIdentifierOid = ObjectIdentifier("2.5.29.35")

private fun X509Certificate.subjectKeyIdentifier(): ByteArray? =
    extensionValue(subjectKeyIdentifierOid)
        ?.let { Asn1Element.parse(it).asOctetString().content }

private fun X509Certificate.authorityKeyIdentifier(): ByteArray? =
    extensionValue(authorityKeyIdentifierOid)
        ?.let { Asn1Element.parse(it).asSequence() }
        ?.children
        ?.filterIsInstance<Asn1Primitive>()
        ?.firstOrNull { it.tag.tagClass == TagClass.CONTEXT_SPECIFIC && it.tag.tagValue == 0uL }
        ?.content

private fun X509Certificate.extensionValue(oid: ObjectIdentifier): ByteArray? =
    tbsCertificate.extensions
        ?.firstOrNull { it.oid == oid }
        ?.value
        ?.asOctetString()
        ?.content
