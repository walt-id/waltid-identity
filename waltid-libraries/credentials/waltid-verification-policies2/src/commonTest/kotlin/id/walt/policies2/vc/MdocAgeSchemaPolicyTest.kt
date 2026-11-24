package id.walt.policies2.vc

import id.walt.credentials.formats.MdocsCredential
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class MdocAgeSchemaPolicyTest {
    private val policy = JsonSchemaPolicy(schema = Json.decodeFromString<JsonObject>("""
        {"type":"object","required":["org.iso.23220.1"],"properties":{
          "org.iso.23220.1":{"type":"object","required":["age_over_18"],
            "properties":{"age_over_18":{"type":"boolean"}}}}}
    """))

    @Test
    fun `boolean age claims pass without interpreting false as an age approval`() = runTest {
        for (value in listOf("true", "false")) {
            assertTrue(policy.verify(credential(value), PolicyExecutionContext.Empty).isSuccess)
        }
    }

    @Test
    fun `non boolean and absent age claims fail the configured schema`() = runTest {
        for (value in listOf("\"false\"", "\"true\"", "\"0\"", "0", "1", "1.0", "[]", "[false]", "{}", "null")) {
            assertTrue(policy.verify(credential(value), PolicyExecutionContext.Empty).isFailure, value)
        }
        val missing = MdocsCredential(Json.decodeFromString("""{"org.iso.23220.1":{}}"""), null, "org.iso.23220.photoid.1")
        assertTrue(policy.verify(missing, PolicyExecutionContext.Empty).isFailure)
    }

    private fun credential(value: String) = MdocsCredential(
        credentialData = Json.decodeFromString("""{"org.iso.23220.1":{"age_over_18":$value}}"""),
        signed = null,
        docType = "org.iso.23220.photoid.1",
    )
}
