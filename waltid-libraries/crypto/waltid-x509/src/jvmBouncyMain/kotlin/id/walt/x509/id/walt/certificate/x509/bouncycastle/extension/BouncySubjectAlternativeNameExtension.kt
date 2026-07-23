package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension


class BouncySubjectAlternativeNameExtension(extension: BouncyCastleExtension) :
    BouncyAlternativeNameExtension(extension),
    SubjectAlternativeNameExtension {


    companion object {
        fun createExtension(extension: SubjectAlternativeNameExtension): ASN1Object {
            return BouncyAlternativeNameExtension.createExtension(extension)
        }
    }
}