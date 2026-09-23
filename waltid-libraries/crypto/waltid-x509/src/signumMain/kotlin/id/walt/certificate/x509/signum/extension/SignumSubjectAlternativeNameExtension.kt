package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toAsn1Sequence
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toGeneralNames


internal class SignumSubjectAlternativeNameExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    SubjectAlternativeNameExtension {

    override val alternativeNames: List<GeneralName> = evaluateSignumSubjectAltNames(extension)

    companion object {

        /**
         * Adopted from
         * at.asitplus.signum.indispensable.pki.AlternativeNames
         */
        private fun evaluateSignumSubjectAltNames(extension: X509CertificateExtension): List<GeneralName> {
            val generalNamesList =
                ((extension.value as Asn1EncapsulatingOctetString).children.firstOrNull() as Asn1Sequence?)
            return generalNamesList?.toGeneralNames() ?: emptyList()
        }

        fun createExtension(extension: SubjectAlternativeNameExtension): Asn1EncapsulatingOctetString =
            extension.alternativeNames.toAsn1Sequence()
                .let { Asn1EncapsulatingOctetString(listOf(it)) }
    }
}