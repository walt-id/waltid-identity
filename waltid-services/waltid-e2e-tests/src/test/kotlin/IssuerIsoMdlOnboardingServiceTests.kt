import WaltidServicesE2ETests.Companion.testHttpClient
import id.walt.commons.testing.E2ETest
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.services.onboarding.models.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.*
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class IssuerIsoMdlOnboardingServiceTests(private val e2e: E2ETest) {

    private val client = testHttpClient()

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

    suspend fun testOnboardIACARootGeneratesValidCertificate() = e2e.test(
        name = "onboard IACA root generates valid certificate",
    ) {
        val response = client.post("/onboard/iso-mdl/iacas") {
            setBody(iacaOnboardingRequest)
        }.expectSuccess().body<IACAOnboardingResponse>()

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
        assertTrue(cert.basicConstraints == 0) // Is a CA
        assertTrue(cert.keyUsage[5]) // keyCertSign
        assertTrue(cert.keyUsage[6]) // cRLSign
        assertNotNull(cert.issuerAlternativeNames)
        assertTrue(cert.issuerAlternativeNames.size == 1)
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
        assertTrue(distPoints.size == 1)
        val uri = distPoints[0].distributionPoint.name as GeneralNames
        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
        val crlUriValue = (uriName!!.name as DERIA5String).string
        assertEquals(
            expected = iacaOnboardingRequest.certificateData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )
    }

    suspend fun testOnboardDocumentSignerGeneratesValidCertificate() = e2e.test(
        name = "onboard Document Signer generates valid certificate",
    ) {

        val iacaResponse = client.post("/onboard/iso-mdl/iacas") {
            setBody(iacaOnboardingRequest)
        }.expectSuccess().body<IACAOnboardingResponse>()

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

        val response = client.post("/onboard/iso-mdl/document-signers") {
            setBody(dsRequest)
        }.expectSuccess().body<DocumentSignerOnboardingResponse>()

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
        assertTrue(cert.basicConstraints == -1) // not a CA
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
        assertTrue(distPoints.size == 1)
        val uri = distPoints[0].distributionPoint.name as GeneralNames
        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
        val crlUriValue = (uriName!!.name as DERIA5String).string
        assertEquals(
            expected = dsRequest.certificateData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )
    }

    private suspend fun testDSValidityPeriodWithinIACAValidityPeriod() = e2e.test(
        name = "Document Signer validity period must be within IACA validity period",
    ) {

        val timeNow = Clock.System.now()
        val iacaResponse = client.post("/onboard/iso-mdl/iacas") {
            setBody(
                IACAOnboardingRequest(
                    certificateData = IACACertificateData(
                        country = "US",
                        commonName = "Test IACA",
                        issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                        notAfter = timeNow.plus((365L).toDuration(DurationUnit.DAYS)),
                        crlDistributionPointUri = "https://iaca.example.com/crl"
                    )
                )
            )
        }.expectSuccess().body<IACAOnboardingResponse>()

        val iacaSigner = IACASignerData(
            certificateData = IACACertificateData(
                country = "US",
                commonName = "Test IACA",
                issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                notBefore = iacaResponse.certificateData.notBefore,
                notAfter = iacaResponse.certificateData.notAfter,
                crlDistributionPointUri = "https://iaca.example.com/crl"
            ),
            iacaKey = iacaResponse.iacaKey
        )

        // Valid: DS certificate is fully within IACA period
        client.post("/onboard/iso-mdl/document-signers") {
            setBody(
                DocumentSignerOnboardingRequest(
                    iacaSigner = iacaSigner,
                    certificateData = DocumentSignerCertificateData(
                        country = "US",
                        commonName = "Valid DSC",
                        crlDistributionPointUri = "https://iaca.example.com/crl",
                        notBefore = timeNow.plus((30L).toDuration(DurationUnit.DAYS)),
                        notAfter = timeNow.plus((365L).toDuration(DurationUnit.DAYS))
                    )
                )
            )
        }.expectSuccess()

        /**
         * In all of the following invalid request payloads, we use buildJsonObject
         * and not the request payload of the data class because we don't want to get
         * an initialization error from the data class, we want to see that the HTTP
         * call actually fails.
         */

        // Invalid: DS certificate starts before IACA
        client.post("/onboard/iso-mdl/document-signers") {
            setBody(buildJsonObject {
                put("iacaSigner", Json.encodeToJsonElement(iacaSigner))
                put("certificateData", buildJsonObject {
                    put("country", "US".toJsonElement())
                    put("commonName", "Valid DSC".toJsonElement())
                    put("crlDistributionPointUri", "Valid DSC".toJsonElement())
                    put("notBefore", Json.encodeToJsonElement(timeNow.minus((1L).toDuration(DurationUnit.DAYS))))
                    put("notAfter", Json.encodeToJsonElement(timeNow.plus((365L).toDuration(DurationUnit.DAYS))))
                })
            })
        }.expectFailure()

        // Invalid: DS certificate ends after IACA
        client.post("/onboard/iso-mdl/document-signers") {
            setBody(buildJsonObject {
                put("iacaSigner", Json.encodeToJsonElement(iacaSigner))
                put("certificateData", buildJsonObject {
                    put("country", "US".toJsonElement())
                    put("commonName", "Valid DSC".toJsonElement())
                    put("crlDistributionPointUri", "Valid DSC".toJsonElement())
                    put("notBefore", Json.encodeToJsonElement(timeNow.plus((30L).toDuration(DurationUnit.DAYS))))
                    put("notAfter", Json.encodeToJsonElement(timeNow.plus((366L).toDuration(DurationUnit.DAYS))))
                })
            })
        }.expectFailure()

    }

    private suspend fun testDSValidityPeriodLargerThan457Days() = e2e.test(
        name = "Document signer certificate validity period cannot be larger than 457 days"
    ) {

        val timeNow = Clock.System.now()
        val iacaResponse = client.post("/onboard/iso-mdl/iacas") {
            setBody(
                IACAOnboardingRequest(
                    certificateData = IACACertificateData(
                        country = "US",
                        commonName = "Test IACA",
                        issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                        notAfter = timeNow.plus((365L).toDuration(DurationUnit.DAYS)),
                        crlDistributionPointUri = "https://iaca.example.com/crl"
                    )
                )
            )
        }.expectSuccess().body<IACAOnboardingResponse>()

        val iacaSigner = IACASignerData(
            certificateData = IACACertificateData(
                country = "US",
                commonName = "Test IACA",
                issuerAlternativeNameConf = IssuerAlternativeNameConfiguration(uri = "https://iaca.example.com"),
                notBefore = iacaResponse.certificateData.notBefore,
                notAfter = iacaResponse.certificateData.notAfter,
                crlDistributionPointUri = "https://iaca.example.com/crl"
            ),
            iacaKey = iacaResponse.iacaKey
        )

        /**
         * In the following invalid request payload, we use buildJsonObject
         * and not the request payload of the data class because we don't want to get
         * an initialization error from the data class, we want to see that the HTTP
         * call actually fails.
         */

        // Invalid: DS certificate starts before IACA
        client.post("/onboard/iso-mdl/document-signers") {
            setBody(buildJsonObject {
                put("iacaSigner", Json.encodeToJsonElement(iacaSigner))
                put("certificateData", buildJsonObject {
                    put("country", "US".toJsonElement())
                    put("commonName", "Valid DSC".toJsonElement())
                    put("crlDistributionPointUri", "Valid DSC".toJsonElement())
                    put("notBefore", Json.encodeToJsonElement(timeNow.minus((1L).toDuration(DurationUnit.DAYS))))
                    put("notAfter", Json.encodeToJsonElement(timeNow.plus((458L).toDuration(DurationUnit.DAYS))))
                })
            })
        }.expectFailure()
    }

    suspend fun runTests() {
        testOnboardIACARootGeneratesValidCertificate()
        testOnboardDocumentSignerGeneratesValidCertificate()
        testDSValidityPeriodWithinIACAValidityPeriod()
        testDSValidityPeriodLargerThan457Days()
    }
}
