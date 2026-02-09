package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.W3CExamples
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JsonSchemaRemotePolicyTest : BasePolicyTest() {

    companion object {
        private val SCHEMA_URL =
            Url("https://raw.githubusercontent.com/walt-id/waltid-identity/7e128d18c21c8fce684574aa424cffd21a3eadb1/waltid-libraries/credentials/waltid-verification-policies2/src/commonTest/resources/json-schema-remote-policy.json")
    }

    override val policy = JsonSchemaPolicy(schemaUrl = SCHEMA_URL)
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
