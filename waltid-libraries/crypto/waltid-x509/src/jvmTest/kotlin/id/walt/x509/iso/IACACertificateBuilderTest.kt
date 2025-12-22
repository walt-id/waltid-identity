@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso

import com.nimbusds.jose.util.X509CertUtils
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.CertificateKeyUsage
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACADecodedCertificate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.*

class IACACertificateBuilderTest {

    private val signingKey = runBlocking {
        KeyManager.createKey(
            generationRequest = KeyGenerationRequest(
                backend = "jwk",
                keyType = KeyType.secp256r1,
            )
        )
    }

    private val validNotBefore = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    private val validNotAfter = validNotBefore.plus((365).toDuration(DurationUnit.DAYS))

    @Test
    fun `builder generates valid IACA root certificate`() = runTest {
        val issuerAlternativeName = IssuerAlternativeName(
            uri = "https://ca.example.com",
        )
        val iacaCertBuilder = IACACertificateBuilder(
            country = "US",
            commonName = "Example IACA",
            notBefore = validNotBefore,
            notAfter = validNotAfter,
            issuerAlternativeName = issuerAlternativeName,
            signingKey = signingKey,
        ).apply {
            crlDistributionPointUri = "https://ca.example.com/crl"
        }

        val iacaCertBundle = iacaCertBuilder.build()

        assertIACABuilderDataEqualsCertificateData(
            builder = iacaCertBuilder,
            iacaCertData = iacaCertBundle.decodedData,
        )

        val cert = X509CertUtils.parse(iacaCertBundle.certificateDer.bytes)

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

        assertEquals(iacaCertBuilder.country, cert.issuerX500Principal.name.substringAfter("C=").take(2))
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal) // self-signed
        assertEquals(cert.basicConstraints, 0) // Is a CA
        assertTrue(cert.keyUsage[5]) // keyCertSign
        assertTrue(cert.keyUsage[6]) // cRLSign
        assertNotNull(cert.issuerAlternativeNames)
        assertEquals(cert.issuerAlternativeNames.size, 1)
        assertTrue(cert.issuerAlternativeNames.any { it[1] == issuerAlternativeName.uri })

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
            expected = iacaCertBundle.decodedData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )
    }

    private fun assertIACABuilderDataEqualsCertificateData(
        builder: IACACertificateBuilder,
        iacaCertData: IACADecodedCertificate,
    ) {

        assertEquals(
            expected = builder.country,
            actual = iacaCertData.country,
        )

        assertEquals(
            expected = builder.commonName,
            actual = iacaCertData.commonName,
        )

        assertEquals(
            expected = builder.notAfter,
            actual = iacaCertData.notAfter,
        )

        assertEquals(
            expected = builder.notBefore,
            actual = iacaCertData.notBefore,
        )

        assertEquals(
            expected = builder.issuerAlternativeName,
            actual = iacaCertData.issuerAlternativeName,
        )

        assertEquals(
            expected = builder.stateOrProvinceName,
            actual = iacaCertData.stateOrProvinceName,
        )

        assertEquals(
            expected = builder.organizationName,
            actual = iacaCertData.organizationName,
        )

        assertEquals(
            expected = builder.crlDistributionPointUri,
            actual = iacaCertData.crlDistributionPointUri,
        )

        assertEquals(
            expected = true,
            actual = iacaCertData.isCA,
        )

        assertEquals(
            expected = 0,
            actual = iacaCertData.pathLengthConstraint,
        )

        assertEquals(
            expected = setOf(CertificateKeyUsage.CRLSign, CertificateKeyUsage.KeyCertSign),
            actual = iacaCertData.keyUsage,
        )

    }
}