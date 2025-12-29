package id.walt.x509.id.walt.x509

import id.walt.x509.X509KeyUsage
import org.bouncycastle.asn1.x509.KeyUsage

fun KeyUsage.toCertificateKeyUsages(): Set<X509KeyUsage> {
    val usages = mutableSetOf<X509KeyUsage>()
    fun addIf(bit: Int, usage: X509KeyUsage) {
        if (this.hasUsages(bit)) usages.add(usage)
    }
    addIf(KeyUsage.digitalSignature, X509KeyUsage.DigitalSignature)
    addIf(KeyUsage.nonRepudiation, X509KeyUsage.NonRepudiation)
    addIf(KeyUsage.keyEncipherment, X509KeyUsage.KeyEncipherment)
    addIf(KeyUsage.dataEncipherment, X509KeyUsage.DataEncipherment)
    addIf(KeyUsage.keyAgreement, X509KeyUsage.KeyAgreement)
    addIf(KeyUsage.keyCertSign, X509KeyUsage.KeyCertSign)
    addIf(KeyUsage.cRLSign, X509KeyUsage.CRLSign)
    addIf(KeyUsage.encipherOnly, X509KeyUsage.EncipherOnly)
    addIf(KeyUsage.decipherOnly, X509KeyUsage.DecipherOnly)
    return usages
}

fun X509KeyUsage.toBCKeyUsage() = when (this) {
    X509KeyUsage.DigitalSignature -> KeyUsage.digitalSignature
    X509KeyUsage.NonRepudiation -> KeyUsage.nonRepudiation
    X509KeyUsage.KeyEncipherment -> KeyUsage.keyEncipherment
    X509KeyUsage.DataEncipherment -> KeyUsage.dataEncipherment
    X509KeyUsage.KeyAgreement -> KeyUsage.keyAgreement
    X509KeyUsage.KeyCertSign -> KeyUsage.keyCertSign
    X509KeyUsage.CRLSign -> KeyUsage.cRLSign
    X509KeyUsage.EncipherOnly -> KeyUsage.encipherOnly
    X509KeyUsage.DecipherOnly -> KeyUsage.decipherOnly
}

fun Iterable<X509KeyUsage>.toBouncyCastleKeyUsage(): KeyUsage {
    var bits = 0
    this.forEach { certKeyUsage ->
        bits = bits or certKeyUsage.toBCKeyUsage()
    }
    return KeyUsage(bits)
}