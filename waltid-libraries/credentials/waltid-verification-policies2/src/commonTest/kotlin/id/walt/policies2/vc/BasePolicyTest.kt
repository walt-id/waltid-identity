package id.walt.policies2.vc

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import kotlinx.coroutines.test.runTest
import kotlin.test.assertTrue

abstract class BasePolicyTest {

    abstract val policy: CredentialVerificationPolicy2
    abstract val credentialOk: suspend () -> DigitalCredential
    abstract val credentialNok: suspend () -> DigitalCredential

    suspend fun runBaseTestOk() {
        println("Policy should succeed: $policy")
        val credential = credentialOk()
        println("Credential for policy (for expected Success): $credential")
        val res = policy.verify(credential)
        println("Policy result (should be Success): $res")
        assertTrue("Policy should have succeeded, but failed!") { res.isSuccess }
    }

    fun baseTestOk() = runTest { runBaseTestOk() }

    suspend fun runBaseTestNok() {
        println("Policy should fail: $policy")
        val credential = credentialNok()
        println("Credential for policy (for expected Failure): $credential")
        val res = policy.verify(credential)
        println("Policy result (should be Failure): $res")
        assertTrue("Policy should have failed, but succeeded!") { res.isFailure }
    }

    fun baseTestNok() = runTest { runBaseTestNok() }

}
