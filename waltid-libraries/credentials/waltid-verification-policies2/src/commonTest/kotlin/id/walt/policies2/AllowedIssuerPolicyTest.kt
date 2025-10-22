package id.walt.policies2

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.examples.W3CExamples
import id.walt.policies2.policies.AllowedIssuerPolicy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test


class AllowedIssuerPolicyTest : BasePolicyTest() {

    override val policy = AllowedIssuerPolicy(JsonArray(listOf(JsonPrimitive("https://university.example/issuers/565049"))))
    override val credentialOk = suspend { CredentialParser.parseOnly(W3CExamples.w3cCredential) }
    override val credentialNok = suspend { CredentialParser.parseOnly(SdJwtExamples.sdJwtVcWithDisclosablesExample) }

    @Test
    fun testOk() = baseTestOk()

    @Test
    fun testFail() = baseTestNok()

}


