package id.walt.x509.iso.iaca.certificate

import id.walt.x509.buildX500Name
import id.walt.x509.getCommonName
import id.walt.x509.getCountryCode
import id.walt.x509.getOrganizationName
import id.walt.x509.getStateOrProvinceName
import org.bouncycastle.asn1.x500.X500Name

internal fun IACAPrincipalName.Companion.parseFromJcaX500Name(
    name: X500Name,
): IACAPrincipalName {

    val country = requireNotNull(
        name.getCountryCode()
    ) {
        "IACA country code must exist as part of principal name in X509 certificate, but was found missing"
    }

    val commonName = requireNotNull(
        name.getCommonName()
    ) {
        "IACA common name must exist as part of principal name in X509 certificate, but was found missing"
    }

    return IACAPrincipalName(
        country = country,
        commonName = commonName,
        stateOrProvinceName = name.getStateOrProvinceName(),
        organizationName = name.getOrganizationName(),
    )
}

internal fun IACAPrincipalName.toJcaX500Name() = buildX500Name(
    country = country,
    commonName = commonName,
    stateOrProvinceName = stateOrProvinceName,
    organizationName = organizationName,
)