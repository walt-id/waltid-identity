package id.walt.x509.id.walt.x509

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

fun X500Name.getCountryCode(): String? {
    return getRDNs(BCStyle.C).firstOrNull()?.first?.value?.toString()
}

fun X500Name.getCommonName(): String? {
    return getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString()
}

fun X500Name.getStateOrProvinceName(): String? {
    return getRDNs(BCStyle.ST).firstOrNull()?.first?.value?.toString()
}

fun X500Name.getOrganizationName(): String? {
    return getRDNs(BCStyle.O).firstOrNull()?.first?.value?.toString()
}

fun X500Name.getLocalityName(): String? {
    return getRDNs(BCStyle.L).firstOrNull()?.first?.value?.toString()
}

fun buildX500Name(
    country: String? = null,
    commonName: String? = null,
    stateOrProvinceName: String? = null,
    organizationName: String? = null,
    localityName: String? = null,
): X500Name {
    val nameBuilder = X500NameBuilder()

    country?.let {
        nameBuilder.addRDN(BCStyle.C, country)
    }

    commonName?.let {
        nameBuilder.addRDN(BCStyle.CN, commonName)
    }

    stateOrProvinceName?.let {
        nameBuilder.addRDN(BCStyle.ST, it)
    }

    organizationName?.let {
        nameBuilder.addRDN(BCStyle.O, it)
    }

    localityName?.let {
        nameBuilder.addRDN(BCStyle.L, it)
    }

    return nameBuilder.build()
}