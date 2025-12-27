package id.walt.x509.id.walt.x509

import id.walt.x509.CertificateKeyUsage
import id.walt.x509.X509V3ExtensionOID
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.cert.X509Certificate

//TODO: REFACTOR AS PROPERTIES
fun X509Certificate.criticalX509V3ExtensionOIDs() =
    criticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()


fun X509Certificate.nonCriticalX509V3ExtensionOIDs() =
    nonCriticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()


val X509Certificate.subjectKeyIdentifier: ByteString?
    get() = getExtensionValue(Extension.subjectKeyIdentifier.id)?.let { skiExtRaw ->
        SubjectKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(skiExtRaw).octets
        ).keyIdentifier?.toByteString()
    }

val X509Certificate.authorityKeyIdentifier: ByteString?
    get() = getExtensionValue(Extension.authorityKeyIdentifier.id)?.let { skiExtRaw ->
        AuthorityKeyIdentifier.getInstance(
            ASN1OctetString.getInstance(skiExtRaw).octets
        ).keyIdentifierOctets?.toByteString()
    }

val X509Certificate.certificateKeyUsages: Set<CertificateKeyUsage>
    get() = getExtensionValue(Extension.keyUsage.id)?.let { kuExtRaw ->
        KeyUsage.getInstance(
            ASN1OctetString.getInstance(kuExtRaw)
        ).toCertificateKeyUsages()
    } ?: emptySet()