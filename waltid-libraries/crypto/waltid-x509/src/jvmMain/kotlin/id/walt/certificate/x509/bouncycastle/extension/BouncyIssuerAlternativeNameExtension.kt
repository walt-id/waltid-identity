package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

internal class BouncyIssuerAlternativeNameExtension(extension: BouncyCastleExtension) :
    BouncyAlternativeNameExtension(extension),
    IssuerAlternativeNameExtension {

    companion object {
        fun createExtension(extension: IssuerAlternativeNameExtension): ASN1Object {
            return BouncyAlternativeNameExtension.createExtension(extension)
        }
    }
}