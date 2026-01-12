package id.walt.x509

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

/**
 * Return the country (C) attribute from this X.500 name, or null when absent.
 */
fun X500Name.getCountryCode(): String? {
    return getRDNs(BCStyle.C).firstOrNull()?.first?.value?.toString()
}

/**
 * Return the common name (CN) attribute from this X.500 name, or null when absent.
 */
fun X500Name.getCommonName(): String? {
    return getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString()
}

/**
 * Return the state or province (ST) attribute from this X.500 name, or null when absent.
 */
fun X500Name.getStateOrProvinceName(): String? {
    return getRDNs(BCStyle.ST).firstOrNull()?.first?.value?.toString()
}

/**
 * Return the organization (O) attribute from this X.500 name, or null when absent.
 */
fun X500Name.getOrganizationName(): String? {
    return getRDNs(BCStyle.O).firstOrNull()?.first?.value?.toString()
}

/**
 * Return the locality (L) attribute from this X.500 name, or null when absent.
 */
fun X500Name.getLocalityName(): String? {
    return getRDNs(BCStyle.L).firstOrNull()?.first?.value?.toString()
}

/**
 * Build an X.500 name from optional attribute values.
 *
 * Only non-null values are included and no additional validation or normalization
 * is performed. The attributes are added in the following order: C, CN, ST, O, L.
 */
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
