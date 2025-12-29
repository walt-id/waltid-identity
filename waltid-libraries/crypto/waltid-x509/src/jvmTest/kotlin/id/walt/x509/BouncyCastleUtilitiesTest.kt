package id.walt.x509

import id.walt.x509.id.walt.x509.toBCKeyUsage
import id.walt.x509.id.walt.x509.toBouncyCastleKeyUsage
import id.walt.x509.id.walt.x509.toCertificateKeyUsages
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.issuerAlternativeNameToGeneralNameArray
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.KeyUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BouncyCastleUtilitiesTest {

    @Test
    fun certificateKeyUsageToBcKeyUsageSingleRoundTrip() {
        X509KeyUsage.entries.forEach { usage ->
            val bcBit = usage.toBCKeyUsage()
            val converted = KeyUsage(bcBit).toCertificateKeyUsages()
            assertEquals(setOf(usage), converted, "Roundtrip failed for $usage")
        }
    }

    @Test
    fun certificateKeyUsageIterableToBouncyCastleKeyUsageMultipleRoundTrip() {
        val expected = setOf(
            X509KeyUsage.DigitalSignature,
            X509KeyUsage.KeyCertSign,
            X509KeyUsage.CRLSign,
            X509KeyUsage.KeyAgreement,
        )
        val bc = expected.toBouncyCastleKeyUsage()
        val converted = bc.toCertificateKeyUsages()
        assertEquals(expected, converted, "Roundtrip failed for multiple usages")
    }

    @Test
    fun issuerAlternativeNameToGeneralNameArrayHandlesBothEntries() {
        val ian = IssuerAlternativeName(
            uri = "https://example.com/issuer",
            email = "issuer@example.com"
        )
        val generalNames = issuerAlternativeNameToGeneralNameArray(ian)
        val tags = generalNames.map { it.tagNo }.toSet()
        assertEquals(setOf(GeneralName.uniformResourceIdentifier, GeneralName.rfc822Name), tags)
    }

    @Test
    fun issuerAlternativeNameToGeneralNameArrayIgnoresNulls() {
        val ian = IssuerAlternativeName(
            uri = null,
            email = null,
        )
        val generalNames = issuerAlternativeNameToGeneralNameArray(ian)
        assertEquals(0, generalNames.size)
    }

}
