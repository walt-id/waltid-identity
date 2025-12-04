package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.policies2.vc.policies.NotBeforePolicy
import kotlin.test.Test

class NotBeforePolicyTest : BasePolicyTest() {

    override val policy = NotBeforePolicy()
    override val credentialOk = suspend { CredentialParser.parseOnly(SdJwtExamples.sdJwtVcWithDisclosablesExample) }
    override val credentialNok = suspend { CredentialParser.parseOnly(SdJwtExamples.sdJwtVcFutureExample) }

    @Test
    fun testOk() = baseTestOk()
    @Test
    fun testFail() = baseTestNok()

}
