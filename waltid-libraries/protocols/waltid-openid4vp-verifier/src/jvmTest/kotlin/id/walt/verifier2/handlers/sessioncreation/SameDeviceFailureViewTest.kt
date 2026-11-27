package id.walt.verifier2.handlers.sessioncreation

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vp.policies.NonceCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcqlFulfillmentFailure
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SameDeviceFlowSetup
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.verification2.Verifier2PolicyResults
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration

class SameDeviceFailureViewTest {
    private val secret = "PRIVATE_PRESENTATION_VALUE"
    private val vc = CredentialPolicyResult(CredentialSignaturePolicy(), false, JsonPrimitive(secret), secret,
        queryId = "credential", credentialIndex = 0)
    private val vpPolicy = NonceCheckJwtVcJsonVPPolicy()
    private val vp = VPPolicy2.PolicyRunResult(vpPolicy, false, mapOf("payload" to JsonPrimitive(secret)),
        listOf(VPPolicy2.PolicyRunError("ValidationError", secret, VPPolicy2.PolicyRunError("Cause", secret))), Duration.ZERO)

    private suspend fun session() = VerificationSessionCreator.createVerificationSession(
        SameDeviceFlowSetup(GeneralFlowConfig(), redirects = Verification2Session.VerificationSessionRedirects(
            successRedirectUri = Url("https://example.com/success"))),
        clientId = null, urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize",
    ).apply {
        status = Verification2Session.VerificationSessionStatus.FAILED
        presentedRawData = Verification2Session.PresentedRawData(mapOf("credential" to listOf(secret)), secret)
        policyResults = Verifier2PolicyResults(mapOf("credential" to mapOf(vpPolicy.id to vp)), listOf(vc), mapOf("credential" to listOf(vc)))
        presentationValidationResults = mapOf("credential" to mapOf(vpPolicy.id to vp))
        statusReason = secret
        failure = SessionFailure.VcPolicyViolations(secret, listOf(vc))
    }

    @Test
    fun `failed views expose policy diagnostics without presentation payloads`() = runTest {
        val stored = session()
        val restored = Json.decodeFromString<Verification2Session>(Json.encodeToString(stored))
        for (view in listOf(restored.publicView(), restored.completionView(null), restored.completionView("wrong"))) {
            val failure = assertIs<SessionFailure.VcPolicyViolations>(view.failure)
            assertEquals("Credential policy verification failed. Failed VC policies: signature", failure.reason)
            assertEquals(failure.reason, view.statusReason)
            assertFalse(assertNotNull(view.policyResults).overallSuccess)
            assertEquals("signature", failure.violations.single().policy.id)
            assertNull(view.presentedRawData)
            assertNull(view.presentedCredentials)
            assertNull(view.responseCode)
            assertFalse(secret in Json.encodeToString(view))
        }
        assertEquals(secret, stored.policyResults!!.vcPolicies.single().error)
        assertEquals(JsonPrimitive(secret), stored.policyResults!!.vcPolicies.single().result)
        assertEquals(secret, stored.presentationValidationResults!!.getValue("credential").getValue(vpPolicy.id).errors.single().message)
    }

    @Test
    fun `every failure type has a diagnostic view without raw errors`() = runTest {
        val failures = listOf(
            SessionFailure.PresentationValidation(secret, mapOf("credential" to mapOf(vpPolicy.id to vp))),
            SessionFailure.DcqlFulfillment(secret, DcqlFulfillmentFailure(missingQueryIds = setOf("credential"))),
            SessionFailure.WalletErrorResponse(secret, secret, secret, secret),
        )
        for (failure in failures) {
            val view = session().apply { this.failure = failure }.publicView()
            assertNotNull(view.failure)
            assertNotNull(view.statusReason)
            assertEquals(failure::class, view.failure!!::class)
            assertFalse(secret in Json.encodeToString(view))
        }
    }

    @Test
    fun `successful and in-progress views still hide outcome details`() = runTest {
        for (status in listOf(Verification2Session.VerificationSessionStatus.SUCCESSFUL,
            Verification2Session.VerificationSessionStatus.PROCESSING_FLOW)) {
            val view = session().apply { this.status = status }.publicView()
            assertNull(view.failure)
            assertNull(view.statusReason)
            assertNull(view.policyResults)
            assertNull(view.presentationValidationResults)
        }
        val crossDevice = session().apply { setup = CrossDeviceFlowSetup(GeneralFlowConfig()) }.publicView()
        assertEquals(secret, crossDevice.failure!!.reason)
        assertEquals(secret, crossDevice.policyResults!!.vcPolicies.single().error)
    }
}
