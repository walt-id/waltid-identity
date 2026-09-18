package id.walt.certificate.x509.extension

import id.walt.certificate.x509.model.GeneralName

/**
 * AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
 *
 * AccessDescription ::= SEQUENCE {
 *      accessMethod          OBJECT IDENTIFIER,
 *      accessLocation        GeneralName }
 *
 * id-ad-caIssuers OBJECT IDENTIFIER ::= { id-ad 2 } -- 1.3.6.1.5.5.7.48.2
 * id-ad-ocsp      OBJECT IDENTIFIER ::= { id-ad 1 } -- 1.3.6.1.5.5.7.48.1
 */
interface AuthorityInfoAccessExtension : Extension {

    val accessDescriptions: List<AccessDescription>

    enum class AccessMethod(val oid: String) {
        caIssuers("1.3.6.1.5.5.7.48.2"),
        ocsp("1.3.6.1.5.5.7.48.1")
    }

    data class AccessDescription(
        val accessMethod: AccessMethod,
        val accessLocation: GeneralName
    )

    companion object {

        const val OID = "1.3.6.1.5.5.7.1.1"
        const val NAME = "Authority Information Access"

        fun MutableExtensionContainer.extensionAuthorityInfoAccess(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionAuthorityInfoAccess: AuthorityInfoAccessExtension?
            get() {
                return this.extensions[OID] as? AuthorityInfoAccessExtension?
            }
    }

    data class Builder(
        override var critical: Boolean = false,
    ) : AuthorityInfoAccessExtension {
        override val oid: String = OID
        override val accessDescriptions: MutableList<AccessDescription> = mutableListOf()

        fun addCaIssuerUri(uri: String) {
            accessDescriptions.add(
                AccessDescription(AccessMethod.caIssuers, GeneralName(GeneralName.NameType.uniformResourceIdentifier, uri))
            )
        }

        fun addOcspResponderUri(uri: String) {
            accessDescriptions.add(
                AccessDescription(AccessMethod.ocsp, GeneralName(GeneralName.NameType.uniformResourceIdentifier, uri))
            )
        }
    }
}
