package id.walt.x509

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X500NameExtensionsTest {

    @Test
    fun `getters return null when attributes missing`() {
        val name = X500Name("CN=OnlyCN")

        assertNull(name.getCountryCode())
        assertEquals(
            expected = "OnlyCN",
            actual = name.getCommonName(),
        )
        assertNull(name.getStateOrProvinceName())
        assertNull(name.getOrganizationName())
        assertNull(name.getLocalityName())

    }

    @Test
    fun `getCountryCode returns the first C RDN when duplicates exist`() {
        val name = X500NameBuilder()
            .addRDN(BCStyle.C, "US")
            .addRDN(BCStyle.C, "DE")
            .addRDN(BCStyle.CN, "Example")
            .build()

        assertEquals(
            expected = "US",
            actual = name.getCountryCode(),
        )

    }

    @Test
    fun `getCommonName returns the first CN RDN when duplicates exist`() {
        val name = X500NameBuilder()
            .addRDN(BCStyle.CN, "First")
            .addRDN(BCStyle.CN, "Second")
            .build()

        assertEquals(
            expected = "First",
            actual = name.getCommonName(),
        )

    }

    @Test
    fun `getStateOrProvinceName returns the first ST RDN when duplicates exist`() {
        val name = X500NameBuilder()
            .addRDN(BCStyle.ST, "CA")
            .addRDN(BCStyle.ST, "TX")
            .build()

        assertEquals(
            expected = "CA",
            actual = name.getStateOrProvinceName(),
        )

    }

    @Test
    fun `getOrganizationName returns the first O RDN when duplicates exist`() {
        val name = X500NameBuilder()
            .addRDN(BCStyle.O, "Org1")
            .addRDN(BCStyle.O, "Org2")
            .build()

        assertEquals(
            expected = "Org1",
            actual = name.getOrganizationName(),
        )

    }

    @Test
    fun `getLocalityName returns the first L RDN when duplicates exist`() {
        val name = X500NameBuilder()
            .addRDN(BCStyle.L, "Locality1")
            .addRDN(BCStyle.L, "Locality2")
            .build()

        assertEquals(
            expected = "Locality1",
            actual = name.getLocalityName(),
        )

    }

    @Test
    fun `buildX500Name with all attributes sets all getters`() {
        val name = buildX500Name(
            country = "US",
            commonName = "Example CN",
            stateOrProvinceName = "CA",
            organizationName = "Example Org",
            localityName = "San Francisco",
        )

        assertEquals(
            expected = "US",
            actual = name.getCountryCode(),
        )

        assertEquals(
            expected = "Example CN",
            actual = name.getCommonName(),
        )

        assertEquals(
            expected = "CA",
            actual = name.getStateOrProvinceName(),
        )

        assertEquals(
            expected = "Example Org",
            actual = name.getOrganizationName(),
        )

        assertEquals(
            expected = "San Francisco",
            actual = name.getLocalityName(),
        )

    }

    @Test
    fun `buildX500Name with only mandatory fields leaves optionals null`() {
        val name = buildX500Name(
            country = "US",
            commonName = "Example CN",
        )

        assertEquals(
            expected = "US",
            actual = name.getCountryCode(),
        )

        assertEquals(
            expected = "Example CN",
            actual = name.getCommonName(),
        )

        assertNull(name.getStateOrProvinceName())
        assertNull(name.getOrganizationName())
        assertNull(name.getLocalityName())

    }

    @Test
    fun `buildX500Name with no args yields name with no supported RDNs`() {
        val name = buildX500Name()

        assertNull(name.getCountryCode())
        assertNull(name.getCommonName())
        assertNull(name.getStateOrProvinceName())
        assertNull(name.getOrganizationName())
        assertNull(name.getLocalityName())

    }

    @Test
    fun `buildX500Name includes empty strings as values`() {
        val name = buildX500Name(
            country = "",
            commonName = "",
            stateOrProvinceName = "",
            organizationName = "",
            localityName = "",
        )

        assertEquals(
            expected = "",
            actual = name.getCountryCode(),
        )

        assertEquals(
            expected = "",
            actual = name.getCommonName(),
        )

        assertEquals(
            expected = "",
            actual = name.getStateOrProvinceName(),
        )

        assertEquals(
            expected = "",
            actual = name.getOrganizationName(),
        )

        assertEquals(
            expected = "",
            actual = name.getLocalityName(),
        )

    }

    @Test
    fun `buildX500Name supports non-ASCII characters`() {
        val name = buildX500Name(
            country = "DE",
            commonName = "München",
            stateOrProvinceName = "Bayern",
            organizationName = "Föø Bår GmbH",
            localityName = "Köln",
        )

        assertEquals(
            expected = "DE",
            actual = name.getCountryCode(),
        )

        assertEquals(
            expected = "München",
            actual = name.getCommonName(),
        )

        assertEquals(
            expected = "Bayern",
            actual = name.getStateOrProvinceName(),
        )

        assertEquals(
            expected = "Föø Bår GmbH",
            actual = name.getOrganizationName(),
        )

        assertEquals(
            expected = "Köln",
            actual = name.getLocalityName(),
        )

    }

    @Test
    fun `buildX500Name preserves special characters in values`() {
        val name = buildX500Name(
            country = "US",
            commonName = "CN, With Comma",
            stateOrProvinceName = "ST=Weird",
            organizationName = "Org+Plus",
            localityName = "Local;Semi",
        )

        assertEquals(
            expected = "US",
            actual = name.getCountryCode(),
        )

        assertEquals(
            expected = "CN, With Comma",
            actual = name.getCommonName(),
        )

        assertEquals(
            expected = "ST=Weird",
            actual = name.getStateOrProvinceName(),
        )

        assertEquals(
            expected = "Org+Plus",
            actual = name.getOrganizationName(),
        )

        assertEquals(
            expected = "Local;Semi",
            actual = name.getLocalityName(),
        )

    }

    @Test
    fun `buildX500Name does not add RDNs when null provided`() {
        val name = buildX500Name(
            country = null,
            commonName = "CN",
            stateOrProvinceName = null,
            organizationName = null,
            localityName = null,
        )

        assertEquals(
            expected = 0,
            actual = name.getRDNs(BCStyle.C).size,
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.CN).size,
        )

        assertEquals(
            expected = 0,
            actual = name.getRDNs(BCStyle.ST).size,
        )

        assertEquals(
            expected = 0,
            actual = name.getRDNs(BCStyle.O).size,
        )

        assertEquals(
            expected = 0,
            actual = name.getRDNs(BCStyle.L).size,
        )

    }

    @Test
    fun `buildX500Name adds RDNs for non-null values`() {
        val name = buildX500Name(
            country = "US",
            commonName = "CN",
            stateOrProvinceName = "CA",
            organizationName = "Org",
            localityName = "Local",
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.C).size,
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.CN).size,
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.ST).size,
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.O).size,
        )

        assertEquals(
            expected = 1,
            actual = name.getRDNs(BCStyle.L).size,
        )

    }

    @Test
    fun `buildX500Name adds attributes in requested order`() {
        val name = buildX500Name(
            country = "US",
            commonName = "CN",
            stateOrProvinceName = "CA",
            organizationName = "Org",
            localityName = "Local",
        )

        assertTrue(name.rdNs.size >= 5)

        assertEquals(
            expected = BCStyle.C,
            actual = name.rdNs[0].first.type,
        )

        assertEquals(
            expected = BCStyle.CN,
            actual = name.rdNs[1].first.type,
        )

        assertEquals(
            expected = BCStyle.ST,
            actual = name.rdNs[2].first.type,
        )

        assertEquals(
            expected = BCStyle.O,
            actual = name.rdNs[3].first.type,
        )

        assertEquals(
            expected = BCStyle.L,
            actual = name.rdNs[4].first.type,
        )

    }
}
