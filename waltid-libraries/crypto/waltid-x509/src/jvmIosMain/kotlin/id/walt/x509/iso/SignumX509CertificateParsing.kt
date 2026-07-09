package id.walt.x509.iso

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.asn1.Asn1Structure
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.TagClass
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.encoding.decodeToBoolean
import at.asitplus.signum.indispensable.asn1.encoding.decodeToInt
import at.asitplus.signum.indispensable.asn1.encoding.decodeToString
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.asn1.readOid
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.X509Certificate
import id.walt.x509.X509BasicConstraints
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509V3ExtensionOID
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString

internal fun X509Certificate.signumBasicConstraints(): X509BasicConstraints {
    val sequence = extensionAsSequence("2.5.29.19") ?: return X509BasicConstraints(
        isCA = false,
        pathLengthConstraint = -1,
    )
    val isCA = sequence.children
        .firstOrNull { it.tag == Asn1Element.Tag.BOOL }
        ?.asPrimitive()
        ?.decodeToBoolean()
        ?: false
    val pathLengthConstraint = sequence.children
        .firstOrNull { it.tag == Asn1Element.Tag.INT }
        ?.asPrimitive()
        ?.decodeToInt()
        ?: -1

    return X509BasicConstraints(
        isCA = isCA,
        pathLengthConstraint = pathLengthConstraint,
    )
}

internal fun X509Certificate.signumKeyUsages(): Set<X509KeyUsage> {
    val bitString = extensionElement("2.5.29.15")?.asPrimitive() ?: return emptySet()
    val content = bitString.content
    if (content.size <= 1) return emptySet()

    fun hasBit(index: Int): Boolean {
        val byte = content.getOrNull(1 + index / 8)?.toInt() ?: return false
        return (byte and (0x80 ushr (index % 8))) != 0
    }

    return buildSet {
        if (hasBit(0)) add(X509KeyUsage.DigitalSignature)
        if (hasBit(1)) add(X509KeyUsage.NonRepudiation)
        if (hasBit(2)) add(X509KeyUsage.KeyEncipherment)
        if (hasBit(3)) add(X509KeyUsage.DataEncipherment)
        if (hasBit(4)) add(X509KeyUsage.KeyAgreement)
        if (hasBit(5)) add(X509KeyUsage.KeyCertSign)
        if (hasBit(6)) add(X509KeyUsage.CRLSign)
        if (hasBit(7)) add(X509KeyUsage.EncipherOnly)
        if (hasBit(8)) add(X509KeyUsage.DecipherOnly)
    }
}

internal fun X509Certificate.signumSubjectKeyIdentifierHex(): String =
    requireNotNull(extensionElement("2.5.29.14")?.asOctetString()?.content) {
        "Subject key identifier must exist as part of the X509 certificate, but was found missing"
    }.let { ByteString(it).toHexString() }

internal fun X509Certificate.signumAuthorityKeyIdentifierHex(): String =
    requireNotNull(
        extensionAsSequence("2.5.29.35")
            ?.children
            ?.filterIsInstance<Asn1Primitive>()
            ?.firstOrNull { it.tag.isContextSpecific(0uL) }
            ?.content
    ) {
        "Authority key identifier must exist as part of the X509 certificate, but was found missing"
    }.let { ByteString(it).toHexString() }

internal fun X509Certificate.signumIssuerAlternativeName(): IssuerAlternativeName {
    val issuerAlternativeNames = requireNotNull(tbsCertificate.issuerAlternativeNames) {
        "Issuer alternative name X509 certificate extension must exist, but was found missing from input certificate"
    }
    val result = IssuerAlternativeName(
        email = issuerAlternativeNames.rfc822Names?.firstOrNull(),
        uri = issuerAlternativeNames.uris?.firstOrNull(),
    )
    require(result.email != null || result.uri != null) {
        "IssuerAlternativeName must contain at least one of email (rfc822Name) or uri (uniformResourceIdentifier)"
    }
    return result
}

internal fun X509Certificate.signumCriticalExtensionOids(): Set<X509V3ExtensionOID> =
    tbsCertificate.extensions.orEmpty()
        .filter { it.critical }
        .mapNotNull { X509V3ExtensionOID.fromOID(it.oid.toString()) }
        .toSet()

internal fun X509Certificate.signumNonCriticalExtensionOids(): Set<X509V3ExtensionOID> =
    tbsCertificate.extensions.orEmpty()
        .filterNot { it.critical }
        .mapNotNull { X509V3ExtensionOID.fromOID(it.oid.toString()) }
        .toSet()

internal fun X509Certificate.signumExtendedKeyUsageOids(): Set<String> {
    val sequence = requireNotNull(extensionAsSequence("2.5.29.37")) {
        "Extended key usage must exist as part of the X509 certificate, but was found missing"
    }
    val usages = sequence.children.map { it.asPrimitive().readOid().toString() }.toSet()
    require(usages.isNotEmpty()) {
        "Extended key usage must exist as part of the X509 certificate, but was found empty"
    }
    return usages
}

internal fun X509Certificate.signumCrlDistributionPointUri(): String =
    requireNotNull(signumCrlDistributionPointUriOrNull()) {
        "CRL distribution point URI must exist as part of the X509 certificate, but was found missing"
    }

internal fun X509Certificate.signumCrlDistributionPointUriOrNull(): String? =
    extensionAsSequence("2.5.29.31")
        ?.findContextSpecificPrimitive(6uL)
        ?.content
        ?.decodeToString()

internal fun List<RelativeDistinguishedName>.signumAttributeValue(oid: String): String? =
    asSequence()
        .flatMap { it.attrsAndValues.asSequence() }
        .firstOrNull { it.oid == ObjectIdentifier(oid) }
        ?.value
        ?.asPrimitive()
        ?.decodeToString()

internal fun List<RelativeDistinguishedName>.toDerByteString(): ByteString =
    ByteString(
        Asn1.Sequence {
            forEach { +it }
        }.derEncoded
    )

internal fun List<RelativeDistinguishedName>.toDisplayString(): String =
    flatMap { it.attrsAndValues }
        .joinToString(",") { "${it.displayName()}=${it.value.asPrimitive().decodeToString()}" }

private fun AttributeTypeAndValue.displayName(): String =
    when (oid.toString()) {
        "2.5.4.6" -> "C"
        "2.5.4.3" -> "CN"
        "2.5.4.8" -> "ST"
        "2.5.4.10" -> "O"
        "2.5.4.7" -> "L"
        "2.5.4.11" -> "OU"
        else -> oid.toString()
    }

private fun X509Certificate.extensionElement(oid: String): Asn1Element? =
    tbsCertificate.extensions
        ?.firstOrNull { it.oid == ObjectIdentifier(oid) }
        ?.value
        ?.asOctetString()
        ?.content
        ?.let { Asn1Element.parse(it) }

private fun X509Certificate.extensionAsSequence(oid: String) =
    extensionElement(oid)?.asSequence()

private fun Asn1Element.findContextSpecificPrimitive(tagValue: ULong): Asn1Primitive? {
    if (this is Asn1Primitive && tag.isContextSpecific(tagValue)) return this

    val children = when (this) {
        is Asn1Structure -> children
        else -> return null
    }

    return children.firstNotNullOfOrNull { it.findContextSpecificPrimitive(tagValue) }
}

private fun Asn1Element.Tag.isContextSpecific(tagValue: ULong): Boolean =
    tagClass == TagClass.CONTEXT_SPECIFIC && this.tagValue == tagValue
