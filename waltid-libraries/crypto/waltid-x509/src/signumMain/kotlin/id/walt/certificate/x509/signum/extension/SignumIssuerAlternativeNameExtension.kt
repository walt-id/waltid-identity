package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toAsn1Sequence
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toGeneralNames

/**
 * IssuerAltName ::= GeneralNames
 */
class SignumIssuerAlternativeNameExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    IssuerAlternativeNameExtension {

    override val alternativeNames: List<GeneralName> =
        extension.content.asSequence().toGeneralNames()

    companion object {
        fun createExtension(extension: IssuerAlternativeNameExtension): Asn1EncapsulatingOctetString =
            extension.alternativeNames.toAsn1Sequence()
                .let { Asn1EncapsulatingOctetString(listOf(it)) }
    }
}