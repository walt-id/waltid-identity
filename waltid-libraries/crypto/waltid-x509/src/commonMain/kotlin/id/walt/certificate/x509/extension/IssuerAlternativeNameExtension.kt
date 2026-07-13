package id.walt.certificate.x509.extension

interface IssuerAlternativeNameExtension : AlternativeNameExtension {

    typealias AlternativeName = AlternativeNameExtension.AlternativeName
    typealias NameType = AlternativeNameExtension.NameType

    override val alternativeNames: List<AlternativeName>

    companion object {
        const val OID = "2.5.29.18"

        class Builder(
            critical: Boolean = false,
        ) : AlternativeNameExtension.Builder(
            OID,
            critical,
        ), IssuerAlternativeNameExtension


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
}