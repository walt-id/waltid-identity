package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.GenericExtension
import kotlinx.io.bytestring.ByteString

internal class SignumGenericExtension(extension: X509CertificateExtension) : SignumExtension(extension),
    GenericExtension {

    override val encoded: ByteString
        get() = ByteString(extension.value.derEncoded)
}