package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1ExplicitlyTagged
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.encoding.Asn1.ExplicitlyTagged
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import id.walt.certificate.x509.model.GeneralName


internal class SignumSubjectAlternativeNameExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    SubjectAlternativeNameExtension {

    override val alternativeNames: List<GeneralName> = evaluateSignumSubjectAltNames(extension)

    companion object {

        /**
         * Adopted from
         * at.asitplus.signum.indispensable.pki.AlternativeNames
         */
        fun evaluateSignumSubjectAltNames(extension: X509CertificateExtension): List<GeneralName> {
            val generalNamesList =
                ((extension.value as Asn1EncapsulatingOctetString).children.firstOrNull() as Asn1Sequence?)
            val mapping = generalNamesList?.map {
                when (it.tag) {
                    SubjectAltNameImplicitTags.dNSName -> GeneralName(
                        GeneralName.NameType.dNSName,
                        it.asPrimitive().content.decodeToString()
                    )

                    SubjectAltNameImplicitTags.rfc822Name -> GeneralName(
                        GeneralName.NameType.rfc822Name,
                        it.asPrimitive().content.decodeToString()
                    )

                    SubjectAltNameImplicitTags.uniformResourceIdentifier -> GeneralName(
                        GeneralName.NameType.uniformResourceIdentifier,
                        it.asPrimitive().content.decodeToString()
                    )

                    else -> GeneralName(
                        GeneralName.NameType.otherName,
                        "Alternative Name Type '${it.tag.tagValue}' ${it.tag.name?.let { "('${it}') " }}is not implemented"
                    )
                }
            }
            return mapping ?: emptyList()
        }

        fun createExtension(extension: SubjectAlternativeNameExtension): Asn1PrimitiveOctetString {
            return Asn1.Sequence {
                extension.alternativeNames.map {
                    when (it.type) {
                        GeneralName.NameType.dNSName -> {
                           Asn1String.IA5(it.value).encodeToTlv().withImplicitTag(SubjectAltNameImplicitTags.dNSName)
                        }
                        else -> throw IllegalArgumentException("Unsupported GeneralName type ${it.type}")
                    }
                }.forEach {
                    +it
                }
            }.let { Asn1PrimitiveOctetString(it.derEncoded) }
        }
    }
}