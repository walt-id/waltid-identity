package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.encoding.decodeToBoolean
import at.asitplus.signum.indispensable.asn1.encoding.decodeToInt
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension

class SignumBasicConstraintsExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    BasicConstraintsExtension {

    override val cA: Boolean
        get() = decodedValue.cA

    override val pathLenConstraint: Int?
        get() = decodedValue.pathLenConstraint

    private val decodedValue: DecodedValue = parseExtensionValue(extension)

    private class DecodedValue(
        val cA: Boolean,
        val pathLenConstraint: Int?
    )

    companion object {

        private fun parseExtensionValue(extension: X509CertificateExtension): DecodedValue {
            val sequence = extension.content.asSequence()
            val isCA = sequence.children
                .firstOrNull { it.tag == Asn1Element.Tag.BOOL }
                ?.asPrimitive()
                ?.decodeToBoolean()
                ?: false

            val pathLengthConstraint = sequence.children
                .firstOrNull { it.tag == Asn1Element.Tag.INT }
                ?.asPrimitive()
                ?.decodeToInt()
            return DecodedValue(isCA, pathLengthConstraint)
        }

        fun createExtension(ext: BasicConstraintsExtension): Asn1PrimitiveOctetString =
            Asn1.Sequence {
                +Asn1.Bool(ext.cA)
                ext.pathLenConstraint?.let { +Asn1.Int(it) }
            }.let { Asn1PrimitiveOctetString(it.derEncoded) }
    }
}