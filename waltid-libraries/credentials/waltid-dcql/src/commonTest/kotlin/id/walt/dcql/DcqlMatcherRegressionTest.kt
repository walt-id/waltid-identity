package id.walt.dcql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DcqlMatcherRegressionTest {
    private fun match(claims: String, sets: String = "", data: String) = DcqlMatcher.matchCredential(
        DcqlParser.parse("""{"credentials":[{"id":"q","format":"jwt_vc_json","meta":{},"claims":$claims $sets}]}""").getOrThrow().credentials.single(),
        RawDcqlCredential("c", "jwt_vc_json", Json.parseToJsonElement(data).jsonObject),
    )

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
