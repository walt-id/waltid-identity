@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso

import com.nimbusds.jose.util.X509CertUtils
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.*
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.*
import kotlin.time.Duration.Companion.days

class DocumentSignerCertificateBuilderTest {

    private val iacaSigningKey = runBlocking {
        KeyManager.createKey(
            generationRequest = KeyGenerationRequest(
                backend = "jwk",
                keyType = KeyType.secp256r1,
            )
        )
    }

    private val iacaValidNotBefore = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    private val iacaValidNotAfter = iacaValidNotBefore.plus((365).toDuration(DurationUnit.DAYS))

    private val dsKey = runBlocking {
        KeyManager.createKey(
            generationRequest = KeyGenerationRequest(
                backend = "jwk",
                keyType = KeyType.secp256r1,
            )
        )
    }

    @Test
    fun `builder generates valid Document Signer certificate`() = runTest {

        val issuerAlternativeName = IssuerAlternativeName(
            uri = "https://ca.example.com",
        )
        val iacaCertBuilder = IACACertificateBuilder(
            profileData = IACACertificateProfileData(
                principalName = IACAPrincipalName(
                    country = "US",
                    commonName = "Example IACA",
                ),
                validityPeriod = CertificateValidityPeriod(
                    notBefore = iacaValidNotBefore,
                    notAfter = iacaValidNotAfter,
                ),
                issuerAlternativeName = issuerAlternativeName,
                crlDistributionPointUri = "https://ca.example.com/crl",
            ),
            signingKey = iacaSigningKey,
        )

        val iacaCertBundle = iacaCertBuilder.build()

        val dsCertBuilder = DocumentSignerCertificateBuilder(
            profileData = DocumentSignerCertificateProfileData(
                principalName = DocumentSignerPrincipalName(
                    country = "US",
                    commonName = "Example DS",
                ),
                validityPeriod = CertificateValidityPeriod(
                    notBefore = iacaValidNotBefore.plus(1.days),
                    notAfter = iacaValidNotAfter.minus(1.days),
                ),
                crlDistributionPointUri = "https://ca.example.com/crl",
            ),
            publicKey = dsKey.getPublicKey(),
            iacaSignerSpec = IACASignerSpecification(
                signingKey = iacaSigningKey,
                profileData = iacaCertBundle.decodedCertData.toIACACertificateProfileData(),
            )
        )

        val dsCertificateBundle = dsCertBuilder.build()

        val cert = X509CertUtils.parse(dsCertificateBundle.certificateDer.bytes)

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
        val expectedOID = KeyPurposeId.getInstance(ASN1ObjectIdentifier(DocumentSignerEkuOid))
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
            expected = dsCertificateBundle.decodedCertData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )

    }
}
