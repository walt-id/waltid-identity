package id.walt.certificate.x509.extension

interface SubjectAlternativeNameExtension : Extension {

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

    companion object {
        const val OID = "2.5.29.17"

        fun MutableExtensionContainer.extensionSan(block: SubjectAlternativeNameExtension.Builder.() -> Unit) {
            val builder = Builder(OID)
            builder.block()
            this.extensions[KeyUsageExtension.OID] = builder
        }

        val ExtensionContainer.extensionSan: SubjectAlternativeNameExtension?
            get() {
                val ext = this.extensions[OID]
                return ext as? SubjectAlternativeNameExtension?
            }
    }

    data class Builder(
        override val oid: String = OID,
        override var critical: Boolean = false,
    ) : SubjectAlternativeNameExtension {
        override val alternativeNames: MutableList<AlternativeName> = mutableListOf()

        fun addDnsName(dnsName: String) {
            alternativeNames.add(AlternativeName(NameType.dNSName, dnsName))
        }

        fun addUri(uri: String) {
            alternativeNames.add(AlternativeName(NameType.uniformResourceIdentifier, uri))
        }

        fun addIpAddress(ipAddress: String) {
            alternativeNames.add(AlternativeName(NameType.IPAddress, ipAddress))
        }
    }
}