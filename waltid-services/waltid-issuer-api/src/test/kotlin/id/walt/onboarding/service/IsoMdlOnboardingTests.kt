@file:OptIn(ExperimentalTime::class)

package id.walt.onboarding.service

import id.walt.issuer.services.onboarding.OnboardingService
import id.walt.issuer.services.onboarding.models.*
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class IsoMdlOnboardingTests {


    private val mdlKeyPurposeDocumentSignerOID = ASN1ObjectIdentifier("1.0.18013.5.1.2")
    private val iacaOnboardingRequest = IACAOnboardingRequest(
        certificateData = IACACertificateData(
            country = "US",
            commonName = "Example IACA",
            issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://ca.example.com"),
            crlDistributionPointUri = "https://ca.example.com/crl"
        )
    )

    private fun parseCertificate(pem: String): X509Certificate {
        val base64 = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\\s".toRegex(), "")
        val derBytes = Base64.getDecoder().decode(base64)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }

    @Test
    fun `onboard IACA root generates valid certificate`() = runTest {

        val response = OnboardingService.onboardIACA(iacaOnboardingRequest)
        val cert = parseCertificate(response.certificatePEM)

        // === Serial Number checks ===
        // 1. Must be positive
        assertTrue(cert.serialNumber.signum() > 0, "Serial number must be positive")

        // 2. Must be non-zero
        assertTrue(cert.serialNumber != BigInteger.ZERO, "Serial number must not be zero")

        // 3. Must be <= 20 octets (160 bits)
        assertTrue(cert.serialNumber.bitLength() <= 160, "Serial number must not exceed 20 bytes (160 bits)")

        // 4. Must contain at least 63 bits (required)
        assertTrue(cert.serialNumber.bitLength() >= 63, "Serial number must contain at least 63 bits of entropy")

        // 5. Should contain at least 71 bits (recommended)
        assertTrue(cert.serialNumber.bitLength() >= 71, "Serial number should contain at least 71 bits of entropy")

        assertEquals("US", cert.issuerX500Principal.name.substringAfter("C=").take(2))
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal) // self-signed
        assertEquals(cert.basicConstraints, 0) // Is a CA
        assertTrue(cert.keyUsage[5]) // keyCertSign
        assertTrue(cert.keyUsage[6]) // cRLSign
        assertNotNull(cert.issuerAlternativeNames)
        assertEquals(cert.issuerAlternativeNames.size, 1)
        assertTrue(cert.issuerAlternativeNames.any { it[1] == iacaOnboardingRequest.certificateData.issuerAlternativeNameConf.uri })

        // === CRL Distribution Point URI check ===
        val crlBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.id)
        val crlOctet = ASN1OctetString.getInstance(crlBytes).octets
        val crlDist = CRLDistPoint.getInstance(ASN1Primitive.fromByteArray(crlOctet))
        val distPoints = crlDist.distributionPoints
        assertTrue(
            actual = distPoints.isNotEmpty(),
            message = "CRL distribution point must be present",
        )
        assertEquals(distPoints.size, 1)
        val uri = distPoints[0].distributionPoint.name as GeneralNames
        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
        val crlUriValue = (uriName!!.name as DERIA5String).string
        assertEquals(
            expected = iacaOnboardingRequest.certificateData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )
    }

    @Test
    fun `onboard Document Signer generates valid certificate`() = runTest {

        val iacaResponse = OnboardingService.onboardIACA(iacaOnboardingRequest)

        val dsRequest = DocumentSignerOnboardingRequest(
            iacaSigner = IACASignerData(
                certificateData = iacaOnboardingRequest.certificateData,
                iacaKey = iacaResponse.iacaKey
            ),
            certificateData = DocumentSignerCertificateData(
                country = "US",
                commonName = "Example DS",
                crlDistributionPointUri = "https://ca.example.com/crl"
            )
        )

        val response = OnboardingService.onboardDocumentSigner(dsRequest)
        val cert = parseCertificate(response.certificatePEM)

        // === Serial Number checks ===
        // 1. Must be positive
        assertTrue(cert.serialNumber.signum() > 0, "Serial number must be positive")

        // 2. Must be non-zero
        assertTrue(cert.serialNumber != BigInteger.ZERO, "Serial number must not be zero")

        // 3. Must be <= 20 octets (160 bits)
        assertTrue(cert.serialNumber.bitLength() <= 160, "Serial number must not exceed 20 bytes (160 bits)")

        // 4. Must contain at least 63 bits (required)
        assertTrue(cert.serialNumber.bitLength() >= 63, "Serial number must contain at least 63 bits of entropy")

        // 5. Should contain at least 71 bits (recommended)
        assertTrue(cert.serialNumber.bitLength() >= 71, "Serial number should contain at least 71 bits of entropy")

        assertEquals("US", cert.subjectX500Principal.name.substringAfter("C=").take(2))
        assertEquals(cert.basicConstraints, -1) // not a CA
        assertTrue(cert.keyUsage[0]) // digitalSignature
        assertFalse(cert.keyUsage[5]) // Not a cert signer

        // === Extended Key Usage check ===
        val ekuBytes = cert.getExtensionValue(Extension.extendedKeyUsage.id)
        val ekuOctet = ASN1OctetString.getInstance(ekuBytes).octets
        val eku = ExtendedKeyUsage.getInstance(ASN1Sequence.fromByteArray(ekuOctet))
        val expectedOID = KeyPurposeId.getInstance(mdlKeyPurposeDocumentSignerOID)
        assertTrue(eku.hasKeyPurposeId(expectedOID))

        // === CRL Distribution Point URI check ===
        val crlBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.id)
        val crlOctet = ASN1OctetString.getInstance(crlBytes).octets
        val crlDist = CRLDistPoint.getInstance(ASN1Primitive.fromByteArray(crlOctet))
        val distPoints = crlDist.distributionPoints
        assertTrue(
            actual = distPoints.isNotEmpty(),
            message = "CRL distribution point must be present",
        )
        assertEquals(distPoints.size, 1)
        val uri = distPoints[0].distributionPoint.name as GeneralNames
        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
        val crlUriValue = (uriName!!.name as DERIA5String).string
        assertEquals(
            expected = dsRequest.certificateData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )
    }
}
