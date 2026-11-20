package id.walt.verifier2.verification2

import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * A DCQL query with `multiple: true` answered with two presentations (WAL-1582 / WAL-1588): every
 * presentation counts, not only the last one.
 */
class MultiplePresentationsTest {

    private val fixture = JwtVcPresentationFixture(nonce = "nonce-1582", multiple = true)

    private fun session() = fixture.session()

    @BeforeTest
    fun generateKeys() = runTest { fixture.generateKeys() }

    private suspend fun credential(name: String, expiresInDays: Int) = fixture.credential(name, expiresInDays)

    private suspend fun presentation(credential: String, presentationNonce: String? = null) =
        if (presentationNonce == null) fixture.presentation(credential) else fixture.presentation(credential, presentationNonce)

    private suspend fun verify(vararg presentations: String): Verification2Session {
        val session = session()
        runCatching {
            PresentationVerificationEngine.executeAllVerification(
                vpTokenContents = mapOf("ids" to presentations.toList()),
                session = session,
                updateSessionCallback = { s, _, block -> s.block() },
                failSessionCallback = { s, event, update -> update(s, event) { status = Verification2Session.VerificationSessionStatus.FAILED } },
            )
        }.exceptionOrNull()?.let { assertIs<PresentationRejectionException>(it) }
        return session
    }

    @Test
    fun `an expired first credential fails the session`() = runTest {
        val session = verify(presentation(credential("first", expiresInDays = -1)), presentation(credential("second", expiresInDays = 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        assertEquals(listOf("first", "second"), session.presentedCredentials!!.getValue("ids").map {
            (it.credentialData["vc"]?.jsonObject ?: it.credentialData)["credentialSubject"]!!.jsonObject["name"]!!.jsonPrimitive.content
        })
        val expiration = session.policyResults!!.vcPolicies.filter { it.policy.id == "expiration" }
        assertEquals(listOf(false, true), expiration.sortedBy { it.credentialIndex }.map { it.success })
    }

    @Test
    fun `every presentation is kept, and a VP policy failing for the first one is not lost`() = runTest {
        val session = verify(presentation(credential("first", 30), presentationNonce = "replayed"), presentation(credential("second", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        assertEquals(2, session.presentedPresentations!!.getValue("ids").size)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertFalse(nonceCheck.success)
        assertEquals(1, nonceCheck.errors.size)
        assertEquals(listOf(0), nonceCheck.failedPresentationIndexes())
    }

    @Test
    fun `a VP policy failing for several presentations reports every one of them`() = runTest {
        val session = verify(
            presentation(credential("first", 30), presentationNonce = "replayed"),
            presentation(credential("second", 30)),
            presentation(credential("third", 30), presentationNonce = "replayed"),
        )

        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertEquals(2, nonceCheck.errors.size)
        assertEquals(listOf(0, 2), nonceCheck.failedPresentationIndexes())
        assertEquals(3, nonceCheck.multiplePresentations().getValue("count").jsonPrimitive.int)
    }

    @Test
    fun `two valid credentials pass, and both are verified`() = runTest {
        val session = verify(presentation(credential("first", 30)), presentation(credential("second", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
        assertEquals(2, session.presentedCredentials!!.getValue("ids").size)
        assertEquals(2, session.policyResults!!.vcPolicies.count { it.policy.id == "expiration" && it.success })
        assertEquals(2, session.presentedPresentations!!.getValue("ids").size)
        val nonceCheck = session.presentationValidationResults!!.getValue("ids").getValue("jwt_vc_json/nonce-check")
        assertTrue(nonceCheck.success)
        assertEquals(emptyList(), nonceCheck.failedPresentationIndexes())
    }

    @Test
    fun `a single presentation keeps its VP policy results unchanged`() = runTest {
        val session = verify(presentation(credential("only", 30)))

        assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
        session.presentationValidationResults!!.getValue("ids").values.forEach {
            assertFalse(PresentationVerificationEngine.MULTIPLE_PRESENTATIONS_RESULT in it.results)
        }
    }

    private fun VPPolicy2.PolicyRunResult.multiplePresentations() =
        results.getValue(PresentationVerificationEngine.MULTIPLE_PRESENTATIONS_RESULT).jsonObject

    private fun VPPolicy2.PolicyRunResult.failedPresentationIndexes() =
        multiplePresentations().getValue("failed_indexes").jsonArray.map { it.jsonPrimitive.int }
}
