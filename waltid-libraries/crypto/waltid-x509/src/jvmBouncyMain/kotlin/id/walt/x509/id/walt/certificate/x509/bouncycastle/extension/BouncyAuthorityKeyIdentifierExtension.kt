package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.PublicKeyInfo
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension
import id.walt.certificate.x509.model.GeneralName
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toGeneralNamesList
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier as BouncyCastleAuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyAuthorityKeyIdentifierExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    AuthorityKeyIdentifierExtension {
    override val keyIdentifier: ByteString?
        get() = parsedValue.keyIdentifierOctets?.let { ByteString(it) }
    override val authorityCertIssuer: List<GeneralName>?
        get() = parsedValue.authorityCertIssuer?.toGeneralNamesList()
    override val authorityCertSerialNumberRaw: ByteString?
        get() = parsedValue.authorityCertSerialNumber?.let { ByteString(it.toByteArray()) }

    val parsedValue: BouncyCastleAuthorityKeyIdentifier =
        BouncyCastleAuthorityKeyIdentifier.getInstance(extension.parsedValue)

    companion object {
        fun createExtension(
            extension: AuthorityKeyIdentifierExtension,
            authorityPublicKey: PublicKeyInfo
        ): ASN1Object {
            return BouncyCastleAuthorityKeyIdentifier(
                authorityPublicKey.keyId.toByteArray(),
            )
        }
    }
}