package id.walt.policies2

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.W3CExamples
import id.walt.did.helpers.WaltidServices
import id.walt.policies2.policies.CredentialSignaturePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CredentialSignaturePolicyTest : BasePolicyTest() {

    override val policy = CredentialSignaturePolicy()
    override val credentialOk = suspend { CredentialParser.parseOnly(W3CExamples.waltidIssuedJoseSignedW3CCredential) }
    override val credentialNok = suspend { CredentialParser.parseOnly(W3CExamples.waltidIssuedJoseSignedW3CCredentialInvalidSignature) }

    @Test
    fun testOk() = runTest {
        WaltidServices.minimalInit()
        runBaseTestOk()
    }

    @Test
    fun testFail() = runTest {
        WaltidServices.minimalInit()
        runBaseTestNok()
    }

}
