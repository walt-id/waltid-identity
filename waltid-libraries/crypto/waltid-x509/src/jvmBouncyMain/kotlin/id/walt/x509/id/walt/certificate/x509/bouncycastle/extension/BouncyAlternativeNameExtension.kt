package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import id.walt.certificate.x509.model.GeneralName
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toBouncyCastleGeneralNames
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toGeneralNamesList
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension
import org.bouncycastle.asn1.x509.GeneralNames as BouncyCastleGeneralNames


internal abstract class BouncyAlternativeNameExtension(extension: BouncyCastleExtension) :
    BouncyExtension(extension) {

    private val internalSubjectAlternativeNames = BouncyCastleGeneralNames.getInstance(extension.parsedValue)

    val alternativeNames: List<GeneralName>
        get() =
            internalSubjectAlternativeNames.names.toGeneralNamesList()

    companion object {
        fun createExtension(extension: IssuerAlternativeNameExtension): ASN1Object =
            createAlternativeNames(extension.alternativeNames)

        fun createExtension(extension: SubjectAlternativeNameExtension): ASN1Object =
            createAlternativeNames(extension.alternativeNames)


        private fun createAlternativeNames(generalNames: List<GeneralName>): ASN1Object =
            generalNames.toBouncyCastleGeneralNames()
    }
}