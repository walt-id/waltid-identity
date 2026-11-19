package id.walt.dcql

import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.dcql.jsonld.JsonLdContextDocumentSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DcqlMatcherRegressionTest {
    private fun match(claims: String, sets: String = "", data: String) = DcqlMatcher.matchCredential(
        DcqlParser.parse("""{"credentials":[{"id":"q","format":"jwt_vc_json","meta":{},"claims":$claims $sets}]}""").getOrThrow().credentials.single(),
        RawDcqlCredential("c", "jwt_vc_json", Json.parseToJsonElement(data).jsonObject),
    )

    @Test
    fun copiedCredentialRetainsAncestorMatchingWithTheSameContext() {
        val query = DcqlParser.parse("""{"credentials":[{"id":"q","format":"dc+sd-jwt","meta":{"vct_values":["urn:example:parent"]}}]}""")
            .getOrThrow().credentials.single()
        val credential = RawDcqlCredential("c", "dc+sd-jwt", buildJsonObject { put("vct", JsonPrimitive("urn:example:leaf")) })
        val context = DcqlMatchingContext(mapOf(credential to setOf("urn:example:parent")))
        assertTrue(DcqlMatcher.matchCredential(query, credential, context).isSuccess)
        assertTrue(DcqlMatcher.matchCredential(query, credential.copy(), context).isSuccess)
        assertTrue(DcqlMatcher.matchCredential(query, credential.copy(), context, JsonLdContextDocumentSource.bundled).isSuccess)
        assertTrue(DcqlMatcher.matchCredential(query, credential.copy()).isFailure)
        val leafQuery = query.copy(meta = SdJwtVcMeta(listOf("urn:example:leaf")))
        assertTrue(DcqlMatcher.matchCredential(leafQuery, credential.copy()).isSuccess)
    }

    @Test
    fun verifiedTypesAreBoundToTheInputAndSharedByEveryMatchingEntryPoint() {
        val query = DcqlParser.parse("""{"credentials":[{"id":"q","format":"dc+sd-jwt","multiple":true,"meta":{"vct_values":["urn:example:parent"]}}]}""")
            .getOrThrow()
        val data = mutableMapOf("vct" to JsonPrimitive("urn:example:leaf"))
        val raw = RawDcqlCredential("c", "dc+sd-jwt", JsonObject(data))
        val custom = object : DcqlCredential by raw {}
        val types = mutableSetOf("urn:example:parent")
        val inputs = mutableMapOf<DcqlCredential, Set<String>>(custom to types)
        val context = DcqlMatchingContext(inputs)
        types.clear()
        inputs.clear()
        // The context owns its inputs, including JSON backed by a mutable map.
        data["vct"] = JsonPrimitive("urn:example:replacement")
        val original = raw.copy(data = buildJsonObject { put("vct", JsonPrimitive("urn:example:leaf")) })
        val other = original.copy(id = "other")
        val candidates = listOf(raw, other, original)
        assertFailsWith<IllegalArgumentException> {
            DcqlMatchingContext(mapOf(
                original to setOf("urn:example:parent"),
                (object : DcqlCredential by original {}) to setOf("urn:example:other"),
            ))
        }
        assertTrue(DcqlMatcher.matchCredential(query.credentials.single(), custom, context).isFailure)
        assertTrue(DcqlMatcher.matchCredential(query.credentials.single(), original, context).isSuccess)
        val customCopy = object : DcqlCredential by original {}
        assertTrue(DcqlMatcher.matchCredential(query.credentials.single(), customCopy, context).isSuccess)
        for (matches in listOf(
            DcqlMatcher.findMatches(query, candidates, context),
            DcqlMatcher.findMatches(query, candidates, context, JsonLdContextDocumentSource.bundled),
            DcqlMatcher.match(query, candidates, context),
            DcqlMatcher.match(query, candidates, context, JsonLdContextDocumentSource.bundled),
        )) assertEquals(listOf(original), matches.getOrThrow().getValue("q").map { it.credential })
        assertEquals(listOf(original), DcqlMatcher.matchWithoutClaims(query, candidates, context).getOrThrow().getValue("q"))
        assertEquals(listOf(original), DcqlMatcher.matchWithoutClaims(query, candidates, context,
            JsonLdContextDocumentSource.bundled).getOrThrow().getValue("q"))
    }

    @Test
    fun wildcardValue() {
        val result = match("""[{"path":["roles",null],"values":["admin"]}]""", data = """{"roles":["admin"]}""")
        assertTrue(result.isSuccess, result.toString())
        assertFalse(match("""[{"path":["roles",null],"values":["admin"]}]""", data = """{"roles":["user"]}""").isSuccess)
        assertTrue(match("""[{"path":["roles",null],"values":["admin"]}]""", data = """{"roles":["user","admin"]}""").isSuccess)
    }

    @Test
    fun alternativeClaimSet() {
        val result = match(
            """[{"id":"a","path":["address","street"]},{"id":"b","path":["email"]}]""",
            """, "claim_sets":[["a"],["b"]]""",
            """{"address":"unstructured address", "email":"a@example.com"}""",
        )
        assertTrue(result.isSuccess, result.toString())
    }

    @Test
    fun scalarType() {
        val query = DcqlParser.parse("""{"credentials":[{"id":"q","format":"jwt_vc_json","meta":{"type_values":[["VerifiableCredential"]]}}]}""").getOrThrow().credentials.single()
        val result = DcqlMatcher.matchCredential(query, RawDcqlCredential("c", "jwt_vc_json", Json.parseToJsonElement("""{"type":"VerifiableCredential"}""").jsonObject))
        assertTrue(result.isSuccess, result.toString())
    }

    @Test
    fun wildcardValuesConsiderEveryDisclosure() {
        val query = DcqlParser.parse("""{"credentials":[{"id":"q","format":"dc+sd-jwt","meta":{},
            "claims":[{"path":["roles",null],"values":["admin"]}]}]}""").getOrThrow().credentials.single()
        val user = DcqlDisclosure(null, JsonPrimitive("user"), listOf(JsonPrimitive("roles"), JsonPrimitive(0)))
        val admin = DcqlDisclosure(null, JsonPrimitive("admin"), listOf(JsonPrimitive("roles"), JsonPrimitive(1)))
        val credential = RawDcqlCredential("c", "dc+sd-jwt", buildJsonObject {}, listOf(user, admin))

        val result = DcqlMatcher.matchCredential(query, credential)
        assertTrue(result.isSuccess, result.toString())
        assertEquals(admin, result.getOrThrow().selectedDisclosures?.values?.single())
        assertTrue(DcqlMatcher.matchCredential(query, credential.copy(disclosures = listOf(user))).isFailure)
    }

    @Test
    fun invalidPathKindsFailTheOptionInsteadOfThrowing() {
        for (address in listOf("null", "42", "true", "[]", "\"unstructured address\"")) {
            val claims = """[{"id":"a","path":["address","street"]},{"id":"b","path":["email"]}]"""
            val sets = """, "claim_sets":[["a"],["b"]]"""
            assertTrue(match(claims, sets, """{"address":$address,"email":"a@example.com"}""").isSuccess)
            assertTrue(match(claims, sets, """{"address":$address}""").isFailure)
        }
    }

    @Test
    fun invalidWildcardBranchesDoNotMatch() {
        val data = Json.parseToJsonElement("""{"roles":[{"name":"admin"},42]}""").jsonObject
        assertEquals(null, DcqlMatcher.resolveClaimPath(data, listOf(JsonPrimitive("roles"), JsonNull, JsonPrimitive("name"))))
        assertEquals(null, DcqlMatcher.resolveClaimPath(data, listOf(JsonPrimitive("roles"), JsonPrimitive("0"))))
    }
}
