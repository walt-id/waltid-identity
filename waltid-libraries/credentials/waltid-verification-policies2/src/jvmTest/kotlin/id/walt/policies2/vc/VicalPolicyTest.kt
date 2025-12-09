package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.policies2.vc.policies.VicalPolicy
import kotlin.test.Test

class VicalPolicyTest : BasePolicyTest() {

    fun loadVicalBase64(name: String) = this::class.java.classLoader.getResource(name)?.readBytes()?.encodeToBase64()
        ?: error("Vical for test not found: $name")

    override val policy = VicalPolicy(loadVicalBase64("vicals/austroads.cbor"))
    override val credentialOk =
        suspend { CredentialParser.parseOnly(MdocsExamples.mdocsExampleBase64Url) } // FIX: Update Valid credential
    override val credentialNok = suspend { CredentialParser.parseOnly(MdocsExamples.mdocsExampleBase64Url) }

// TODO: We need a positive test-case
//    @Test
//    fun testOk() = baseTestOk()

    @Test
    fun testFail() = baseTestNok()

}
