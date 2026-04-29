package id.walt.trust.signature

import id.walt.trust.model.AuthenticityState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class XmlDsigValidatorTest {
    
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
        assertEquals(AuthenticityState.VALIDATED, result.state,
            "EU LoTL signature should be valid: ${result.details}")
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
        val deTslXml = fetchUrl("https://www.nrca-ds.de/st/TSL-XML.xml")
        
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
        assertEquals(AuthenticityState.VALIDATED, result.state,
            "German TSL signature should be valid: ${result.details}")
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
        
        assertEquals(AuthenticityState.FAILED, result.state)
        assertTrue(result.details?.contains("No Signature element") == true,
            "Should report missing signature")
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
        assertEquals(AuthenticityState.FAILED, result.state,
            "Tampered document should fail validation")
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
