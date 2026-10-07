package id.walt.dcql.jsonld

import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.DcqlMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class W3cTypeExpanderTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val verifiableCredential = "https://www.w3.org/2018/credentials#VerifiableCredential"
    private val universityDegree = "https://example.org/examples#UniversityDegreeCredential"
    private val legalPerson = "https://w3id.org/gaia-x/development#LegalPerson"

    private val examplesContext = json.parseToJsonElement(
        """
        {
          "@context": [
            { "@version": 1.1 },
            {
              "ex": "https://example.org/examples#",
              "UniversityDegreeCredential": "ex:UniversityDegreeCredential",
              "AlumniCredential": "ex:AlumniCredential",
              "BachelorDegree": "ex:BachelorDegree"
            }
          ]
        }
        """.trimIndent()
    ).jsonObject

    private val gaiaXContext = json.parseToJsonElement(
        """
        { "@context": { "gx": "https://w3id.org/gaia-x/development#" } }
        """.trimIndent()
    ).jsonObject

    @Test
    fun verifiableCredentialExpandsThroughVendoredV1Context() {
        val expanded = W3cTypeExpander.expandedTypes(credential("https://www.w3.org/2018/credentials/v1", "VerifiableCredential", "IdentityCredential"))
        assertTrue(verifiableCredential in expanded)
        assertTrue("IdentityCredential" in expanded)
    }

    @Test
    fun examplesContextExpandsCompactTypeToFullIri() {
        val documents = LayeredJsonLdContextDocuments(
            listOf(
                JsonLdContextDocumentSource.bundled,
                MapJsonLdContextDocuments(mapOf("https://www.w3.org/2018/credentials/examples/v1" to examplesContext)),
            )
        )
        val expanded = W3cTypeExpander.expandedTypes(
            credential(
                contexts = listOf(
                    "https://www.w3.org/2018/credentials/v1",
                    "https://www.w3.org/2018/credentials/examples/v1",
                ),
                "VerifiableCredential",
                "UniversityDegreeCredential",
            ),
            documents,
        )
        assertEquals(setOf(verifiableCredential, universityDegree), expanded)
    }

    @Test
    fun prefixAndInlineContextExpandGaiaXType() {
        val documents = MapJsonLdContextDocuments(
            mapOf("https://w3id.org/gaia-x/development" to gaiaXContext),
        )
        val expanded = W3cTypeExpander.expandedTypes(
            credential(
                contexts = listOf("https://w3id.org/gaia-x/development#"),
                "gx:LegalPerson",
            ),
            documents,
        )
        assertEquals(setOf(legalPerson), expanded)

        val inline = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": [{ "gx": "https://w3id.org/gaia-x/development#" }],
                  "type": ["gx:LegalPerson"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf(legalPerson), inline)
    }

    @Test
    fun bundledGaiaXContextExpandsLegalPerson() {
        val expanded = W3cTypeExpander.expandedTypes(
            credential(
                contexts = listOf(
                    "https://www.w3.org/ns/credentials/v2",
                    "https://w3id.org/gaia-x/development#",
                ),
                "VerifiableCredential",
                "gx:LegalPerson",
            ),
        )
        assertEquals(setOf(verifiableCredential, legalPerson), expanded)
    }

    @Test
    fun vocabDoesNotRewriteAnUndefinedIri() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": {
                    "@vocab": "https://w3id.org/gaia-x/development#",
                    "gx": "https://w3id.org/gaia-x/development#"
                  },
                  "type": ["urn:example:Foo", "gx:LegalPerson"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf("urn:example:Foo", legalPerson), expanded)
    }

    @Test
    fun termDefinitionOrderDoesNotChangeExpansion() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": { "Degree": "ex:Degree", "ex": "https://example.org/#" },
                  "type": ["Degree"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf("https://example.org/#Degree"), expanded)
    }

    @Test
    fun objectTermDefinitionIsNotAPrefixUnlessMarked() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": { "ex": { "@id": "https://example.org/examples#" } },
                  "type": ["ex:Foo"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf("ex:Foo"), expanded)
    }

    @Test
    fun nullContextDoesNotDropProtectedBaseTypes() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": ["https://www.w3.org/2018/credentials/v1", null],
                  "type": ["VerifiableCredential"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf(verifiableCredential), expanded)
    }

    @Test
    fun contextDocumentWithoutContextIsIgnored() {
        val documents = MapJsonLdContextDocuments(
            mapOf(
                "https://issuer.example/ctx" to json.parseToJsonElement(
                    """{ "gx": "https://w3id.org/gaia-x/development#" }"""
                ).jsonObject,
            ),
        )
        val expanded = W3cTypeExpander.expandedTypes(
            credential(contexts = listOf("https://issuer.example/ctx"), "gx:LegalPerson"),
            documents,
        )
        assertEquals(setOf("gx:LegalPerson"), expanded)
    }

    @Test
    fun absoluteIriAndUndefinedTermStayUnchanged() {
        val expanded = W3cTypeExpander.expandedTypes(
            credential("https://www.w3.org/2018/credentials/v1", legalPerson, "IdentityCredential"),
        )
        assertTrue(legalPerson in expanded)
        assertTrue("IdentityCredential" in expanded)
    }

    @Test
    fun protectedBaseContextRejectsLaterRedefinition() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": [
                    "https://www.w3.org/2018/credentials/v1",
                    { "VerifiableCredential": "https://evil.example/VerifiableCredential" }
                  ],
                  "type": ["VerifiableCredential"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf(verifiableCredential), expanded)
    }

    @Test
    fun vocabExpandsTermsThatAreNotDefined() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": { "@vocab": "https://example.org/vocab#" },
                  "type": ["UnknownType"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf("https://example.org/vocab#UnknownType"), expanded)
    }

    @Test
    fun dcqlMatchesExpandedIriAndStillMatchesCompactName() {
        val credential = RawDcqlCredential(
            id = "legal-person",
            format = "jwt_vc_json",
            data = json.parseToJsonElement(
                """
                {
                  "@context": [
                    "https://www.w3.org/ns/credentials/v2",
                    "https://w3id.org/gaia-x/development#",
                    { "vcard": "http://www.w3.org/2006/vcard/ns#", "schema": "https://schema.org/" }
                  ],
                  "type": ["VerifiableCredential", "gx:LegalPerson"],
                  "credentialSubject": { "name": "Example Org" }
                }
                """.trimIndent()
            ).jsonObject,
        )
        val documents = LayeredJsonLdContextDocuments(
            listOf(
                JsonLdContextDocumentSource.bundled,
                MapJsonLdContextDocuments(mapOf("https://w3id.org/gaia-x/development" to gaiaXContext)),
            )
        )
        val expandedQuery = query(
            """[["VerifiableCredential", "$legalPerson"]]""",
        )
        val compactQuery = query("""[["gx:LegalPerson"]]""")
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential), contextDocuments = documents).isSuccess)
        assertTrue(DcqlMatcher.match(compactQuery, listOf(credential), contextDocuments = documents).isSuccess)

        val claimsQuery = DcqlParser.parse(
            """
            {
              "credentials": [{
                "id": "credential_1",
                "format": "jwt_vc_json",
                "meta": { "type_values": [["$legalPerson"]] },
                "claims": [{ "path": ["credentialSubject", "name"], "values": ["Example Org"] }]
              }]
            }
            """.trimIndent()
        ).getOrThrow()
        assertTrue(DcqlMatcher.match(claimsQuery, listOf(credential), contextDocuments = documents).isSuccess)
    }

    private fun query(typeValues: String) = DcqlParser.parse(
        """
        {
          "credentials": [{
            "id": "credential_1",
            "format": "jwt_vc_json",
            "meta": { "type_values": $typeValues }
          }]
        }
        """.trimIndent()
    ).getOrThrow()

    private fun credential(vararg types: String): JsonObject =
        credential(contexts = listOf("https://www.w3.org/2018/credentials/v1"), *types)

    private fun credential(contexts: List<String>, vararg types: String): JsonObject = buildJsonObject {
        put("type", buildJsonArray { types.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("@context", buildJsonArray { contexts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
    }
}
