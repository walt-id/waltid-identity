package id.walt.x509.iso.documentsigner

import id.walt.x509.buildX500Name
import id.walt.x509.getCommonName
import id.walt.x509.getCountryCode
import id.walt.x509.getLocalityName
import id.walt.x509.getOrganizationName
import id.walt.x509.getStateOrProvinceName
import id.walt.x509.iso.documentsigner.certificate.parseFromJcaX500Name
import id.walt.x509.iso.documentsigner.certificate.toJcaX500Name
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DocumentSignerPrincipalNameExtensionsTest {

    @Test
    fun `toJcaX500Name includes mandatory and optional fields`() {
        val principal = DocumentSignerPrincipalName(
            country = "US",
            commonName = "Example DS",
            stateOrProvinceName = "CA",
            organizationName = "Example Org",
            localityName = "San Francisco",
        )

        val x500 = principal.toJcaX500Name()

        assertEquals(
            expected = "US",
            actual = x500.getCountryCode(),
        )

        assertEquals(
            expected = "Example DS",
            actual = x500.getCommonName(),
        )

        assertEquals(
            expected = "CA",
            actual = x500.getStateOrProvinceName(),
        )

        assertEquals(
            expected = "Example Org",
            actual = x500.getOrganizationName(),
        )

        assertEquals(
            expected = "San Francisco",
            actual = x500.getLocalityName(),
        )

    }

    @Test
    fun `toJcaX500Name omits null optional fields`() {

        val principal = DocumentSignerPrincipalName(
            country = "US",
            commonName = "Example DS",
            stateOrProvinceName = null,
            organizationName = null,
            localityName = null,
        )

        val x500 = principal.toJcaX500Name()

        assertEquals(
            expected = "US",
            actual = x500.getCountryCode(),
        )

        assertEquals(
            expected = "Example DS",
            actual = x500.getCommonName(),
        )

        assertNull(x500.getStateOrProvinceName())
        assertNull(x500.getOrganizationName())
        assertNull(x500.getLocalityName())

    }

    @Test
    fun `parseFromJcaX500Name parses mandatory and optional fields`() {

        val x500 = buildX500Name(
            country = "US",
            commonName = "Example DS",
            stateOrProvinceName = "CA",
            organizationName = "Example Org",
            localityName = "San Francisco",
        )

        val principal = DocumentSignerPrincipalName.parseFromJcaX500Name(x500)

        assertEquals(
            expected = DocumentSignerPrincipalName(
                country = "US",
                commonName = "Example DS",
                stateOrProvinceName = "CA",
                organizationName = "Example Org",
                localityName = "San Francisco",
            ),
            actual = principal,
        )

    }

    @Test
    fun `parseFromJcaX500Name throws when country is missing`() {

        val x500 = X500Name("CN=Example DS")

        assertFailsWith<IllegalArgumentException> {
            DocumentSignerPrincipalName.parseFromJcaX500Name(x500)
        }

    }

    @Test
    fun `parseFromJcaX500Name throws when commonName is missing`() {

        val x500 = X500Name("C=US")

        assertFailsWith<IllegalArgumentException> {
            DocumentSignerPrincipalName.parseFromJcaX500Name(x500)
        }

    }

    @Test
    fun `parseFromJcaX500Name uses first occurrence when attributes are duplicated`() {

        val x500 = X500NameBuilder()
            .addRDN(BCStyle.C, "US")
            .addRDN(BCStyle.C, "DE")
            .addRDN(BCStyle.CN, "First CN")
            .addRDN(BCStyle.CN, "Second CN")
            .addRDN(BCStyle.ST, "CA")
            .addRDN(BCStyle.ST, "TX")
            .addRDN(BCStyle.O, "Org1")
            .addRDN(BCStyle.O, "Org2")
            .addRDN(BCStyle.L, "Local1")
            .addRDN(BCStyle.L, "Local2")
            .build()

        val principal = DocumentSignerPrincipalName.parseFromJcaX500Name(x500)

        assertEquals(
            expected = "US",
            actual = principal.country,
        )

        assertEquals(
            expected = "First CN",
            actual = principal.commonName,
        )

        assertEquals(
            expected = "CA",
            actual = principal.stateOrProvinceName,
        )

        assertEquals(
            expected = "Org1",
            actual = principal.organizationName,
        )

        assertEquals(
            expected = "Local1",
            actual = principal.localityName,
        )

    }

    @Test
    fun `parseFromJcaX500Name ignores unrelated RDNs`() {

        val x500 = X500NameBuilder()
            .addRDN(BCStyle.C, "US")
            .addRDN(BCStyle.CN, "CN")
            .addRDN(BCStyle.EmailAddress, "ds@example.com")
            .build()

        val principal = DocumentSignerPrincipalName.parseFromJcaX500Name(x500)

        assertEquals(
            expected = DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN",
            ),
            actual = principal,
        )
    }

    @Test
    fun `roundtrip principal to X500Name and back`() {

        val principals = listOf(
            DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN"
            ),
            DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN",
                stateOrProvinceName = "CA"
            ),
            DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN",
                organizationName = "Org"
            ),
            DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN",
                localityName = "Local"
            ),
            DocumentSignerPrincipalName(
                country = "US",
                commonName = "CN",
                stateOrProvinceName = "CA",
                organizationName = "Org",
                localityName = "Local"
            ),
        )

        principals.forEach { original ->
            val parsed = DocumentSignerPrincipalName.parseFromJcaX500Name(original.toJcaX500Name())
            assertEquals(
                expected = original,
                actual = parsed,
            )
        }

    }
}
