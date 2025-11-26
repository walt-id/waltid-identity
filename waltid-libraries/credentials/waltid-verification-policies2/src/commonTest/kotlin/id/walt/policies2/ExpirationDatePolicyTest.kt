package id.walt.policies2

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.policies2.policies.ExpirationDatePolicy
import kotlin.test.Test

class ExpirationDatePolicyTest : BasePolicyTest() {

    override val policy = ExpirationDatePolicy()
    override val credentialOk = suspend { CredentialParser.parseOnly(SdJwtExamples.sdJwtVcWithDisclosablesExample) }
    override val credentialNok = suspend { CredentialParser.parseOnly(SdJwtExamples.sdJwtVcExpiredExample) }

    @Test
    fun testOk() = baseTestOk()
    @Test
    fun testFail() = baseTestNok()

}
