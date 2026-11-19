package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.crypto.AdesDigestAlgorithm

/**
 * RFC 5755 4.2.2 holder binding, independent of attribute-authority trust and AC signature validation.
 * AdES keeps the original DER because waltid-x509 currently models public-key certificates, not ACs.
 */
class AttributeCertificateHolder(attributeCertificate: ByteArray) {
    private val info = Der.parse(attributeCertificate).expectSequence().child(0).expectSequence()
    private val holder = info.child(1).expectSequence()

    init {
        require(info.child(0).asInt() == 1) { "Only X.509 v2 attribute certificates are supported" }
        require(holder.children.isNotEmpty() && holder.children.all { it.isContext(0) || it.isContext(1) || it.isContext(2) }) {
            "Missing or unsupported attribute-certificate holder"
        }
        require(holder.children.map { it.tagNumber }.distinct().size == holder.children.size) { "Duplicate holder fields" }
    }

    /** All supplied holder identifiers must refer to [certificate]; an unsupported identifier never matches. */
    suspend fun matches(certificate: X509Certificate): Boolean {
        for (field in holder.children) {
            val matches = when (field.tagNumber) {
                0 -> {
                    // IssuerSerial is IMPLICIT [0]; issuerUID is not supported by this profile.
                    require(field.children.size == 2) { "Unsupported holder issuerUID" }
                    namesMatch(field.child(0), certificate.issuer, certificate, "2.5.29.18") &&
                        field.child(1).asIntegerBytes().unsigned().contentEquals(certificate.serialNumber.unsigned())
                }
                1 -> namesMatch(field, certificate.subject, certificate, "2.5.29.17")
                2 -> {
                    require(field.children.size == 3) { "Unsupported holder object type" }
                    val type = field.child(0).asInt()
                    require(type in 0..1) { "Unsupported holder digest object type" }
                    val data = when (type) {
                        0 -> Der.parse(certificate.publicKey.encoded).child(1).asBitStringBytes()
                        else -> certificate.encoded
                    }
                    val digest = AdesDigestAlgorithm.fromOid(field.child(1).child(0).asOid())
                    digest.digest(data).contentEquals(field.child(2).asBitStringBytes())
                }
                else -> false
            }
            if (!matches) return false
        }
        return true
    }

    private fun namesMatch(names: DerElement, dn: DistinguishedName, certificate: X509Certificate, alternativeNamesOid: String): Boolean {
        require(names.children.isNotEmpty()) { "Empty holder GeneralNames" }
        val alternatives = certificate.extension(alternativeNamesOid)?.parsed?.children.orEmpty()
        return names.children.any { name ->
            if (name.isContext(4)) DistinguishedName.of(name.explicitInner()).matches(dn)
            else alternatives.any { it.encoded.contentEquals(name.encoded) }
        }
    }

    private fun ByteArray.unsigned(): ByteArray = dropWhile { it == 0.toByte() }.toByteArray()
}
