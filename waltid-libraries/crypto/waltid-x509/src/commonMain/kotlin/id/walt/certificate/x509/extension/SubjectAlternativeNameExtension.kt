package id.walt.certificate.x509.extension

interface SubjectAlternativeNameExtension : AlternativeNameExtension {

    typealias AlternativeName = AlternativeNameExtension.AlternativeName
    typealias NameType = AlternativeNameExtension.NameType

    override val alternativeNames: List<AlternativeName>

    class Builder(
        critical: Boolean = false,
    ) : AlternativeNameExtension.Builder(
        OID,
        critical,
    ), SubjectAlternativeNameExtension

    companion object {
        const val OID = "2.5.29.17"

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
}