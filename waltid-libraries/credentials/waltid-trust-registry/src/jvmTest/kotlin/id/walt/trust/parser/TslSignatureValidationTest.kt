package id.walt.trust.parser

import id.walt.trust.model.*
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslSignatureValidationException
import id.walt.trust.parser.tsl.TslXmlParser
import id.walt.trust.signature.SignatureValidationConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Integration tests for TSL parsing with XMLDSig signature validation.
 * Network-dependent tests require RUN_NETWORK_TESTS=true environment variable.
 */
class TslSignatureValidationTest {
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `parse EU LoTL with signature validation`() = runTest {
        val lotlXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")
        
        if (lotlXml == null) {
            println("Skipping EU LoTL test - could not fetch")
            return@runTest
        }
        
        val config = TslParseConfig(validateSignature = true)
        val result = TslXmlParser.parse(lotlXml, "eu-lotl", "https://ec.europa.eu/tools/lotl/eu-lotl.xml", config)
        
        println("EU LoTL Parse Result:")
        println("  Source ID: ${result.source.sourceId}")
        println("  Territory: ${result.source.territory}")
        println("  Sequence Number: ${result.source.sequenceNumber}")
        println("  Authenticity: ${result.source.authenticityState}")
        println("  Entities: ${result.entities.size}")
        println("  Services: ${result.services.size}")
        println("  Signer: ${result.signerCertificate?.subjectX500Principal}")
        
        // EU LoTL should validate successfully
        assertEquals(AuthenticityState.VALIDATED, result.source.authenticityState,
            "EU LoTL should have valid signature")
        assertNotNull(result.signatureValidation, "Should have signature validation result")
        assertTrue(result.signatureValidation!!.signatureValid, "Signature should be valid")
        assertTrue(result.signatureValidation!!.referencesValid, "References should be valid")
        
        // Note: The EU LoTL is a List of Trusted Lists, not a regular trust list.
        // It contains pointers to member state TLs, not TSPs directly.
        // The parser returns 0 entities because LoTL has a different structure (OtherTSLPointer elements).
        // This is expected behavior - LoTL processing requires separate logic.
        println("Note: LoTL contains pointers, not TSPs. Entities: ${result.entities.size}")
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `parse German TSL with signature validation`() = runTest {
        val deTslXml = fetchUrl("https://www.nrca-ds.de/st/TSL-XML.xml")
        
        if (deTslXml == null) {
            println("Skipping German TSL test - could not fetch")
            return@runTest
        }
        
        val config = TslParseConfig(validateSignature = true)
        val result = TslXmlParser.parse(deTslXml, "de-tsl", "https://www.nrca-ds.de/st/TSL-XML.xml", config)
        
        println("German TSL Parse Result:")
        println("  Source ID: ${result.source.sourceId}")
        println("  Territory: ${result.source.territory}")
        println("  Authenticity: ${result.source.authenticityState}")
        println("  Entities: ${result.entities.size}")
        println("  Services: ${result.services.size}")
        
        assertEquals(AuthenticityState.VALIDATED, result.source.authenticityState,
            "German TSL should have valid signature")
        assertEquals("DE", result.source.territory, "Should identify as German TSL")
    }
    
    @Test
    fun `parse without signature validation`() {
        val xml = javaClass.classLoader.getResource("sample-tl.xml")?.readText()
            ?: error("Test resource not found: sample-tl.xml")
        
        val config = TslParseConfig(validateSignature = false)
        val result = TslXmlParser.parse(xml, "test-no-sig", config = config)
        
        assertEquals(AuthenticityState.SKIPPED_DEMO, result.source.authenticityState,
            "Should skip signature validation when disabled")
        assertNull(result.signatureValidation, "Should not have signature validation result")
        
        // Content should still be parsed
        assertEquals("AT", result.source.territory)
        assertEquals(1, result.entities.size)
    }
    
    @Test
    fun `strict mode rejects unsigned TSL`() {
        val xml = javaClass.classLoader.getResource("sample-tl.xml")?.readText()
            ?: error("Test resource not found: sample-tl.xml")
        
        val config = TslParseConfig(
            validateSignature = true,
            strictSignatureValidation = true
        )
        
        val exception = assertThrows<TslSignatureValidationException> {
            TslXmlParser.parse(xml, "test-strict", config = config)
        }
        
        println("Expected exception: ${exception.message}")
        assertNotNull(exception.validationResult, "Should include validation result")
        assertEquals(AuthenticityState.FAILED, exception.validationResult?.state)
    }
    
    @Test
    fun `lenient mode parses unsigned TSL with FAILED state`() {
        val xml = javaClass.classLoader.getResource("sample-tl.xml")?.readText()
            ?: error("Test resource not found: sample-tl.xml")
        
        val config = TslParseConfig(
            validateSignature = true,
            strictSignatureValidation = false // lenient
        )
        
        val result = TslXmlParser.parse(xml, "test-lenient", config = config)
        
        // Should parse but mark as failed
        assertEquals(AuthenticityState.FAILED, result.source.authenticityState,
            "Should mark as FAILED when signature validation fails in lenient mode")
        
        // Content should still be parsed
        assertEquals("AT", result.source.territory)
        assertEquals(1, result.entities.size)
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `detect tampered content`() = runTest {
        val lotlXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")
        
        if (lotlXml == null) {
            println("Skipping tamper detection test - could not fetch")
            return@runTest
        }
        
        // Tamper with the content
        val tamperedXml = lotlXml.replace(
            Regex("<TSLSequenceNumber>\\d+</TSLSequenceNumber>"),
            "<TSLSequenceNumber>99999</TSLSequenceNumber>"
        )
        
        val config = TslParseConfig(
            validateSignature = true,
            strictSignatureValidation = false
        )
        
        val result = TslXmlParser.parse(tamperedXml, "tampered", config = config)
        
        assertEquals(AuthenticityState.FAILED, result.source.authenticityState,
            "Tampered content should fail signature validation")
        
        // The (wrong) sequence number should still be parsed
        assertEquals("99999", result.source.sequenceNumber)
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `signer certificate metadata is stored`() = runTest {
        val lotlXml = fetchUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml")
        
        if (lotlXml == null) {
            println("Skipping metadata test - could not fetch")
            return@runTest
        }
        
        val config = TslParseConfig(validateSignature = true)
        val result = TslXmlParser.parse(lotlXml, "eu-lotl-meta", config = config)
        
        // Check that signer metadata is stored in source metadata
        assertTrue(result.source.metadata.containsKey("signerSubjectDN"),
            "Should store signer subject DN in metadata")
        assertTrue(result.source.metadata.containsKey("signerIssuerDN"),
            "Should store signer issuer DN in metadata")
        
        println("Signer metadata:")
        result.source.metadata.forEach { (k, v) ->
            println("  $k: $v")
        }
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
