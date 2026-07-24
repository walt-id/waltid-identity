package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.GenericExtension
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyGenericExtension(extension: BouncyCastleExtension) : BouncyExtension(extension), GenericExtension {
    override val encoded: ByteString
        get() = ByteString(extension.encoded)
}