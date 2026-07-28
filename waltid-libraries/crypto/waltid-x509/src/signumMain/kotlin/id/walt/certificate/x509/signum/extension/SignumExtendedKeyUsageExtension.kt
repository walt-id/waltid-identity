package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.getKeyUsageById

/**
 * ExtKeyUsageSyntax ::= SEQUENCE SIZE (1..MAX) OF KeyPurposeId
 * KeyPurposeId ::= OBJECT IDENTIFIER
 */
class SignumExtendedKeyUsageExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    ExtendedKeyUsageExtension {

    override val keyPurposeIdList: Set<ExtendedKeyUsageExtension.KeyUsage>
        get() =
            extension.content.asSequence().children.map {
                getKeyUsageById(ObjectIdentifier.decodeFromTlv(it.asPrimitive()).toString())
            }.toSet()

    companion object {
        fun createExtension(ext: ExtendedKeyUsageExtension): Asn1EncapsulatingOctetString =
            Asn1.OctetStringEncapsulating {
                +Asn1.Sequence {
                    ext.keyPurposeIdList.forEach { +ObjectIdentifier(it.id) }
                }
            }
    }
}