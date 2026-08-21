package id.walt.certificate.x509.extension

import id.walt.certificate.x509.model.GeneralName

interface SubjectAlternativeNameExtension : Extension {

    val alternativeNames: List<GeneralName>

    companion object {
        const val OID = "2.5.29.17"
        const val NAME = "Subject Alternative Name"

        fun MutableExtensionContainer.extensionSan(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionSan: SubjectAlternativeNameExtension?
            get() {
                val ext = this.extensions[OID]
                return ext as? SubjectAlternativeNameExtension?
            }
    }

    class Builder(
        override val critical: Boolean = false,
    ) : SubjectAlternativeNameExtension {
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