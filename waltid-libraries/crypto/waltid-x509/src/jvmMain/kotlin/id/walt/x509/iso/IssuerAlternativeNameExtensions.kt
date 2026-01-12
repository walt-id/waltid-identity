package id.walt.x509.iso

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import java.security.cert.X509Certificate

fun IssuerAlternativeName.Companion.parseFromX509Certificate(
    cert: X509Certificate,
): IssuerAlternativeName {
    val issAltNamesExtRawBytes = requireNotNull(
        cert.getExtensionValue(Extension.issuerAlternativeName.id)
    ) {
        "Issuer alternative name X509 certificate extension must exist, but was found missing from input certificate"
    }
    val names = GeneralNames.getInstance(
        ASN1OctetString.getInstance(issAltNamesExtRawBytes).octets
    ).names
    require(names.isNotEmpty() && names.size <= 2)
    val result = IssuerAlternativeName(
        email = names.find { it.tagNo == GeneralName.rfc822Name }?.name?.toString(),
        uri = names.find { it.tagNo == GeneralName.uniformResourceIdentifier }?.name?.toString(),
    )
    require(result.email != null || result.uri != null) {
        "IssuerAlternativeName must contain at least one of email (rfc822Name) or uri (uniformResourceIdentifier)"
    }
    return result
}