package id.walt.certificate.x509.extension

interface AlternativeNameExtension : Extension {

    val alternativeNames: List<AlternativeName>

    enum class NameType {
        dNSName,
        uniformResourceIdentifier,
        IPAddress,
        registeredID,
        directoryName,
        rfc822Name,
        ediPartyName,
        x400Address,
        otherName
    }

    data class AlternativeName(
        val type: NameType,
        val value: String
    )

    abstract class Builder(
        override val oid: String,
        override var critical: Boolean = false,
    ) : AlternativeNameExtension {
        override val alternativeNames: MutableList<SubjectAlternativeNameExtension.AlternativeName> = mutableListOf()

        fun addDnsName(dnsName: String) {
            alternativeNames.add(AlternativeName(SubjectAlternativeNameExtension.NameType.dNSName, dnsName))
        }

        fun addUri(uri: String) {
            alternativeNames.add(
                AlternativeName(
                    SubjectAlternativeNameExtension.NameType.uniformResourceIdentifier,
                    uri
                )
            )
        }

        fun addEmail(email: String) {
            alternativeNames.add(AlternativeName(SubjectAlternativeNameExtension.NameType.rfc822Name, email))
        }

        fun addIpAddress(ipAddress: String) {
            alternativeNames.add(AlternativeName(SubjectAlternativeNameExtension.NameType.IPAddress, ipAddress))
        }
    }
}