package id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toBouncyCastleGeneralName
import id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toGeneralName
import id.walt.certificate.x509.extension.AuthorityInfoAccessExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.AuthorityInformationAccess
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyAuthorityInfoAccessExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    AuthorityInfoAccessExtension {

    private val accessInfo = AuthorityInformationAccess.getInstance(extension.parsedValue)

    override val accessDescriptions: List<AuthorityInfoAccessExtension.AccessDescription>
        get() = accessInfo.accessDescriptions.map { ad ->
            val method = AuthorityInfoAccessExtension.AccessMethod.entries
                .firstOrNull { it.oid == ad.accessMethod.id }
                ?: error("Unsupported AccessMethod OID '${ad.accessMethod.id}'")
            AuthorityInfoAccessExtension.AccessDescription(
                accessMethod = method,
                accessLocation = ad.accessLocation.toGeneralName()
            )
        }

    companion object {
        fun createExtension(ext: AuthorityInfoAccessExtension): ASN1Object {
            val descriptions = ext.accessDescriptions.map { ad ->
                AccessDescription(
                    ASN1ObjectIdentifier(ad.accessMethod.oid),
                    ad.accessLocation.toBouncyCastleGeneralName()
                )
            }.toTypedArray()
            return AuthorityInformationAccess(descriptions)
        }
    }
}
