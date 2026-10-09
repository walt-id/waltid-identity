package id.walt.dcql.jsonld

import id.walt.dcql.DcqlCredential
import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.TrustedAuthoritiesQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun localTermReferenceDoesNotDependOnKeyOrder() {
        val expanded = W3cTypeExpander.expandedTypes(
            json.parseToJsonElement(
                """
                {
                  "@context": {
                    "Degree": "UniversityDegree",
                    "UniversityDegree": "https://example.org/#UniversityDegree"
                  },
                  "type": ["Degree"]
                }
                """.trimIndent()
            ).jsonObject
        )
        assertEquals(setOf("https://example.org/#UniversityDegree"), expanded)
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

    @Test
    fun defaultMatchOverloadUsesBundledGaiaXContext() {
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
        val expandedQuery = query("""[["VerifiableCredential", "$legalPerson"]]""")
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential)).isSuccess)
        val checker: (DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean = { _, _ -> true }
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential), checker).isSuccess)
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential)) { _, _ -> true }.isSuccess)
    }

    @Test
    fun unavailableLaterContextDoesNotKeepThePreviousExpandedIri() {
        val revised = "https://example.org/revised#"
        val credential = RawDcqlCredential(
            id = "legal-person",
            format = "jwt_vc_json",
            data = json.parseToJsonElement(
                """
                {
                  "@context": [
                    "https://www.w3.org/ns/credentials/v2",
                    "https://w3id.org/gaia-x/development",
                    "https://issuer.example/revised"
                  ],
                  "type": ["VerifiableCredential", "gx:LegalPerson"]
                }
                """.trimIndent()
            ).jsonObject,
        )
        val redefined = LayeredJsonLdContextDocuments(
            listOf(
                JsonLdContextDocumentSource.bundled,
                MapJsonLdContextDocuments(
                    mapOf(
                        "https://issuer.example/revised" to json.parseToJsonElement(
                            """{ "@context": { "gx": "$revised" } }"""
                        ).jsonObject,
                    ),
                ),
            ),
        )
        val withRevision = W3cTypeExpander.expandedTypes(credential.data, redefined)
        assertEquals(setOf(verifiableCredential, revised + "LegalPerson"), withRevision)
        assertFalse(legalPerson in withRevision)

        val unresolved = W3cTypeExpander.expandedTypes(credential.data)
        val unresolvedAgain = W3cTypeExpander.expandedTypes(credential.data)
        assertEquals(setOf(verifiableCredential, "gx:LegalPerson"), unresolved)
        assertEquals(unresolved, unresolvedAgain)
        assertFalse(legalPerson in unresolved)

        val expandedQuery = query("""[["$legalPerson"]]""")
        val compactQuery = query("""[["gx:LegalPerson"]]""")
        val protectedQuery = query("""[["$verifiableCredential"]]""")
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential)).isFailure)
        assertTrue(DcqlMatcher.match(compactQuery, listOf(credential)).isSuccess)
        assertTrue(DcqlMatcher.match(protectedQuery, listOf(credential)).isSuccess)
        assertTrue(DcqlMatcher.match(expandedQuery, listOf(credential), contextDocuments = redefined).isFailure)
        assertTrue(
            DcqlMatcher.match(
                query("""[["${revised}LegalPerson"]]"""),
                listOf(credential),
                contextDocuments = redefined,
            ).isSuccess,
        )
    }

    @Test
    fun aliasOfUncertainPrefixDoesNotPublishThePreviousIri() {
        val revised = "https://example.org/revised#"
        val explicit = "https://example.org/explicit#Degree"
        val credential = typedCredential(
            "VerifiableCredential",
            "Degree",
            contexts = listOf(
                "https://www.w3.org/ns/credentials/v2",
                "https://w3id.org/gaia-x/development",
                "https://example.org/review/unresolved",
            ),
            inlineContext = """{ "Degree": "gx:LegalPerson" }""",
        )
        val unresolved = W3cTypeExpander.expandedTypes(credential.data)
        assertEquals(setOf(verifiableCredential, "Degree"), unresolved)
        assertFalse(legalPerson in unresolved)
        assertTrue(DcqlMatcher.match(query("""[["$legalPerson"]]"""), listOf(credential)).isFailure)
        assertTrue(DcqlMatcher.match(query("""[["Degree"]]"""), listOf(credential)).isSuccess)

        val supplied = documentsRedefining(
            "https://example.org/review/unresolved" to """{ "@context": { "gx": "$revised" } }""",
        )
        val withRevision = W3cTypeExpander.expandedTypes(credential.data, supplied)
        assertEquals(setOf(verifiableCredential, revised + "LegalPerson"), withRevision)
        assertFalse(legalPerson in withRevision)
        assertTrue(DcqlMatcher.match(query("""[["$legalPerson"]]"""), listOf(credential), contextDocuments = supplied).isFailure)
        assertTrue(
            DcqlMatcher.match(query("""[["${revised}LegalPerson"]]"""), listOf(credential), contextDocuments = supplied).isSuccess,
        )

        val absolute = typedCredential(
            "Degree",
            contexts = listOf(
                "https://www.w3.org/ns/credentials/v2",
                "https://w3id.org/gaia-x/development",
                "https://example.org/review/unresolved",
            ),
            inlineContext = """{ "Degree": "$explicit" }""",
        )
        assertEquals(setOf(explicit), W3cTypeExpander.expandedTypes(absolute.data))
        assertTrue(DcqlMatcher.match(query("""[["$explicit"]]"""), listOf(absolute)).isSuccess)
    }

    @Test
    fun aliasOfUncertainTermDoesNotPublishThePreviousIri() {
        val previous = "https://example.org/Degree"
        val revised = "https://example.org/OtherDegree"
        val credential = typedCredential(
            "Degree",
            leadingContext = """{ "BaseDegree": "$previous" }""",
            remoteContext = "https://example.org/review/unresolved",
            inlineContext = """{ "Degree": "BaseDegree" }""",
        )
        val unresolved = W3cTypeExpander.expandedTypes(credential.data)
        assertEquals(setOf("Degree"), unresolved)
        assertFalse(previous in unresolved)
        assertTrue(DcqlMatcher.match(query("""[["$previous"]]"""), listOf(credential)).isFailure)
        assertTrue(DcqlMatcher.match(query("""[["Degree"]]"""), listOf(credential)).isSuccess)

        val supplied = documentsRedefining(
            "https://example.org/review/unresolved" to """{ "@context": { "BaseDegree": "$revised" } }""",
        )
        assertEquals(setOf(revised), W3cTypeExpander.expandedTypes(credential.data, supplied))
        assertTrue(DcqlMatcher.match(query("""[["$previous"]]"""), listOf(credential), contextDocuments = supplied).isFailure)
        assertTrue(DcqlMatcher.match(query("""[["$revised"]]"""), listOf(credential), contextDocuments = supplied).isSuccess)
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

    private fun typedCredential(
        vararg types: String,
        contexts: List<String> = emptyList(),
        leadingContext: String? = null,
        remoteContext: String? = null,
        inlineContext: String,
    ): RawDcqlCredential {
        val contextJson = buildList {
            leadingContext?.let { add(it) }
            contexts.forEach { add("\"$it\"") }
            remoteContext?.let { add("\"$it\"") }
            add(inlineContext)
        }.joinToString(",")
        return RawDcqlCredential(
            id = "credential",
            format = "jwt_vc_json",
            data = json.parseToJsonElement(
                """
                {
                  "@context": [$contextJson],
                  "type": [${types.joinToString(",") { "\"$it\"" }}]
                }
                """.trimIndent()
            ).jsonObject,
        )
    }

    private fun documentsRedefining(vararg documents: Pair<String, String>): JsonLdContextDocumentSource =
        LayeredJsonLdContextDocuments(
            listOf(
                JsonLdContextDocumentSource.bundled,
                MapJsonLdContextDocuments(
                    documents.associate { (url, body) -> url to json.parseToJsonElement(body).jsonObject },
                ),
            ),
        )
}
