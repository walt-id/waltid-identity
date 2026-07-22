package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.Extension

abstract class SignumExtension(val extension: X509CertificateExtension) : Extension {

    override val oid: String = extension.oid.toString()

    override val critical: Boolean = extension.critical
}