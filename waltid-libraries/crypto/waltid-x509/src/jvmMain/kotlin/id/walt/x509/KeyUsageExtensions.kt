package id.walt.x509.id.walt.x509

import id.walt.x509.CertificateKeyUsage
import org.bouncycastle.asn1.x509.KeyUsage

fun KeyUsage.toCertificateKeyUsages(): Set<CertificateKeyUsage> {
    val usages = mutableSetOf<CertificateKeyUsage>()
    fun addIf(bit: Int, usage: CertificateKeyUsage) {
        if (this.hasUsages(bit)) usages.add(usage)
    }
    addIf(KeyUsage.digitalSignature, CertificateKeyUsage.DigitalSignature)
    addIf(KeyUsage.nonRepudiation, CertificateKeyUsage.NonRepudiation)
    addIf(KeyUsage.keyEncipherment, CertificateKeyUsage.KeyEncipherment)
    addIf(KeyUsage.dataEncipherment, CertificateKeyUsage.DataEncipherment)
    addIf(KeyUsage.keyAgreement, CertificateKeyUsage.KeyAgreement)
    addIf(KeyUsage.keyCertSign, CertificateKeyUsage.KeyCertSign)
    addIf(KeyUsage.cRLSign, CertificateKeyUsage.CRLSign)
    addIf(KeyUsage.encipherOnly, CertificateKeyUsage.EncipherOnly)
    addIf(KeyUsage.decipherOnly, CertificateKeyUsage.DecipherOnly)
    return usages
}

fun CertificateKeyUsage.toBCKeyUsage() = when (this) {
    CertificateKeyUsage.DigitalSignature -> KeyUsage.digitalSignature
    CertificateKeyUsage.NonRepudiation -> KeyUsage.nonRepudiation
    CertificateKeyUsage.KeyEncipherment -> KeyUsage.keyEncipherment
    CertificateKeyUsage.DataEncipherment -> KeyUsage.dataEncipherment
    CertificateKeyUsage.KeyAgreement -> KeyUsage.keyAgreement
    CertificateKeyUsage.KeyCertSign -> KeyUsage.keyCertSign
    CertificateKeyUsage.CRLSign -> KeyUsage.cRLSign
    CertificateKeyUsage.EncipherOnly -> KeyUsage.encipherOnly
    CertificateKeyUsage.DecipherOnly -> KeyUsage.decipherOnly
}

fun Iterable<CertificateKeyUsage>.toBouncyCastleKeyUsage(): KeyUsage {
    var bits = 0
    this.forEach { certKeyUsage ->
        bits = bits or certKeyUsage.toBCKeyUsage()
    }
    return KeyUsage(bits)
}