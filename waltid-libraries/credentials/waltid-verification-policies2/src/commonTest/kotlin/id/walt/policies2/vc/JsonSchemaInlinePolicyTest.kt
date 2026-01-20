package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.W3CExamples
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

class JsonSchemaInlinePolicyTest : BasePolicyTest() {

    // language=JSON
    private val schema = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "title": "Issuer Schema",
          "type": "object",
          "properties": {
            "issuer": {
              "type": "object",
              "properties": {
                "type": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "contains": {
                    "const": "Profile"
                  }
                },
                "name": {
                  "type": "string"
                },
                "url": {
                  "type": "string",
                  "format": "uri"
                }
              },
              "required": ["type", "name", "url"]
            }
          },
          "required": ["issuer"]
        }
    """.trimIndent()
        .let { Json.decodeFromString<JsonObject>(it) }

    override val policy = JsonSchemaPolicy(schema = schema)
    override val credentialOk = suspend { CredentialParser.parseOnly(W3CExamples.waltidIssuedJoseSignedW3CCredential) }
    override val credentialNok = suspend { CredentialParser.parseOnly(W3CExamples.joseSignedW3CCredential) }

    @Test
    fun testOk() = runTest {
        DidService.minimalInit()
        runBaseTestOk()
    }

    @Test
    fun testFail() = runTest {
        DidService.minimalInit()
        runBaseTestNok()
    }

}
