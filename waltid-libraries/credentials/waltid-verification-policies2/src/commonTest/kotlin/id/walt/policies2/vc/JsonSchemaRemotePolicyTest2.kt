package id.walt.policies2.vc

import id.walt.credentials.examples.W3CExamples
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class JsonSchemaRemotePolicyTest2 : BasePolicyTest() {

    companion object {
        private val SCHEMA_URL = Url("https://purl.imsglobal.org/spec/ob/v3p0/schema/json/ob_v3p0_achievementcredential_schema.json")
    }

    override val policy = JsonSchemaPolicy(schemaUrl = SCHEMA_URL)
    override val credentialOk = suspend { W3CExamples.compliantOpenBadgeCredential }
    override val credentialNok = suspend { W3CExamples.noncompliantOpenBadgeCredential }

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
