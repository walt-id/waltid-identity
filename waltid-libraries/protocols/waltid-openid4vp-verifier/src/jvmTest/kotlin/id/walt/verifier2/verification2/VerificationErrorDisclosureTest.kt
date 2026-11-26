package id.walt.verifier2.verification2

import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.ETSITrustListPolicy
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationVerificationUnavailableException
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.VERIFICATION_TEMPORARILY_UNAVAILABLE
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.respondHandleDirectPostResponse
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * An unexpected failure during verification must not tell the wallet, `/info` or a webhook what
 * broke inside the verifier: exception class names and messages can carry store hosts, ports and
 * connection strings. The client gets a generic reason and an errorId; the detail stays in the log.
 * A rejection of the wallet's own input keeps a specific reason, written by the verifier.
 */
class VerificationErrorDisclosureTest {

    private val state = "state-disclosure"
    private val fixture = JwtVcPresentationFixture(nonce = "nonce-disclosure", state = state)
    private val secretDetail = "mongo://secret-host:27017 timeout"

    /** Stand-in for the driver's timeout: its name is what classifies the failure as infrastructure. */
    private class MongoTimeoutException(message: String) : Exception(message)

    private fun storeTimeout() = RuntimeException(secretDetail, MongoTimeoutException(secretDetail))

    @BeforeTest
    fun generateKeys() = runTest { fixture.generateKeys() }

    private suspend fun presentation(expiresInDays: Int = 30) =
        fixture.presentation(fixture.credential("disclosure", expiresInDays))

    /** A session store whose writes fail with [failure] from [failingEvent] on. */
    private fun failingStore(
        failure: Exception,
        failingEvent: SessionEvent = SessionEvent.parsed_presentation_available,
        failEveryLaterWrite: Boolean = false,
    ): suspend (Verification2Session, SessionEvent, Verification2Session.() -> Unit) -> Unit {
        var failing = false
        return { session, event, block ->
            if (event == failingEvent) failing = true
            if (failing) {
                if (!failEveryLaterWrite) failing = false
                throw failure
            }
            session.block()
        }
    }

    private val workingStore: suspend (Verification2Session, SessionEvent, Verification2Session.() -> Unit) -> Unit =
        { s, _, block -> s.block() }

    private val failSession: suspend (
        Verification2Session, SessionEvent,
        suspend (Verification2Session, SessionEvent, Verification2Session.() -> Unit) -> Unit,
    ) -> Unit = { s, event, update -> update(s, event) { status = Verification2Session.VerificationSessionStatus.FAILED } }

    private fun failingExpirationPolicy(failure: Exception) = ExpirationDatePolicy(clock = object : Clock {
        override fun now(): Instant = throw failure
    })

    private fun assertNoInternalDetail(text: String?) {
        assertNotNull(text)
        for (fragment in listOf("secret-host", ":27017", "mongo://", "RuntimeException", "MongoTimeoutException")) {
            assertFalse(fragment in text, "'$fragment' leaked into: $text")
        }
    }

    private suspend fun verify(
        vpToken: List<String>,
        session: Verification2Session,
        store: suspend (Verification2Session, SessionEvent, Verification2Session.() -> Unit) -> Unit,
    ) = PresentationVerificationEngine.executeAllVerification(
        vpTokenContents = mapOf("ids" to vpToken),
        session = session,
        updateSessionCallback = store,
        failSessionCallback = failSession,
    )

    /** Posts [vpToken] to a direct_post endpoint backed by [store] and returns the response. */
    private suspend fun postDirect(
        vpToken: String,
        session: Verification2Session,
        store: suspend (Verification2Session, SessionEvent, Verification2Session.() -> Unit) -> Unit,
    ): Pair<HttpStatusCode, String> {
        var result: Pair<HttpStatusCode, String>? = null
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    post("/response") {
                        call.respondHandleDirectPostResponse(
                            verificationSession = session,
                            updateSessionCallback = store,
                            failSessionCallback = failSession,
                        )
                    }
                }
            }
            val response: HttpResponse = client.submitForm("/response", parameters {
                append("vp_token", buildJsonObject { putJsonArray("ids") { add(vpToken) } }.toString())
                append("state", state)
            })
            result = response.status to response.bodyAsText()
        }
        return result!!
    }

    private fun String.field(name: String) = Json.parseToJsonElement(this).jsonObject[name]!!.jsonPrimitive.content

    @Test
    fun `an infrastructure failure is reported without its message`() = runTest {
        val session = fixture.session()
        val thrown = assertFailsWith<PresentationVerificationUnavailableException> {
            verify(listOf(presentation()), session, failingStore(storeTimeout()))
        }

        assertNoInternalDetail(thrown.message)
        assertContains(thrown.message!!, "errorId=")
        val reason = session.failure!!.reason
        assertNoInternalDetail(reason)
        assertContains(reason, "errorId=")
        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
    }

    @Test
    fun `returned and thrown policy errors have safe reasons throughout the public session`() = runTest {
        val original = fixture.session()
        val session = original.copy(policies = original.policies.copy(
            vc_policies = VCPolicyList(listOf(ETSITrustListPolicy())),
            specific_vc_policies = mapOf("ids" to VCPolicyList(listOf(failingExpirationPolicy(IllegalStateException(secretDetail))))),
        ))
        val (status, _) = postDirect(presentation(), session, workingStore)

        assertEquals(HttpStatusCode.BadRequest, status)
        val public = session.publicView()
        val results = assertNotNull(public.policyResults)
        val general = results.vcPolicies.single()
        val specific = results.specificVcPolicies.getValue("ids").single()
        assertEquals("Credential did not pass the 'etsi-trust-list' policy", general.error)
        assertEquals("Credential did not pass the 'expiration' policy", specific.error)
        assertEquals(listOf(general, specific), assertIs<SessionFailure.VcPolicyViolations>(public.failure).violations)
    }

    @Test
    fun `a cancelled verification leaves terminal state to the caller`() = runTest {
        val cancellation = CancellationException("request ended")
        val session = fixture.session()
        session.policies = session.policies.copy(vc_policies = VCPolicyList(listOf(failingExpirationPolicy(cancellation))))
        val thrown = assertFailsWith<CancellationException> {
            verify(listOf(presentation()), session, workingStore)
        }
        assertEquals(cancellation.message, thrown.message)
        assertNull(session.failure)
        assertNotEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
    }

    @Test
    fun `a store that also fails to record the failure is still reported as unavailable`() = runTest {
        // A store outage fails every write, including the one that records the failure. That second
        // failure must not escape unclassified: the wallet should still get a retryable 503.
        val thrown = assertFailsWith<PresentationVerificationUnavailableException> {
            verify(listOf(presentation()), fixture.session(), failingStore(storeTimeout(), failEveryLaterWrite = true))
        }

        assertNoInternalDetail(thrown.message)
        assertContains(thrown.message!!, "errorId=")
    }

    @Test
    fun `an unexpected internal failure is reported without its class or message`() = runTest {
        val session = fixture.session()
        val thrown = assertFailsWith<PresentationRejectionException> {
            verify(listOf(presentation()), session, failingStore(RuntimeException(secretDetail)))
        }

        assertNoInternalDetail(thrown.message)
        assertContains(thrown.message!!, "errorId=")
        val reason = session.failure!!.reason
        assertNoInternalDetail(reason)
        // The errorId the client sees is the one on the session, so support can match the two.
        assertEquals(
            Regex("errorId=([0-9a-f-]+)").find(thrown.message!!)!!.groupValues[1],
            Regex("errorId=([0-9a-f-]+)").find(reason)!!.groupValues[1],
        )
    }

    @Test
    fun `an oversized presentation is still rejected with its sizes`() = runTest {
        val session = fixture.session()
        val thrown = assertFailsWith<PresentationRejectionException> {
            verify(listOf("a".repeat(maxStorablePresentationBytes + 1)), session, workingStore)
        }

        assertContains(thrown.message!!, "too large to store")
        assertEquals(thrown.message, session.failure!!.reason)
    }

    @Test
    fun `the 503 response carries a constant description`() = runTest {
        val (status, body) = postDirect(presentation(), fixture.session(), failingStore(storeTimeout()))

        assertEquals(HttpStatusCode.ServiceUnavailable, status)
        assertEquals("temporarily_unavailable", body.field("error"))
        assertEquals(VERIFICATION_TEMPORARILY_UNAVAILABLE, body.field("error_description"))
        assertNoInternalDetail(body)
    }

    @Test
    fun `a 400 rejection answers the wallet neutrally and keeps its reason on the session`() = runTest {
        // The wallet gets one constant description; the verifier operator reads the specific reason on the session.
        val session = fixture.session()
        val (status, body) = postDirect(presentation(expiresInDays = -1), session, workingStore)

        assertEquals(HttpStatusCode.BadRequest, status)
        assertEquals("invalid_request", body.field("error"))
        assertEquals("Invalid presentation response", body.field("error_description"))
        val failure = assertIs<SessionFailure.VcPolicyViolations>(session.failure)
        assertEquals("1 credential policy violation(s)", failure.reason)
        assertEquals("Credential has expired", failure.violations.single().error)
    }

    @Test
    fun `an unparseable presentation is recorded naming the query and format`() = runTest {
        val session = fixture.session()
        val (status, body) = postDirect("not-a-jwt", session, workingStore)

        val expected = "Presentation for 'ids' could not be parsed as jwt_vc_json"
        assertEquals(HttpStatusCode.BadRequest, status)
        assertEquals("Invalid presentation response", body.field("error_description"))
        assertEquals(expected, session.failure!!.reason)
    }
}
