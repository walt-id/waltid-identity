package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

internal class BouncyExtendedKeyUsageExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    ExtendedKeyUsageExtension {

    private val keyUsage = ExtendedKeyUsage.getInstance(extension.parsedValue)

    override val keyPurposeIdList: Set<ExtendedKeyUsageExtension.KeyUsage>
        get() = keyUsage.usages.map {
            ExtendedKeyUsageExtension.getKeyUsageById(it.id)
        }.toSet()

    companion object {
        fun createExtension(ext: ExtendedKeyUsageExtension): ASN1Object {
            val purposes = ext.keyPurposeIdList.map {
                KeyPurposeId.getInstance(ASN1ObjectIdentifier(it.id))
            }.toTypedArray()
            return ExtendedKeyUsage(purposes)
        }
    }
}