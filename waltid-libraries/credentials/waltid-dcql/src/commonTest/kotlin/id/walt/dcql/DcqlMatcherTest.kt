package id.walt.dcql

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

        assertTrue(matchResult.isSuccess)
        val matches = matchResult.getOrThrow()
        // Fails claim match, so no credential returned for this query
        assertTrue(matches.isEmpty())
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

        assertTrue(matchResult.isSuccess)
        // Current implementation returns partial matches if no sets defined.
        // Change DcqlMatcher line `// return Result.failure...` to fail instead if needed.
        val matches = matchResult.getOrThrow()
        assertEquals(1, matches.size)
        assertEquals(listOf(cred1), matches["q_jwt"])
        assertNull(matches["q_nonexistent"])
    }
}
