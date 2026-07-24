package id.walt.certificate.x509.signum

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.signum.dn.toDistinguishedName
import id.walt.certificate.x509.signum.extension.SignumExtensionFactory
import kotlinx.io.bytestring.ByteString
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

class SignumX509Certificate(
    private val certificate: SignumCertificate
) : X509Certificate {

    override val data: X509Certificate.CertificateData = CertData()

    override val signatureAlgorithmOid: String = certificate.signatureAlgorithm.oid.toString()

    override val signatureValueRaw: ByteString = ByteString(certificate.rawSignature.content)

    override val encodedDer: ByteString
        get() = ByteString(certificate.encodeToDer())

    inner class CertData : X509Certificate.CertificateData {
        override val version: Int = certificate.tbsCertificate.version ?: error("certificate version is null")

        override val serialNumberRaw: ByteString = ByteString(certificate.tbsCertificate.serialNumber)

        override val issuerDn: String = certificate.tbsCertificate.issuerName.toDistinguishedName().toString()

        override val validity: X509Certificate.Validity
            get() = X509Certificate.Validity(
                notBefore = certificate.tbsCertificate.validFrom.instant,
                notAfter = certificate.tbsCertificate.validUntil.instant
            )

        override val subjectDn: String
            get() {
                val subject = certificate.tbsCertificate.subjectName
                return subject.toDistinguishedName().toString()
            }

        override val subjectPublicKeyInfo: X509Certificate.SubjectPublicKeyInfo
            get() = SignumPublicKeyInfo.ofCryptoPublicKey(
                certificate.tbsCertificate.decodedPublicKey.getOrThrow()
            )

        override val extensions: Map<String, Extension>
            get() =
                certificate.tbsCertificate.extensions?.map {
                    SignumExtensionFactory.parseExtension(it)
                }?.associateBy { it.oid } ?: emptyMap()
    }
}