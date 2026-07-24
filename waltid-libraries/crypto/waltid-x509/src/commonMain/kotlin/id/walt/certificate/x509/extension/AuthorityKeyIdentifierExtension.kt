package id.walt.certificate.x509.extension

import id.walt.certificate.x509.model.GeneralName
import kotlinx.io.bytestring.ByteString

interface AuthorityKeyIdentifierExtension : Extension {

    val keyIdentifier: ByteString?
    val authorityCertIssuer: List<GeneralName>?
    val authorityCertSerialNumberRaw: ByteString?

    companion object {

        const val OID = "2.5.29.35"
        const val NAME = "Authority Key Identifier"

        fun MutableExtensionContainer.extensionAuthorityKeyIdentifier(block: (Builder.() -> Unit)? = null) {
            val builder =
                Builder(keyIdentifier = ByteString(), authorityCertIssuer = null, authorityCertSerialNumberRaw = null)
            block?.invoke(builder)
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionAuthorityKeyIdentifier: AuthorityKeyIdentifierExtension?
            get() {
                return this.extensions[OID] as? AuthorityKeyIdentifierExtension?
            }

    }

    data class Builder(
        override var critical: Boolean = false,
        override var keyIdentifier: ByteString = ByteString(),
        override var authorityCertIssuer: List<GeneralName>?,
        override var authorityCertSerialNumberRaw: ByteString?
    ) : AuthorityKeyIdentifierExtension {
        override val oid: String = OID
    }
}