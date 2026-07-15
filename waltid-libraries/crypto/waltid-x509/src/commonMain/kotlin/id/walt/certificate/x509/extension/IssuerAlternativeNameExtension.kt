package id.walt.certificate.x509.extension

import id.walt.certificate.x509.model.GeneralName

interface IssuerAlternativeNameExtension : Extension {

    val alternativeNames: List<GeneralName>

    companion object {
        const val OID = "2.5.29.18"
        const val NAME = "Issuer Alternative Name"

        fun MutableExtensionContainer.extensionIssuerAltName(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionIssuerAltName: IssuerAlternativeNameExtension?
            get() {
                val ext = this.extensions[OID]
                return ext as? IssuerAlternativeNameExtension?
            }
    }

    class Builder(
        override var critical: Boolean = false,
    ) : IssuerAlternativeNameExtension {
        override val oid: String = OID
        override val alternativeNames: MutableList<GeneralName> = mutableListOf()

        fun addDnsName(dnsName: String) {
            alternativeNames.add(GeneralName(GeneralName.NameType.dNSName, dnsName))
        }

        fun addUri(uri: String) {
            alternativeNames.add(GeneralName(GeneralName.NameType.uniformResourceIdentifier, uri))
        }

        fun addEmail(email: String) {
            alternativeNames.add(GeneralName(GeneralName.NameType.rfc822Name, email))
        }

        fun addIpAddress(ipAddress: String) {
            alternativeNames.add(GeneralName(GeneralName.NameType.IPAddress, ipAddress))
        }

    }
}