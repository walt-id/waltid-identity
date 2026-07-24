package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1BitString
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.MessageDigest
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension


class BouncySubjectKeyIdentifierExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    SubjectKeyIdentifierExtension {

    override val keyIdentifier: ByteString
        get() =
            ByteString(SubjectKeyIdentifier.getInstance(extension.parsedValue).keyIdentifier)

    companion object {
        fun createExtension(extension: SubjectKeyIdentifierExtension, subjectPublicKey: ASN1BitString): ASN1Object {
            val md = MessageDigest.getInstance("SHA-1")
            // Calculate message digest and return it as a byte array
            val hashBytes = md.digest(subjectPublicKey.bytes)
            return SubjectKeyIdentifier(hashBytes)
        }
    }
}