package id.walt.dcql

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DcqlMatcherTest {

    // Sample Credentials
    private val cred1 = RawDcqlCredential(
        id = "cred-jwt-vc-1",
        format = "jwt_vc_json",
//        issuer = "did:example:issuer1",
        data = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential"); add("IDCard") })
            putJsonObject("credentialSubject") {
                put("given_name", "Alice")
                put("family_name", "Smith")
                putJsonObject("address") {
                    put("street_address", "123 Main St")
                    put("locality", "Anytown")
                }
            }
        }
    )
    private val cred2 = RawDcqlCredential(
        id = "cred-jwt-vc-2",
        format = "jwt_vc_json",
//        issuer = "did:example:issuer2",
        data = buildJsonObject {
            put("type", buildJsonArray { add("VerifiableCredential"); add("MembershipCard") })
            putJsonObject("credentialSubject") {
                put("given_name", "Bob")
                put("membership_level", "Gold")
            }
        }
    )
    private val cred3_mdoc = RawDcqlCredential(
        id = "cred-mdoc-3",
        format = "mso_mdoc",
//        issuer = "did:example:issuer_mdoc",
        data = buildJsonObject {
            put("doctype", "org.iso.18013.5.1.mDL") // Example meta field
            putJsonObject("org.iso.18013.5.1") { // Example namespace
                put("given_name", "Charlie")
                put("family_name", "Brown")
            }
        }
    )

    private val allCreds = listOf(cred1, cred2, cred3_mdoc)

    fun parseAndMatch(queryJson: String): Result<Map<String, List<DcqlCredential>>> {
        println("Query: $queryJson")

        val queryResult = DcqlParser.parse(queryJson)
        assertTrue(queryResult.isSuccess)
        val query = queryResult.getOrThrow()

        val matchResult = DcqlMatcher.match(query, allCreds)
        println("Match result: $matchResult")
        return matchResult.map { it.mapValues { it.value.map { it.credential } } }
    }

    @Test
    fun testSimpleFormatMatch() {
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                { "id": "query1", "format": "jwt_vc_json", "meta": {} }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        assertEquals(1, matches.size)
        assertTrue(matches.containsKey("query1"))
        assertEquals(listOf(cred1), matches["query1"]) // multiple=false, takes first
    }

    @Test
    fun testMultipleFormatMatch() {
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                { "id": "query1", "format": "jwt_vc_json", "meta": {}, "multiple": true }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        assertEquals(1, matches.size)
        assertTrue(matches.containsKey("query1"))
        assertEquals(listOf(cred1, cred2), matches["query1"]) // multiple=true, takes all
    }

    @Test
    fun testClaimMatch() {
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                {
                  "id": "id_card_query",
                  "format": "jwt_vc_json",
                  "meta": {},
                  "claims": [
                    { "path": ["credentialSubject", "given_name"] },
                    { "path": ["credentialSubject", "family_name"], "values": ["Smith"] }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        assertEquals(1, matches.size)
        assertEquals(listOf(cred1), matches["id_card_query"])
    }

    @Test
    fun testClaimMismatchValue() {
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                {
                  "id": "id_card_query",
                  "format": "jwt_vc_json",
                  "meta": {},
                  "claims": [
                     { "path": ["credentialSubject", "family_name"], "values": ["Jones"] }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isFailure)
        assertIs<DcqlMatchException>(matchResult.exceptionOrNull())
    }

    @Test
    fun testClaimSetMatch() {
        // Cred1 matches both sets, should return cred1
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                {
                  "id": "addr_query",
                  "format": "jwt_vc_json",
                  "meta": {},
                  "claims": [
                    { "id": "gn", "path": ["credentialSubject", "given_name"] },
                    { "id": "sn", "path": ["credentialSubject", "family_name"] },
                    { "id": "street", "path": ["credentialSubject", "address", "street_address"] },
                    { "id": "city", "path": ["credentialSubject", "address", "locality"] }
                  ],
                  "claim_sets": [
                     ["gn", "sn"],
                     ["street", "city"]
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        assertEquals(1, matches.size)
        assertEquals(listOf(cred1), matches["addr_query"])
    }


    @Test
    fun testRequiredCredentialSetFail() {
        // We match q_jwt and q_mdoc individually, but the required set needs q_other which won't match.
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                { "id": "q_jwt", "format": "jwt_vc_json", "meta": {} },
                { "id": "q_mdoc", "format": "mso_mdoc", "meta": {} }
              ],
              "credential_sets": [
                {
                  "required": true,
                  "options": [ ["q_jwt", "q_other"] ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isFailure) // Fails because the required set isn't met
        assertTrue(matchResult.exceptionOrNull() is DcqlMatchException)
        assertEquals("Required credential set constraints not met.", matchResult.exceptionOrNull()?.message)
    }

    @Test
    fun testOptionalCredentialSet() {
        // q_jwt matches cred1, q_mdoc matches cred3_mdoc.
        // The optional set isn't met, but the required one is.
        val matchResult = parseAndMatch(
            /*
            credential_sets.required = false -> Optional set
            credential_sets.required = true -> Required set that IS met
             */
            // language=json
            """
            {
              "credentials": [
                { "id": "q_jwt", "format": "jwt_vc_json", "meta": {} },
                { "id": "q_mdoc", "format": "mso_mdoc", "meta": {} }
              ],
              "credential_sets": [
                {
                  "required": false,
                  "options": [ ["q_jwt", "q_other"] ]
                },
                 {
                  "required": true,
                  "options": [ ["q_mdoc"] ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        assertEquals(2, matches.size) // Both individual queries matched
        assertEquals(listOf(cred1), matches["q_jwt"])
        assertEquals(listOf(cred3_mdoc), matches["q_mdoc"])
    }

    @Test
    fun testNoCredentialSetMatchImpliesAllRequired() {
        // q_jwt matches, q_nonexistent doesn't. Since no sets, both are required.
        val matchResult = parseAndMatch( // language=json
            """
            {
              "credentials": [
                { "id": "q_jwt", "format": "jwt_vc_json", "meta": {} },
                { "id": "q_nonexistent", "format": "ac_vp", "meta": {} }
              ]
            }
            """.trimIndent()
        )

        assertTrue(matchResult.isFailure)
        assertTrue(matchResult.exceptionOrNull()?.message.orEmpty().contains("q_nonexistent"))
    }

    @Test
    fun noCredentialSetsRequiresAllQueriesAtomically() {
        val query = DcqlParser.parse(
            """
            {
              "credentials": [
                { "id": "identity", "format": "jwt_vc_json", "meta": {} },
                { "id": "mdoc", "format": "mso_mdoc", "meta": {} }
              ]
            }
            """.trimIndent()
        ).getOrThrow()

        assertTrue(DcqlMatcher.match(query, emptyList()).isFailure)
        assertTrue(DcqlMatcher.match(query, listOf(cred1)).isFailure)
        val complete = DcqlMatcher.match(query, listOf(cred1, cred3_mdoc)).getOrThrow()
        assertEquals(setOf("identity", "mdoc"), complete.keys)
    }

    @Test
    fun sdJwtDisclosureMatchingUsesCompleteClaimPath() {
        val customerName = DcqlDisclosure(
            name = "name",
            value = JsonPrimitive("Customer Name"),
            location = listOf(JsonPrimitive("customer"), JsonPrimitive("name")),
        )
        val employeeName = DcqlDisclosure(
            name = "name",
            value = JsonPrimitive("Employee Name"),
            location = listOf(JsonPrimitive("employee"), JsonPrimitive("name")),
        )
        val credential = RawDcqlCredential(
            id = "nested-sd-jwt",
            format = "dc+sd-jwt",
            data = buildJsonObject { put("vct", "https://issuer.example/employee") },
            disclosures = listOf(customerName, employeeName),
        )
        val query = DcqlParser.parse(
            """
            {
              "credentials": [{
                "id": "employee",
                "format": "dc+sd-jwt",
                "meta": { "vct_values": ["https://issuer.example/employee"] },
                "claims": [{ "path": ["employee", "name"], "values": ["Employee Name"] }]
              }]
            }
            """.trimIndent()
        ).getOrThrow()

        val selected = DcqlMatcher.match(query, listOf(credential)).getOrThrow()
            .getValue("employee").single().selectedDisclosures.orEmpty().values.single()
        assertEquals(employeeName, selected)
    }

    @Test
    fun sdJwtDisclosureMatchingUsesExactClaimsPathPointerSemantics() {
        val objectPropertyDisclosure = DcqlDisclosure(
            name = "name",
            value = JsonPrimitive("Alice"),
            location = listOf(JsonPrimitive("name")),
        )
        val privateSsnDisclosure = DcqlDisclosure(
            name = "ssn",
            value = JsonPrimitive("123-45-6789"),
            location = listOf(JsonPrimitive("address"), JsonPrimitive("private"), JsonPrimitive("ssn")),
        )
        val arrayDisclosure = DcqlDisclosure(
            name = "name",
            value = JsonPrimitive("Alice"),
            location = listOf(JsonPrimitive("people"), JsonPrimitive(0), JsonPrimitive("name")),
        )

        assertFalse(
            DcqlMatcher.run {
                objectPropertyDisclosure.matchesPath(listOf(JsonPrimitive("$"), JsonPrimitive("name")))
            },
            "A leading dollar sign is an ordinary property name, not a root marker",
        )
        assertFalse(
            DcqlMatcher.run {
                privateSsnDisclosure.matchesPath(
                    listOf(JsonPrimitive("address"), JsonNull, JsonPrimitive("ssn"))
                )
            },
            "The null wildcard must not match an object-property segment",
        )
        assertTrue(
            DcqlMatcher.run {
                arrayDisclosure.matchesPath(listOf(JsonPrimitive("people"), JsonNull, JsonPrimitive("name")))
            },
            "The null wildcard matches a non-negative array index",
        )
    }

    @Test
    fun trustedAuthoritiesFailsClosedWithoutChecker() {
        val query = DcqlParser.parse(
            """
            {
              "credentials": [{
                "id": "trusted",
                "format": "jwt_vc_json",
                "meta": {},
                "trusted_authorities": [{ "type": "aki", "values": ["authority-key-id"] }]
              }]
            }
            """.trimIndent()
        ).getOrThrow()

        assertTrue(DcqlMatcher.match(query, listOf(cred1)).isFailure)
        assertTrue(DcqlMatcher.match(query, listOf(cred1)) { _, _ -> true }.isSuccess)
    }
}
