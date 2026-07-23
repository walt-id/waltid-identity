package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.BasicConstraintsExtension
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyBasicConstraintsExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    BasicConstraintsExtension {

    private val constraints = BasicConstraints.getInstance(extension.parsedValue)

    override val cA: Boolean
        get() = constraints.isCA
    override val pathLenConstraint: Int?
        get() = constraints.pathLenConstraint?.toInt()

    companion object {
        fun createExtension(ext: BasicConstraintsExtension): ASN1Object {
            val ca = if (ext.cA) {
                ASN1Boolean.TRUE
            } else {
                ASN1Boolean.FALSE
            }
            val lengthConstraint = ext.pathLenConstraint?.let { ASN1Integer(it) }
            val sequence = lengthConstraint?.let { lc ->
                DERSequence(
                    ca,
                    lc
                )
            } ?: DERSequence(ca)
            return BasicConstraints.getInstance(sequence)
        }
    }
}