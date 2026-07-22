package id.walt.x509

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.decodeToBoolean
import at.asitplus.signum.indispensable.asn1.encoding.decodeToInt
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.asn1.readOid
import at.asitplus.signum.indispensable.pki.X509Certificate

internal data class PlatformCertificateConstraints(
    val isCertificateAuthority: Boolean,
    val pathLengthConstraint: Int?,
    val canSignCertificates: Boolean,
    val canSignData: Boolean,
    val extendedKeyUsageOids: Set<String>?,
    val basicConstraintsCritical: Boolean,
    val keyUsageCritical: Boolean,
    val criticalExtensionOids: Set<String>,
)

internal fun X509Certificate.platformCertificateConstraints(): PlatformCertificateConstraints {
    val extensions = tbsCertificate.extensions.orEmpty()
    val basicConstraintsExtension = extensions.firstOrNull { it.oid == basicConstraintsOid }
    val basicConstraints = basicConstraintsExtension?.value?.asOctetString()?.content
        ?.let(Asn1Element::parse)
        ?.asSequence()
    val isCertificateAuthority = basicConstraints?.children
        ?.firstOrNull { it.tag == Asn1Element.Tag.BOOL }
        ?.asPrimitive()
        ?.decodeToBoolean()
        ?: false
    val pathLengthConstraint = basicConstraints?.children
        ?.firstOrNull { it.tag == Asn1Element.Tag.INT }
        ?.asPrimitive()
        ?.decodeToInt()
    val keyUsageExtension = extensions.firstOrNull { it.oid == keyUsageOid }
    val keyUsageContent = keyUsageExtension?.value?.asOctetString()?.content
        ?.let(Asn1Element::parse)
        ?.asPrimitive()
        ?.content
    fun hasKeyUsageBit(bit: Int): Boolean = keyUsageContent?.let { content ->
        val byte = content.getOrNull(1 + bit / Byte.SIZE_BITS)?.toInt() ?: return@let false
        (byte and (0x80 ushr (bit % Byte.SIZE_BITS))) != 0
    } ?: false
    val extendedKeyUsageOids = extensions.firstOrNull { it.oid == extendedKeyUsageOid }
        ?.value?.asOctetString()?.content
        ?.let(Asn1Element::parse)
        ?.asSequence()
        ?.children
        ?.map { it.asPrimitive().readOid().toString() }
        ?.toSet()
    return PlatformCertificateConstraints(
        isCertificateAuthority = isCertificateAuthority,
        pathLengthConstraint = pathLengthConstraint,
        canSignCertificates = hasKeyUsageBit(KEY_CERT_SIGN_BIT),
        canSignData = hasKeyUsageBit(DIGITAL_SIGNATURE_BIT),
        extendedKeyUsageOids = extendedKeyUsageOids,
        basicConstraintsCritical = basicConstraintsExtension?.critical == true,
        keyUsageCritical = keyUsageExtension?.critical == true,
        criticalExtensionOids = extensions.filter { it.critical }.map { it.oid.toString() }.toSet(),
    )
}

private val basicConstraintsOid = ObjectIdentifier("2.5.29.19")
private val keyUsageOid = ObjectIdentifier("2.5.29.15")
private val extendedKeyUsageOid = ObjectIdentifier("2.5.29.37")
private const val DIGITAL_SIGNATURE_BIT = 0
private const val KEY_CERT_SIGN_BIT = 5

internal val processedCriticalExtensionOids = setOf(
    "2.5.29.14", // subjectKeyIdentifier
    "2.5.29.15", // keyUsage
    "2.5.29.19", // basicConstraints
    "2.5.29.35", // authorityKeyIdentifier
)
