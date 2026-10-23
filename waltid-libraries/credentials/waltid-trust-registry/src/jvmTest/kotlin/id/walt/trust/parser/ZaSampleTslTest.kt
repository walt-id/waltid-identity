package id.walt.trust.parser

import id.walt.trust.TestCertificates
import id.walt.trust.model.AuthenticityState
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslXmlParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parses a synthetic non-EU trust list with DFID extension shapes.
 * The parser preserves service types; it does not enforce DFID authorization policies.
 */
class ZaSampleTslTest {

    @Test
    fun `parse ZA DFID trust list sample`() {
        val fixture = assertNotNull(
            javaClass.classLoader.getResource("za-dfid-trust-list.synthetic.xml"),
            "Required test resource: za-dfid-trust-list.synthetic.xml"
        ).readText()
        val certificate = TestCertificates.derBase64(
            TestCertificates.createChain("Synthetic ZA DFID parser fixture").leaf
        )
        val xmlContent = fixture.replace("__TEST_CERTIFICATE_BASE64__", certificate)

        // The synthetic signature is a placeholder; this case covers content parsing.
        val config = TslParseConfig(validateSignature = false)
        val result = TslXmlParser.parse(
            xml = xmlContent,
            sourceId = "za-dfid-sample",
            sourceUrl = "file:///sample/trust_list_structure_xml.xml",
            config = config
        )

        assertEquals("ZA", result.source.territory)
        assertEquals(AuthenticityState.UNVERIFIED, result.source.assurance.authenticityState)
        assertTrue(result.entities.isNotEmpty(), "Should have entities")
        val tspNames = result.entities.map { it.legalName }
        assertTrue(tspNames.contains("Department of Home Affairs"), "Should have DHA")
        assertTrue(tspNames.contains("Example Bank Ltd"), "Should have Example Bank")
        assertTrue(result.services.isNotEmpty(), "Should have services")
        assertTrue(result.identities.isNotEmpty(), "Should have identities")
        assertEquals(
            "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/nonEUgeneric",
            result.source.metadata["tslType"]
        )
        assertEquals(
            setOf(
                "urn:example:za:service:mdoc-document-signer",
                "urn:example:za:service:sd-jwt-issuer",
                "urn:example:za:service:verifier",
                "urn:example:za:service:wallet-provider"
            ),
            result.services.map { it.serviceType }.toSet()
        )
    }
}
