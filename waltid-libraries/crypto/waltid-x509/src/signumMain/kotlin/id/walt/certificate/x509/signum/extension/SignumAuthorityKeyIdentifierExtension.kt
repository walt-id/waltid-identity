package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.TagClass
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.model.GeneralName
import kotlinx.io.bytestring.ByteString

/**
 *  AuthorityKeyIdentifier ::= SEQUENCE {
 *       keyIdentifier             [0] KeyIdentifier           OPTIONAL,
 *       authorityCertIssuer       [1] GeneralNames            OPTIONAL,
 *       authorityCertSerialNumber [2] CertificateSerialNumber OPTIONAL  }
 *
 *    KeyIdentifier ::= OCTET STRING
 */
class SignumAuthorityKeyIdentifierExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    AuthorityKeyIdentifierExtension {

    override val keyIdentifier: ByteString?
        get() = parsedValue.children
            .filter {
                it.tag.tagClass == TagClass.CONTEXT_SPECIFIC && it.tag.tagValue == 0uL
            }
            .map { ByteString(it.asPrimitive().content) }
            .firstOrNull()

    override val authorityCertIssuer: List<GeneralName>?
        get() = TODO()
    override val authorityCertSerialNumberRaw: ByteString?
        get() = TODO()

    val parsedValue = extension.content.asSequence()
}