package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import kotlinx.io.bytestring.ByteString

class SignumSubjectKeyIdentifierExtension(extension: X509CertificateExtension) : SignumExtension(extension),
    SubjectKeyIdentifierExtension {

    override val keyIdentifier: ByteString
        get() =
            ByteString(extension.content.asOctetString().content)

    companion object {
        fun createExtension(
            extension: SubjectKeyIdentifierExtension,
            subjectPublicKey: PublicKeyInfo
        ): Asn1PrimitiveOctetString {
            val keyId = Asn1PrimitiveOctetString(subjectPublicKey.keyId.toByteArray())
            return Asn1PrimitiveOctetString(keyId.derEncoded)
        }
    }
}