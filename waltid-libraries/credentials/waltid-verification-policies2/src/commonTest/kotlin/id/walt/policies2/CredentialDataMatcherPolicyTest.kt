package id.walt.policies2

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.examples.W3CExamples
import id.walt.policies2.policies.CredentialDataMatcherPolicy
import kotlin.test.Test


class CredentialDataMatcherPolicyTest : BasePolicyTest() {

    override val policy = CredentialDataMatcherPolicy(
        path = "$.credentialSubject.degree.name",
        regex = "^Bachelor of Science and Arts$"
    )
    override val credentialOk = suspend { CredentialParser.parseOnly(W3CExamples.dipEcdsaSignedW3CCredential) }
    override val credentialNok = suspend { CredentialParser.parseOnly(W3CExamples.w3cCredential) }

    @Test
    fun testOk() = baseTestOk()

    @Test
    fun testFail() = baseTestNok()

}


