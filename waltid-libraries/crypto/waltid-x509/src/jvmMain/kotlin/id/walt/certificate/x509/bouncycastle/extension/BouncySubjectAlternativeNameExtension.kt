package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import java.net.InetAddress
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension


internal class BouncySubjectAlternativeNameExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    SubjectAlternativeNameExtension {

    private val internalSubjectAlternativeNames = GeneralNames.getInstance(extension.parsedValue)

    override val alternativeNames: List<SubjectAlternativeNameExtension.AlternativeName>
        get() {
            return internalSubjectAlternativeNames.names.map {
                when (it.tagNo) {
                    GeneralName.dNSName -> SubjectAlternativeNameExtension.AlternativeName(
                        SubjectAlternativeNameExtension.NameType.dNSName,
                        it.name.toString()
                    )

                    GeneralName.rfc822Name -> SubjectAlternativeNameExtension.AlternativeName(
                        SubjectAlternativeNameExtension.NameType.rfc822Name,
                        it.name.toString()
                    )

                    GeneralName.uniformResourceIdentifier -> SubjectAlternativeNameExtension.AlternativeName(
                        SubjectAlternativeNameExtension.NameType.uniformResourceIdentifier,
                        it.name.toString()
                    )

                    GeneralName.iPAddress -> {
                        val ipBytes: ByteArray? = (it.name as ASN1OctetString).octets
                        val address = InetAddress.getByAddress(ipBytes)
                        SubjectAlternativeNameExtension.AlternativeName(
                            SubjectAlternativeNameExtension.NameType.IPAddress,
                            address.hostAddress
                        )
                    }

                    GeneralName.registeredID -> {
                        SubjectAlternativeNameExtension.AlternativeName(
                            SubjectAlternativeNameExtension.NameType.registeredID,
                            (it.name as ASN1ObjectIdentifier).id
                        )
                    }

                    else -> SubjectAlternativeNameExtension.AlternativeName(
                        SubjectAlternativeNameExtension.NameType.otherName,
                        "SAN Type '${it.tagNo}' is not implemented"
                    )
                }
            }
        }

    companion object {
        fun createExtension(extension: SubjectAlternativeNameExtension): ASN1Object {

            val sans = extension.alternativeNames.map {
                when (it.type) {
                    SubjectAlternativeNameExtension.NameType.dNSName -> GeneralName(GeneralName.dNSName, it.value)
                    SubjectAlternativeNameExtension.NameType.uniformResourceIdentifier -> GeneralName(
                        GeneralName.uniformResourceIdentifier,
                        it.value
                    )

                    SubjectAlternativeNameExtension.NameType.IPAddress -> GeneralName(GeneralName.iPAddress, it.value)
                    SubjectAlternativeNameExtension.NameType.registeredID -> GeneralName(
                        GeneralName.registeredID,
                        it.value
                    )

                    SubjectAlternativeNameExtension.NameType.directoryName -> GeneralName(
                        GeneralName.directoryName,
                        it.value
                    )

                    SubjectAlternativeNameExtension.NameType.rfc822Name -> GeneralName(GeneralName.rfc822Name, it.value)
                    SubjectAlternativeNameExtension.NameType.ediPartyName -> GeneralName(
                        GeneralName.ediPartyName,
                        it.value
                    )

                    SubjectAlternativeNameExtension.NameType.x400Address -> GeneralName(
                        GeneralName.x400Address,
                        it.value
                    )

                    SubjectAlternativeNameExtension.NameType.otherName -> GeneralName(GeneralName.otherName, it.value)
                }
            }.toTypedArray()
            return GeneralNames(sans)
        }

    }
}