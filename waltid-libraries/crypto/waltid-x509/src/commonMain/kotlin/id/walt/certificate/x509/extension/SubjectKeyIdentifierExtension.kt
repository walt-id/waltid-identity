package id.walt.certificate.x509.extension

import kotlinx.io.bytestring.ByteString

interface SubjectKeyIdentifierExtension : Extension {

    val keyIdentifier: ByteString

    companion object {

        const val OID = "2.5.29.14"
        const val NAME= "Subject Key Identifier"

        /**
         * When building a certificate, value will be set by the Certificate Signer
         */
        fun MutableExtensionContainer.extensionSubjectKeyIdentifier(block: (Builder.() -> Unit)? = null) {
            val builder = Builder(oid = OID, keyIdentifier = ByteString())
            block?.invoke(builder)
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionSubjectKeyIdentifier: SubjectKeyIdentifierExtension?
            get() {
                return this.extensions[OID] as? SubjectKeyIdentifierExtension?
            }

    }

    data class Builder(
        override val oid: String = OID,
        override var critical: Boolean = false,
        override var keyIdentifier: ByteString = ByteString(),
    ) : SubjectKeyIdentifierExtension
}