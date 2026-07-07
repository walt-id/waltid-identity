package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.Extension

import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

internal abstract class BouncyExtension(
    protected val extension: BouncyCastleExtension
) : Extension {
    override val oid: String
        get() = extension.extnId.id
    override val critical: Boolean
        get() = extension.isCritical
}