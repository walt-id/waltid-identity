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
    fun findMatchesOmitsMissingQueriesWhileMatchEnforcesSatisfaction() {
        val query = DcqlParser.parse(
            """
            {
              "credentials": [
                { "id": "q_jwt", "format": "jwt_vc_json", "meta": {} },
                { "id": "q_sdjwt", "format": "dc+sd-jwt", "meta": {} }
              ]
            }
            """.trimIndent()
        ).getOrThrow()

        val discovered = DcqlMatcher.findMatches(query, allCreds).getOrThrow()
        assertEquals(setOf("q_jwt"), discovered.keys)
        assertEquals(listOf(cred1.id), discovered.getValue("q_jwt").map { it.credential.id })

        val matchResult = DcqlMatcher.match(query, allCreds)
        assertTrue(matchResult.isFailure)
        val unavailable = assertIs<RequiredCredentialUnavailableException>(matchResult.exceptionOrNull())
        assertEquals(listOf("q_sdjwt"), unavailable.queryIds)
    }

    @Test
    fun findMatchesKeepsAnAvailableCredentialSetAlternative() {
        val query = DcqlParser.parse(
            """
            {
              "credentials": [
                { "id": "q_jwt", "format": "jwt_vc_json", "meta": {} },
                { "id": "q_sdjwt", "format": "dc+sd-jwt", "meta": {} }
              ],
              "credential_sets": [
                { "required": true, "options": [["q_jwt"], ["q_sdjwt"]] }
              ]
            }
            """.trimIndent()
        ).getOrThrow()

        val discovered = DcqlMatcher.findMatches(query, allCreds).getOrThrow()
        assertEquals(setOf("q_jwt"), discovered.keys)
        assertTrue(DcqlMatcher.match(query, allCreds).isSuccess)
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

    // SD-JWT claim selection over recursive disclosures (OpenID4VP §7.1.1, RFC 9901 §4.2.6): a claims path
    // releases the disclosures of the claims it selects plus those containing them, never their descendants.


    // nationalities: ["IN", {kind, country, since, documents: ["passport", "visa"]}, "FR"]; everything SD except kind
    private val nestedSdJwtData = Json.parseToJsonElement(
        """
        {
          "vct": "https://issuer.example/pid",
          "given_name": "John",
          "nationalities": ["IN", {"kind": "residency", "country": "DE", "since": 2015, "documents": ["passport", "visa"]}, "FR"],
          "address": {"street": "Main St 1", "locality": "Vienna", "geo": {"lat": 48.2, "lng": 16.37}},
          "matrix": [["a", "b"], ["c"]]
        }
        """.trimIndent()
    ).jsonObject

    private val nestedLabels = mutableMapOf<List<JsonElement>, String>()

    private fun nestedDisclosure(label: String, vararg location: Any): DcqlDisclosure {
        val path = location.map { if (it is Int) JsonPrimitive(it) else JsonPrimitive(it as String) }
        val value = path.fold<JsonPrimitive, JsonElement>(nestedSdJwtData) { element, segment ->
            if (segment.isString) element.jsonObject.getValue(segment.content) else element.jsonArray[segment.int]
        }
        nestedLabels[path] = label
        return DcqlDisclosure(name = (location.last() as? String), value = value, location = path)
    }

    private val nestedDisclosures = listOf(
        nestedDisclosure("nat", "nationalities"),
        nestedDisclosure("nat0", "nationalities", 0),
        nestedDisclosure("nat1", "nationalities", 1),
        nestedDisclosure("nat1.country", "nationalities", 1, "country"),
        nestedDisclosure("nat1.since", "nationalities", 1, "since"),
        nestedDisclosure("nat1.docs", "nationalities", 1, "documents"),
        nestedDisclosure("nat1.docs0", "nationalities", 1, "documents", 0),
        nestedDisclosure("nat1.docs1", "nationalities", 1, "documents", 1),
        nestedDisclosure("nat2", "nationalities", 2),
        nestedDisclosure("addr", "address"),
        nestedDisclosure("addr.street", "address", "street"),
        nestedDisclosure("addr.locality", "address", "locality"),
        nestedDisclosure("addr.geo", "address", "geo"),
        nestedDisclosure("addr.geo.lat", "address", "geo", "lat"),
        nestedDisclosure("addr.geo.lng", "address", "geo", "lng"),
        nestedDisclosure("matrix", "matrix"),
        nestedDisclosure("matrix0", "matrix", 0),
        nestedDisclosure("matrix00", "matrix", 0, 0),
        nestedDisclosure("matrix01", "matrix", 0, 1),
        nestedDisclosure("matrix1", "matrix", 1),
        nestedDisclosure("matrix10", "matrix", 1, 0),
    )

    private val nestedSdJwtCredential = RawDcqlCredential(id = "pid", format = "dc+sd-jwt", data = nestedSdJwtData, disclosures = nestedDisclosures)

    private fun matchNestedSdJwt(vararg claims: String) = DcqlMatcher.match(
        DcqlParser.parse(
            """
            {
              "credentials": [{
                "id": "pid",
                "format": "dc+sd-jwt",
                "meta": { "vct_values": ["https://issuer.example/pid"] },
                "claims": [${claims.joinToString(",")}]
              }]
            }
            """.trimIndent()
        ).getOrThrow(),
        listOf(nestedSdJwtCredential)
    )

    private fun presentedForNestedSdJwt(vararg claims: String): Set<String> =
        matchNestedSdJwt(*claims).getOrThrow().getValue("pid").single().disclosuresByPath.values.flatten().map { nestedLabels.getValue(it.location.orEmpty()) }.toSet()

    @Test
    fun sdJwtContainerPathReleasesOnlyTheContainer() {
        assertEquals(setOf("nat"), presentedForNestedSdJwt("""{"path": ["nationalities"]}"""))
        assertEquals(setOf("addr"), presentedForNestedSdJwt("""{"path": ["address"]}"""))
    }

    @Test
    fun sdJwtNullSelectsEveryArrayElement() {
        assertEquals(setOf("nat", "nat0", "nat1", "nat2"), presentedForNestedSdJwt("""{"path": ["nationalities", null]}"""))
        assertEquals(setOf("matrix", "matrix0", "matrix1"), presentedForNestedSdJwt("""{"path": ["matrix", null]}"""))
        assertEquals(
            setOf("matrix", "matrix0", "matrix1", "matrix00", "matrix01", "matrix10"),
            presentedForNestedSdJwt("""{"path": ["matrix", null, null]}""")
        )
    }

    @Test
    fun sdJwtIndexSelectsThatElementWithItsParents() {
        assertEquals(setOf("nat", "nat0"), presentedForNestedSdJwt("""{"path": ["nationalities", 0]}"""))
        assertEquals(setOf("matrix", "matrix0", "matrix01"), presentedForNestedSdJwt("""{"path": ["matrix", 0, 1]}"""))
    }

    @Test
    fun sdJwtNestedPathIncludesTheParentChain() {
        assertEquals(setOf("nat", "nat1", "nat1.country"), presentedForNestedSdJwt("""{"path": ["nationalities", 1, "country"]}"""))
        assertEquals(
            setOf("nat", "nat1", "nat1.docs", "nat1.docs0", "nat1.docs1"),
            presentedForNestedSdJwt("""{"path": ["nationalities", 1, "documents", null]}""")
        )
        assertEquals(setOf("addr", "addr.geo", "addr.geo.lat"), presentedForNestedSdJwt("""{"path": ["address", "geo", "lat"]}"""))
    }

    @Test
    fun sdJwtClaimsAreReleasedPerPath() {
        val result = matchNestedSdJwt("""{"path": ["nationalities"]}""", """{"path": ["nationalities", 2]}""").getOrThrow()
            .getValue("pid").single()
        assertEquals(listOf("nat"), result.disclosuresByPath.getValue(""""nationalities"""").map { nestedLabels[it.location] })
        assertEquals(listOf("nat", "nat2"), result.disclosuresByPath.getValue(""""nationalities".2""").map { nestedLabels[it.location] })
        assertEquals(
            setOf("addr", "addr.street", "addr.locality", "addr.geo", "addr.geo.lat", "addr.geo.lng"),
            presentedForNestedSdJwt(
                """{"path": ["address", "street"]}""",
                """{"path": ["address", "locality"]}""",
                """{"path": ["address", "geo", "lat"]}""",
                """{"path": ["address", "geo", "lng"]}""",
            )
        )
    }

    @Test
    fun sdJwtClearTextClaimNeedsOnlyTheDisclosuresContainingIt() {
        val result = matchNestedSdJwt("""{"path": ["given_name"]}""", """{"path": ["nationalities", 1, "kind"]}""")
            .getOrThrow().getValue("pid").single()

        assertEquals(emptyList(), result.disclosuresByPath.getValue(""""given_name""""))
        assertEquals(JsonPrimitive("John"), result.selectedDisclosures?.get(""""given_name""""))
        assertEquals(listOf("nat", "nat1"), result.disclosuresByPath.getValue(""""nationalities".1."kind"""").map { nestedLabels[it.location] })
        assertEquals(JsonPrimitive("residency"), result.selectedDisclosures?.get(""""nationalities".1."kind""""))
    }

    @Test
    fun sdJwtValuesFilterTheSelectedElements() {
        assertEquals(setOf("nat", "nat2"), presentedForNestedSdJwt("""{"path": ["nationalities", null], "values": ["FR"]}"""))
        assertTrue(matchNestedSdJwt("""{"path": ["nationalities", null], "values": ["AT"]}""").isFailure)
    }

    @Test
    fun sdJwtPathNotSelectingAClaimFails() {
        // "IN" is not an object, so "country" cannot be applied to every element
        assertTrue(matchNestedSdJwt("""{"path": ["nationalities", null, "country"]}""").isFailure)
        assertTrue(matchNestedSdJwt("""{"path": ["nationalities", 7]}""").isFailure)
        assertTrue(matchNestedSdJwt("""{"path": ["nationalities", "country"]}""").isFailure)
        assertTrue(matchNestedSdJwt("""{"path": ["address", 0]}""").isFailure)
    }

    @Test
    fun sdJwtSingleDisclosedClaimKeepsItsSelectedDisclosure() {
        val result = matchNestedSdJwt("""{"path": ["nationalities", 0]}""").getOrThrow().getValue("pid").single()
        assertEquals(nestedDisclosures.single { nestedLabels[it.location] == "nat0" }, result.selectedDisclosures?.get(""""nationalities".0"""))
    }
}
