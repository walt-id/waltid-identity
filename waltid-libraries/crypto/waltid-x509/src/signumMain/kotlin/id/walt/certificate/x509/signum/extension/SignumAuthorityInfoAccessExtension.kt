package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toAsn1Element
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toGeneralNames

/**
 * AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
 * AccessDescription ::= SEQUENCE { accessMethod OBJECT IDENTIFIER, accessLocation GeneralName }
 */
class SignumAuthorityInfoAccessExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    AuthorityInfoAccessExtension {

    override val accessDescriptions: List<AuthorityInfoAccessExtension.AccessDescription>
        get() = extension.content.asSequence().children.map { accessDescription ->
            val children = accessDescription.asSequence().children.toList()
            val methodOid = ObjectIdentifier.decodeFromTlv(children[0].asPrimitive()).toString()
            val method = AuthorityInfoAccessExtension.AccessMethod.entries.firstOrNull { it.oid == methodOid }
                ?: error("Unsupported AccessMethod OID '$methodOid'")
            val accessLocation = listOf(children[1]).toGeneralNames().single()
            AuthorityInfoAccessExtension.AccessDescription(method, accessLocation)
        }

    companion object {
        fun createExtension(ext: AuthorityInfoAccessExtension): Asn1EncapsulatingOctetString =
            Asn1.OctetStringEncapsulating {
                +Asn1.Sequence {
                    ext.accessDescriptions.forEach { ad ->
                        +Asn1.Sequence {
                            +ObjectIdentifier(ad.accessMethod.oid)
                            +ad.accessLocation.toAsn1Element()
                        }
                    }
                }
            }
    }
}
