package id.walt.x509.id.walt.x509.iso

import id.walt.x509.iso.IssuerAlternativeName
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import java.security.cert.X509Certificate

fun IssuerAlternativeName.Companion.parseFromX509Certificate(
    cert: X509Certificate,
): IssuerAlternativeName {
    val issAltNamesBytes = requireNotNull(
        cert.getExtensionValue(Extension.issuerAlternativeName.id)
    ) {
        "Issuer alternative name X509 certificate extension must exist, but was found missing from input certificate"
    }
    val asn1OctetStr = ASN1OctetString.getInstance(issAltNamesBytes)
    val names = GeneralNames.getInstance(asn1OctetStr.octets).names
    require(names.isNotEmpty() && names.size <= 2)
    return IssuerAlternativeName(
        email = names.find { it.tagNo == GeneralName.rfc822Name }?.name?.toString(),
        uri = names.find { it.tagNo == GeneralName.uniformResourceIdentifier }?.name?.toString(),
    )
}