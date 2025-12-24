package id.walt.x509.id.walt.x509.iso.documentsigner.certificate

import id.walt.x509.id.walt.x509.*
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import org.bouncycastle.asn1.x500.X500Name

fun DocumentSignerPrincipalName.Companion.parseFromJcaX500Name(
    name: X500Name,
): DocumentSignerPrincipalName {

    val country = requireNotNull(
        name.getCountryCode()
    ) {
        "Document signer country code must exist as part of principal name in X509 certificate , but was found missing"
    }

    val commonName = requireNotNull(
        name.getCommonName()
    ) {
        "Document signer common name must exist as part of principal name in X509 certificate , but was found missing"
    }

    return DocumentSignerPrincipalName(
        country = country,
        commonName = commonName,
        stateOrProvinceName = name.getStateOrProvinceName(),
        organizationName = name.getOrganizationName(),
        localityName = name.getLocalityName(),
    )
}

fun DocumentSignerPrincipalName.toJcaX500Name() = buildX500Name(
    country = country,
    commonName = commonName,
    stateOrProvinceName = stateOrProvinceName,
    organizationName = organizationName,
    localityName = localityName,
)