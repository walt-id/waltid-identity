package id.walt.x509.id.walt.x509.iso.iaca.certificate

import id.walt.x509.id.walt.x509.*
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import org.bouncycastle.asn1.x500.X500Name

fun IACAPrincipalName.Companion.parseFromJcaX500Name(
    name: X500Name,
): IACAPrincipalName {

    val country = requireNotNull(
        name.getCountryCode()
    ) {
        "IACA country code must exist as part of principal name in X509 certificate , but was found missing"
    }

    val commonName = requireNotNull(
        name.getCommonName()
    ) {
        "IACA common name must exist as part of principal name in X509 certificate , but was found missing"
    }

    return IACAPrincipalName(
        country = country,
        commonName = commonName,
        stateOrProvinceName = name.getStateOrProvinceName(),
        organizationName = name.getOrganizationName(),
    )
}

fun IACAPrincipalName.toJcaX500Name() = buildX500Name(
    country = country,
    commonName = commonName,
    stateOrProvinceName = stateOrProvinceName,
    organizationName = organizationName,
)