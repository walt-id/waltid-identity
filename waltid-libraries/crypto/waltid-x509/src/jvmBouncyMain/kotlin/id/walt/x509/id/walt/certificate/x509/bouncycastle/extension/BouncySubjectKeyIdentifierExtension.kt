package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension


class BouncySubjectKeyIdentifierExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    SubjectKeyIdentifierExtension {

    override val keyIdentifier: ByteString
        get() =
            ByteString(SubjectKeyIdentifier.getInstance(extension.parsedValue).keyIdentifier)

    companion object {
        fun createExtension(extension: SubjectKeyIdentifierExtension, subjectPublicKey: PublicKeyInfo): ASN1Object {
            return SubjectKeyIdentifier(subjectPublicKey.keyId.toByteArray())
        }
    }
}