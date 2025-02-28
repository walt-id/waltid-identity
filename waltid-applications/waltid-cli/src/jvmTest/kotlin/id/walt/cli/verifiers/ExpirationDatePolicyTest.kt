package id.walt.cli.verifiers

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.policies.ExpirationDatePolicyException
import id.walt.policies.PolicyManager
import id.walt.policies.Verifier
import id.walt.policies.models.PolicyRequest
import id.walt.policies.policies.ExpirationDatePolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.*

class ExpirationDatePolicyTest {

    val resourcesPath = "src/jvmTest/resources"

    // val keyFileName = "${resourcesPath}/key/ed25519_by_waltid_pvt_key.jwk"
    // val key = runBlocking { KeyUtil().getKey(File(keyFileName)) }
    // val issuerDid = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    // val subjectDid = "did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9"

    val expiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.expired.json"
    val notExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"
    val signedExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.expired.signed.json"
    val signedNotExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"

    val notExpiredJwt = File(notExpiredVCFilePath).readText()
    val expiredJwt = File(expiredVCFilePath).readText()

    val notExpiredJws = File(signedNotExpiredVCFilePath).readText()
    val expiredJws = File(signedExpiredVCFilePath).readText()

    @Test
    fun `should NOT fail with a valid signed VC and data = jws-payload`() {

        val data = notExpiredJws.decodeJws().payload

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertSuccess(result)
    }

    @Test
    fun `should NOT fail with a valid signed VC and data = jws-payload('vc')`() {

        val data = notExpiredJws.decodeJws().payload["vc"]!!.jsonObject

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertSuccess(result)
    }

    @Test
    fun `should NOT fail with a valid NOT signed VC and data = jwt`() {

        val data = Json.parseToJsonElement(notExpiredJwt).jsonObject

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertSuccess(result)
    }

    @Test
    fun `should fail ExpirationDatePolicy + jws-payload('vc')`() {

        val data = expiredJws.decodeJws().payload["vc"]!!.jsonObject

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertFail(result)
    }

    @Test
    fun `should fail ExpirationDatePolicy + jwt`() {

        val data = Json.parseToJsonElement(expiredJwt).jsonObject

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertFail(result)
    }

    @Test
    fun `should fail ExpirationDatePolicy + jws-payload`() {

        val data = expiredJws.decodeJws().payload

        val policy = ExpirationDatePolicy()
        val result = runBlocking { policy.verify(data = data, context = emptyMap()) }

        assertFail(result)
    }

    @Test
    fun `should fail Verifier + jws`() {

        val data = expiredJws

        val policy = PolicyManager.getPolicy("expired")
        val request = PolicyRequest(policy = policy)

        val result = runBlocking { Verifier.verifyCredential(data, listOf(request)) }

        assertFail(result[0].result)
    }

    private fun assertSuccess(result: Result<Any>?) {
        val policyAvailable =
            (result!!.getOrThrow() as JsonObject)["policy_available"]!! == JsonPrimitive(true)

        assertTrue(policyAvailable, "policy not available i.e. policy not even applied.")
        assertTrue(result.isSuccess)
        assertNull(result.exceptionOrNull())
        assertEquals((result.getOrThrow() as JsonObject)["policy_available"]!!, JsonPrimitive(true), "policy not available i.e. policy not even applied.")
    }

    private fun assertFail(result: Result<Any?>) {
        var policyAvailable = false
        var reason = "neither 'exp', 'validUntil' nor 'expirationDate' found"
        val exception = result.exceptionOrNull()

        if (exception != null) {
            if (exception is ExpirationDatePolicyException) {
                policyAvailable = (result.exceptionOrNull() as ExpirationDatePolicyException).policyAvailable
            } else if (exception is IllegalStateException) {
                policyAvailable = false
                reason = exception.message!!
            }
        } else {
            policyAvailable = (result.getOrThrow() as JsonObject)["policy_available"]!! == JsonPrimitive(true)
        }

        assertTrue(policyAvailable, "policy not available i.e. policy not even applied (${reason})")
        assertFalse(result.isSuccess)
        assertTrue((result.exceptionOrNull() as ExpirationDatePolicyException).expiredSince.isPositive())
    }

    // @Test
    // fun ``() = Unit
    //
    // @Test
    // fun ``() = Unit
    //
    // @Test
    // fun ``() = Unit
    //
    // @Test
    // fun ``() = Unit
    //
    // @Test
    // fun ``() = Unit
}
