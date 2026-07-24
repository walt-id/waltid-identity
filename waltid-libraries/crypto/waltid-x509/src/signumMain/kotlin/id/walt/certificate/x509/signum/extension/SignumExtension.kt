package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.Extension

abstract class SignumExtension(val extension: X509CertificateExtension) : Extension {

    override val oid: String = extension.oid.toString()

    override val critical: Boolean = extension.critical

    companion object {
        val X509CertificateExtension.content: Asn1Element
            get() = Asn1Element.parse(value.asOctetString().content)
    }
}