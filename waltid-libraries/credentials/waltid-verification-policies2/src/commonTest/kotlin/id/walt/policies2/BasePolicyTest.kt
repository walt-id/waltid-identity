package id.walt.policies2

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.policies.VerificationPolicy2
import kotlinx.coroutines.test.runTest
import kotlin.test.assertTrue

abstract class BasePolicyTest {

    abstract val policy: VerificationPolicy2
    abstract val credentialOk: suspend () -> DigitalCredential
    abstract val credentialNok: suspend () -> DigitalCredential

    suspend fun runBaseTestOk() {
        println("Policy should succeed: $policy")
        val res = policy.verify(credentialOk())
        println("Policy succeeded (should be true): $res")
        assertTrue(res.isSuccess)
    }

    fun baseTestOk() = runTest { runBaseTestOk() }

    suspend fun runBaseTestNok() {
        println("Policy should fail: $policy")
        val res = policy.verify(credentialNok())
        println("Policy failed (should be true): $res")
        assertTrue(res.isFailure)
    }

    fun baseTestNok() = runTest { runBaseTestNok() }

}
