package id.walt.trust.signature

import id.walt.trust.model.AuthenticityState
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslXmlParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Manual verification script to test XMLDSig validation against real EU trust lists.
 * Run with: ./gradlew :waltid-libraries:credentials:waltid-trust-registry:run -PmainClass=id.walt.trust.signature.VerifyEuLotlKt
 */
fun main() {
    println("=".repeat(60))
    println("EU Trust List Signature Verification")
    println("=".repeat(60))
    
    val testUrls = listOf(
        "EU LoTL" to "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
        "German TSL" to "https://www.nrca-ds.de/st/TSL-XML.xml"
    )
    
    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    
    for ((name, url) in testUrls) {
        println("\n${"─".repeat(60)}")
        println("Testing: $name")
        println("URL: $url")
        println("─".repeat(60))
        
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()
            
            println("Fetching...")
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                println("❌ HTTP ${response.statusCode()} - Skipping")
                continue
            }
            
            val xml = response.body()
            println("Downloaded: ${xml.length} bytes")
            
            // First validate just the signature
            println("\nValidating XMLDSig signature...")
            val sigResult = XmlDsigValidator.validate(xml)
            
            println("Signature State: ${sigResult.state}")
            println("Signature Valid: ${sigResult.signatureValid}")
            println("References Valid: ${sigResult.referencesValid}")
            sigResult.details?.let { println("Details: $it") }
            sigResult.warnings.forEach { println("Warning: $it") }
            
            sigResult.signerCertificate?.let { cert ->
                println("\nSigner Certificate:")
                println("  Subject: ${cert.subjectX500Principal}")
                println("  Issuer: ${cert.issuerX500Principal}")
                println("  Valid From: ${cert.notBefore}")
                println("  Valid Until: ${cert.notAfter}")
            }
            
            if (sigResult.state == AuthenticityState.VALIDATED) {
                println("\n✅ SIGNATURE VALID")
            } else {
                println("\n❌ SIGNATURE INVALID")
            }
            
            // Now parse with signature validation
            println("\nParsing TSL with signature validation...")
            val config = TslParseConfig(validateSignature = true)
            val sourceId = name.lowercase().replace(" ", "-")
            val parsed = TslXmlParser.parse(xml, sourceId, url, config)
            
            println("Source ID: ${parsed.source.sourceId}")
            println("Territory: ${parsed.source.territory}")
            println("Sequence: ${parsed.source.sequenceNumber}")
            println("Authenticity: ${parsed.source.authenticityState}")
            println("Entities: ${parsed.entities.size}")
            println("Services: ${parsed.services.size}")
            println("Identities: ${parsed.identities.size}")
            
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    println("\n${"=".repeat(60)}")
    println("Verification complete")
    println("=".repeat(60))
}
