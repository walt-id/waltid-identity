package id.walt.trust.signature

import id.walt.trust.TestCertificates
import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.SignatureStatus
import id.walt.trust.model.SignerTrust
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.Instant
import java.util.Date

class XmlDsigValidatorTest {

    private val sampleTl: String
        get() = javaClass.classLoader.getResource("sample-tl.xml")?.readText()
            ?: error("Test resource not found: sample-tl.xml")

    /**
     * A signer certificate whose SubjectPublicKeyInfo uses the explicit
     * id-RSASSA-PSS OID (as Germany's BNetzA trusted list uses) reports its JCA key
     * algorithm as "RSASSA-PSS" rather than "RSA". The KeySelector must still accept it
     * for an RSA-family SignatureMethod, or every reference/signature check fails with
     * "Cannot find validation key" before the acceptance policy is even evaluated.
     */
    @Test
    fun `validate RSASSA-PSS signature from a PSS-only certificate`() {
        val signer = TestCertificates.createRsaPssSelfSigned()
        val signedXml = XmlDsigTestSigner.signEnveloped(
            xml = sampleTl,
            certificate = signer.certificate,
            privateKey = signer.keyPair.private,
            signatureMethodUri = XmlDsigTestSigner.SHA256_RSA_MGF1
        )

        assertEquals(
            "RSASSA-PSS", signer.certificate.publicKey.algorithm,
            "Sanity check: fixture certificate must use the id-RSASSA-PSS SPKI OID, matching the reported bug"
        )

        val result = XmlDsigValidator.validate(signedXml)

        assertEquals(AuthenticityState.INTEGRITY_VERIFIED, result.state, "Details: ${result.details}")
        assertEquals(SignatureStatus.VALID, result.signatureStatus)
        assertTrue(result.signatureValid, "SignatureValue should validate")
        assertTrue(result.referencesValid, "References should validate")
        assertEquals(signer.certificate, result.signerCertificate)
    }

    /**
     * Same as above, but under the stricter policy that requires the signer to be an
     * explicitly trusted anchor (REQUIRE_AUTHENTICATED in the trust-registry service).
     */
    @Test
    fun `validate RSASSA-PSS signature against a trusted anchor`() {
        val signer = TestCertificates.createRsaPssSelfSigned()
        val signedXml = XmlDsigTestSigner.signEnveloped(
            xml = sampleTl,
            certificate = signer.certificate,
            privateKey = signer.keyPair.private,
            signatureMethodUri = XmlDsigTestSigner.SHA256_RSA_MGF1
        )

        val result = XmlDsigValidator.validate(
            signedXml,
            SignatureValidationConfig(
                requireTrustedCertificate = true,
                trustedAnchors = setOf(signer.certificate)
            )
        )

        assertEquals(AuthenticityState.AUTHENTICATED, result.state, "Details: ${result.details}")
        assertEquals(SignatureStatus.VALID, result.signatureStatus)
        assertEquals(SignerTrust.TRUSTED, result.signerTrust)
    }

    /**
     * Regression guard: an ordinary RSA certificate (rsaEncryption SPKI, the common case)
     * used with a PSS *signature* must keep working exactly as before.
     */
    @Test
    fun `validate RSASSA-PSS signature from a plain RSA certificate`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val subject = X500Name("CN=Plain RSA Signer,O=walt.id,C=AT")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(128, SecureRandom()),
            Date.from(Instant.now().minusSeconds(60)),
            Date.from(Instant.now().plusSeconds(86_400)),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSAandMGF1")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        assertEquals("RSA", certificate.publicKey.algorithm)

        val signedXml = XmlDsigTestSigner.signEnveloped(
            xml = sampleTl,
            certificate = certificate,
            privateKey = keyPair.private,
            signatureMethodUri = XmlDsigTestSigner.SHA256_RSA_MGF1
        )

        val result = XmlDsigValidator.validate(signedXml)

        assertEquals(AuthenticityState.INTEGRITY_VERIFIED, result.state, "Details: ${result.details}")
    }

    /**
     * An ECDSA SignatureMethod URI (`xmldsig-more#ecdsa-*`) also contains "dsa", so the key selector
     * used to take it for DSA, reject the EC certificate, and fail with "cannot find validation key" -
     * the same symptom as the RSASSA-PSS case above, for every ECDSA-signed trust list.
     */
    @Test
    fun `validate ECDSA signature from an EC certificate`() {
        val chain = TestCertificates.createChain("ECDSA Trust List Signer")
        val signedXml = XmlDsigTestSigner.signEnveloped(
            xml = sampleTl,
            certificate = chain.leaf,
            privateKey = chain.leafKeyPair.private,
            signatureMethodUri = XmlDsigTestSigner.ECDSA_SHA256
        )

        val result = XmlDsigValidator.validate(signedXml)

        assertEquals(AuthenticityState.INTEGRITY_VERIFIED, result.state, "Details: ${result.details}")
        assertEquals(chain.leaf, result.signerCertificate)
    }

    /**
     * Integration test that validates the real EU LoTL signature.
     * Requires network access - enable by setting RUN_NETWORK_TESTS=true
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `validate EU LoTL signature`() = runTest {
        // Fetch the real EU List of Trusted Lists
        val lotlXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")

        assertNotNull(lotlXml, "Failed to fetch EU LoTL")
        assertTrue(lotlXml!!.contains("<TrustServiceStatusList"), "Not a valid TSL XML")

        // Validate signature
        val result = XmlDsigValidator.validate(lotlXml)

        println("EU LoTL Signature Validation Result:")
        println("  State: ${result.state}")
        println("  Signature Valid: ${result.signatureValid}")
        println("  References Valid: ${result.referencesValid}")
        println("  Details: ${result.details}")
        result.warnings.forEach { println("  Warning: $it") }
        result.signerCertificate?.let { cert ->
            println("  Signer Subject: ${cert.subjectX500Principal}")
            println("  Signer Issuer: ${cert.issuerX500Principal}")
            println("  Signer Valid From: ${cert.notBefore}")
            println("  Signer Valid To: ${cert.notAfter}")
        }

        // The EU LoTL should have a valid signature
        assertEquals(
            AuthenticityState.INTEGRITY_VERIFIED, result.state,
            "EU LoTL signature should be valid: ${result.details}"
        )
        assertTrue(result.signatureValid, "Signature value should be valid")
        assertTrue(result.referencesValid, "All references should be valid")
        assertNotNull(result.signerCertificate, "Should extract signer certificate")
    }

    /**
     * Integration test that validates the German national TSL signature.
     * Requires network access - enable by setting RUN_NETWORK_TESTS=true
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `validate German national trust list signature`() = runTest {
        // Fetch the German national trust list
        val deTslXml = fetchUrl("https://tl.bundesnetzagentur.de/TL-DE.xml")

        if (deTslXml == null) {
            println("Skipping German TSL test - could not fetch")
            return@runTest
        }

        val result = XmlDsigValidator.validate(deTslXml)

        println("German TSL Signature Validation Result:")
        println("  State: ${result.state}")
        println("  Signature Valid: ${result.signatureValid}")
        println("  References Valid: ${result.referencesValid}")
        println("  Details: ${result.details}")

        // German TSL should also have a valid signature
        assertEquals(
            AuthenticityState.INTEGRITY_VERIFIED, result.state,
            "German TSL signature should be valid: ${result.details}"
        )
    }

    @Test
    fun `reject document without signature`() {
        val xmlWithoutSignature = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
                <SchemeInformation>
                    <TSLSequenceNumber>1</TSLSequenceNumber>
                </SchemeInformation>
            </TrustServiceStatusList>
        """.trimIndent()

        val result = XmlDsigValidator.validate(xmlWithoutSignature)

        assertEquals(AuthenticityState.UNVERIFIED, result.state)
        assertEquals(SignatureStatus.NOT_PRESENT, result.signatureStatus)
        assertTrue(
            result.details?.contains("No Signature element") == true,
            "Should report missing signature"
        )
    }

    /**
     * Test that tampered content is detected.
     * Uses cached XML to avoid network dependency.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `reject document with tampered content`() = runTest {
        val lotlXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")

        if (lotlXml == null) {
            println("Skipping tamper test - could not fetch")
            return@runTest
        }

        // Tamper with the document by changing the sequence number
        val tamperedXml = lotlXml.replace(
            Regex("<TSLSequenceNumber>\\d+</TSLSequenceNumber>"),
            "<TSLSequenceNumber>99999</TSLSequenceNumber>"
        )

        val result = XmlDsigValidator.validate(tamperedXml)

        println("Tampered XML Signature Validation Result:")
        println("  State: ${result.state}")
        println("  Details: ${result.details}")

        // Tampered document should fail validation
        assertEquals(
            AuthenticityState.FAILED, result.state,
            "Tampered document should fail validation"
        )
    }

    @Test
    fun `handle malformed XML gracefully`() {
        val malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
                <SchemeInformation>
                    <TSLSequenceNumber>1
                <!-- missing closing tags -->
        """.trimIndent()

        val result = XmlDsigValidator.validate(malformedXml)

        assertEquals(AuthenticityState.FAILED, result.state)
        assertNotNull(result.details, "Should provide error details")
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) response.body() else null
        } catch (e: Exception) {
            println("Failed to fetch $url: ${e.message}")
            null
        }
    }
}
