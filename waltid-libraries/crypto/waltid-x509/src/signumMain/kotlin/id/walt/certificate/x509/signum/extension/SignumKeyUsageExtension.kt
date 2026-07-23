package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.BitSet
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.KeyUsageExtension

class SignumKeyUsageExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    KeyUsageExtension {

    override val keyPurposeIdList: Set<KeyUsageExtension.KeyUsage> = parseExtensionValue(extension)

    companion object {

        fun parseExtensionValue(extension: X509CertificateExtension): Set<KeyUsageExtension.KeyUsage> {
            val bits = Asn1BitString.decodeFromTlv(extension.content.asPrimitive()).toBitSet()
            require(bits.length().toInt() <= KeyUsageExtension.KeyUsage.entries.size)
            val keyUsageValue: MutableSet<KeyUsageExtension.KeyUsage> = mutableSetOf()
            if (bits[0]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.digitalSignature)
            }
            if (bits[1]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.nonRepudiation)
            }
            if (bits[2]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.keyEncipherment)
            }
            if (bits[3]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.dataEncipherment)
            }
            if (bits[4]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.keyAgreement)
            }
            if (bits[5]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.cRLSign)
            }
            if (bits[6]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.keyCertSign)
            }
            if (bits[7]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.encipherOnly)
            }
            if (bits[8]) {
                keyUsageValue.add(KeyUsageExtension.KeyUsage.decipherOnly)
            }
            return keyUsageValue.toSet()
        }

        fun createExtension(ext: KeyUsageExtension): Asn1PrimitiveOctetString = ext.let { ext ->
            val keyUsageValue = BitSet(KeyUsageExtension.KeyUsage.entries.size.toLong())
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.digitalSignature)) {
                keyUsageValue.set(0)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.nonRepudiation)) {
                keyUsageValue.set(1)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyEncipherment)) {
                keyUsageValue.set(2)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.dataEncipherment)) {
                keyUsageValue.set(3)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyAgreement)) {
                keyUsageValue.set(4)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign)) {
                keyUsageValue.set(5)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign)) {
                keyUsageValue.set(6)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.encipherOnly)) {
                keyUsageValue.set(7)
            }
            if (ext.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.decipherOnly)) {
                keyUsageValue.set(8)
            }
            Asn1.BitString(keyUsageValue)
        }.let {
            Asn1PrimitiveOctetString(it.derEncoded)
        }
    }

}