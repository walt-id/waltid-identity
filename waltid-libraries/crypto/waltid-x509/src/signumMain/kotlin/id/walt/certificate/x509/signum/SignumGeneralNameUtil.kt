package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.SubjectAltNameImplicitTags
import id.walt.certificate.x509.IpAddressUtil
import id.walt.certificate.x509.model.GeneralName

object SignumGeneralNameUtil {

    fun Asn1Sequence.toGeneralNames(): List<GeneralName> =
        children.toGeneralNames()

    fun Asn1Structure.toGeneralNames(): List<GeneralName> =
        children.toGeneralNames()

    fun List<Asn1Element>.toGeneralNames(): List<GeneralName> =
        map {
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

                SubjectAltNameImplicitTags.iPAddress -> GeneralName(
                    GeneralName.NameType.IPAddress,
                    IpAddressUtil.byteArrayToIpAddress(it.asPrimitive().content),
                )

                else -> GeneralName(
                    GeneralName.NameType.otherName,
                    "Alternative Name Type '${it.tag.tagValue}' ${it.tag.name?.let { "('${it}') " }}is not implemented"
                )
            }
        }

    fun Collection<GeneralName>.toAsn1Sequence() = Asn1.Sequence {
        this@toAsn1Sequence.map {
            it.toAsn1Element()
        }.forEach {
            +it
        }
    }

    fun GeneralName.toAsn1Element(): Asn1Element =
        when (type) {
            GeneralName.NameType.dNSName -> {
                Asn1String.IA5(value).encodeToTlv().withImplicitTag(SubjectAltNameImplicitTags.dNSName)
            }

            GeneralName.NameType.rfc822Name -> {
                Asn1String.IA5(value).encodeToTlv().withImplicitTag(SubjectAltNameImplicitTags.rfc822Name)
            }

            GeneralName.NameType.uniformResourceIdentifier -> {
                Asn1String.IA5(value).encodeToTlv()
                    .withImplicitTag(SubjectAltNameImplicitTags.uniformResourceIdentifier)
            }

            GeneralName.NameType.IPAddress -> {
                Asn1OctetString(IpAddressUtil.parseString(value))
                    .withImplicitTag(SubjectAltNameImplicitTags.iPAddress)
            }

            else -> throw IllegalArgumentException("Unsupported GeneralName type ${type}")
        }
}