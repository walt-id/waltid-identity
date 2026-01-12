package id.walt.x509

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.cert.X509Certificate

/**
 * Set of critical v3 extension OIDs that are recognized by [X509V3ExtensionOID].
 *
 * Unknown OIDs are ignored.
 */
val X509Certificate.criticalX509V3ExtensionOIDs
    get() = criticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()


/**
 * Set of non-critical v3 extension OIDs that are recognized by [X509V3ExtensionOID].
 *
 * Unknown OIDs are ignored.
 */
val X509Certificate.nonCriticalX509V3ExtensionOIDs
    get() = nonCriticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()


/**
 * Parsed SubjectKeyIdentifier value as raw bytes, or null when absent.
 */
val X509Certificate.subjectKeyIdentifier: ByteString?
    get() = getExtensionValue(Extension.subjectKeyIdentifier.id)?.let { skiExtRaw ->
        SubjectKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(skiExtRaw).octets
        ).keyIdentifier?.toByteString()
    }

/**
 * Parsed AuthorityKeyIdentifier value as raw bytes, or null when absent.
 */
val X509Certificate.authorityKeyIdentifier: ByteString?
    get() = getExtensionValue(Extension.authorityKeyIdentifier.id)?.let { skiExtRaw ->
        AuthorityKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(skiExtRaw).octets
        ).keyIdentifierOctets?.toByteString()
    }

/**
 * Parsed KeyUsage extension as a set of [X509KeyUsage] values.
 *
 * Returns an empty set when the extension is not present.
 */
val X509Certificate.x509KeyUsages: Set<X509KeyUsage>
    get() = getExtensionValue(Extension.keyUsage.id)?.let { kuExtRaw ->
        KeyUsage.getInstance(
            ASN1OctetString.getInstance(kuExtRaw).octets
        ).toCertificateKeyUsages()
    } ?: emptySet()

/**
 * Parsed BasicConstraints as a platform-agnostic [X509BasicConstraints] view.
 */
val X509Certificate.x509BasicConstraints
    get() = X509BasicConstraints(
        isCA = (basicConstraints != -1),
        pathLengthConstraint = basicConstraints,
    )
