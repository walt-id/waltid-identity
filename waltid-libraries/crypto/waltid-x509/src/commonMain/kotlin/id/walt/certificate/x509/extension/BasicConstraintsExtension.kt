package id.walt.certificate.x509.extension

interface BasicConstraintsExtension : Extension {
    val cA: Boolean
    val pathLenConstraint: Int?

    companion object {
        const val OID = "2.5.29.19"

        fun MutableExtensionContainer.extensionBasicConstraints(block: Builder.() -> Unit) {
            val builder = Builder(oid = OID)
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionBasicConstraints: BasicConstraintsExtension?
            get() {
                return this.extensions[OID] as? BasicConstraintsExtension?
            }

    }

    data class Builder(
        override val oid: String,
        override var critical: Boolean = false,
        override var cA: Boolean = false,
        override var pathLenConstraint: Int? = null,
    ) : BasicConstraintsExtension

}