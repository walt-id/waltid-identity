package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.KeyUsageExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

internal class BouncyKeyUsageExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    KeyUsageExtension {

    private val keyUsage = KeyUsage.getInstance(extension.parsedValue)

    override val keyPurposeIdList: Set<KeyUsageExtension.KeyUsage>
        get() {
            val keyUsageSet = mutableSetOf<KeyUsageExtension.KeyUsage>()

            if (keyUsage.hasUsages(KeyUsage.digitalSignature)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.digitalSignature)
            }
            if (keyUsage.hasUsages(KeyUsage.nonRepudiation)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.nonRepudiation)
            }
            if (keyUsage.hasUsages(KeyUsage.keyEncipherment)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.keyEncipherment)
            }
            if (keyUsage.hasUsages(KeyUsage.dataEncipherment)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.dataEncipherment)
            }
            if (keyUsage.hasUsages(KeyUsage.keyAgreement)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.keyAgreement)
            }
            if (keyUsage.hasUsages(KeyUsage.keyCertSign)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.keyCertSign)
            }
            if (keyUsage.hasUsages(KeyUsage.cRLSign)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.cRLSign)
            }
            if (keyUsage.hasUsages(KeyUsage.encipherOnly)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.encipherOnly)
            }
            if (keyUsage.hasUsages(KeyUsage.decipherOnly)) {
                keyUsageSet.add(KeyUsageExtension.KeyUsage.decipherOnly)
            }
            return keyUsageSet
        }

    companion object {
        fun createExtension(ext: KeyUsageExtension): ASN1Object {
            var keyUsageValue: Int = 0
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.digitalSignature)) {
                keyUsageValue = keyUsageValue or KeyUsage.digitalSignature
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.nonRepudiation)) {
                keyUsageValue = keyUsageValue or KeyUsage.nonRepudiation
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyEncipherment)) {
                keyUsageValue = keyUsageValue or KeyUsage.keyEncipherment
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.dataEncipherment)) {
                keyUsageValue = keyUsageValue or KeyUsage.dataEncipherment
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyAgreement)) {
                keyUsageValue = keyUsageValue or KeyUsage.keyAgreement
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign)) {
                keyUsageValue = keyUsageValue or KeyUsage.cRLSign
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign)) {
                keyUsageValue = keyUsageValue or KeyUsage.keyCertSign
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.encipherOnly)) {
                keyUsageValue = keyUsageValue or KeyUsage.encipherOnly
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.decipherOnly)) {
                keyUsageValue = keyUsageValue or KeyUsage.decipherOnly
            }
            return KeyUsage(keyUsageValue)
        }
    }

}