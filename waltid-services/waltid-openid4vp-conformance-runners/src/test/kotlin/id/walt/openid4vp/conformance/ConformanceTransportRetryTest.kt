package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.http.SuiteReachedTerminalBeforeReady
import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.runner.ConformanceTransportRetry
import id.walt.openid4vp.conformance.utils.JsonUtils.lenientJson
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConformanceTransportRetryTest {
    @Test
    fun retriesWhenTheSuiteObservedAGatewayStatusAndTheVerifierNeverSawThePresentation() {
        for (status in listOf(502, 503, 504)) {
            val log = lenientJson.decodeFromString<List<TestLogEntry>>(
                """[{"result":"FAILURE","src":"EnsureHttpStatusCodeIs4xx","actual":$status}]""",
            )
            assertTrue(
                ConformanceTransportRetry.unusedVerifierAfterGateway(
                    VerificationSessionStatus.UNUSED,
                    passed = false,
                    log = log,
                ),
                "HTTP $status",
            )
        }
    }

    @Test
    fun doesNotRetryWhenTheVerifierAnsweredOrTheSuiteSawANonGatewayStatus() {
        val gateway = failure(502)
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.SUCCESSFUL,
                passed = false,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.FAILED,
                passed = false,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = true,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = false,
                log = failure(400),
            ),
        )
        val nonNumeric = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"result":"FAILURE","actual":{"status":502}}]""",
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = false,
                log = nonNumeric,
            ),
        )
    }

    @Test
    fun retriesAnUnusedVerifierWhenTheGatewayStatusIsOnlyOnTheHttpLine() {
        for (status in listOf(502, 503, 504)) {
            val log = lenientJson.decodeFromString<List<TestLogEntry>>(
                """[{"msg":"HTTP response","response_status_code":$status}]""",
            )
            assertTrue(
                ConformanceTransportRetry.unusedVerifierAfterGateway(
                    VerificationSessionStatus.UNUSED,
                    passed = false,
                    log = log,
                ),
                "HTTP $status",
            )
        }
        val textual = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":"502"}]""",
        )
        assertTrue(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = false,
                log = textual,
            ),
        )
        val gateway = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":502}]""",
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.SUCCESSFUL,
                passed = false,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.FAILED,
                passed = false,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = true,
                log = gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.unusedVerifierAfterGateway(
                VerificationSessionStatus.UNUSED,
                passed = false,
                log = lenientJson.decodeFromString("""[{"response_status_code":400}]"""),
            ),
        )
    }

    @Test
    fun issuerAndVciWalletRetryWhenInterruptedAfterAGatewayStatus() {
        for (status in listOf(502, 503, 504)) {
            val httpLine = lenientJson.decodeFromString<List<TestLogEntry>>(
                """[{"msg":"HTTP response","response_status_code":$status}]""",
            )
            val compared = lenientJson.decodeFromString<List<TestLogEntry>>(
                """[{"result":"FAILURE","actual":$status}]""",
            )
            assertTrue(ConformanceTransportRetry.interruptedByGateway("INTERRUPTED", httpLine), "HTTP $status")
            assertTrue(ConformanceTransportRetry.interruptedByGateway("INTERRUPTED", compared), "actual $status")
        }
        val textual = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":"502"}]""",
        )
        assertTrue(ConformanceTransportRetry.interruptedByGateway("interrupted", textual))
    }

    @Test
    fun doesNotRetryAFinishedModuleOrANonGatewayInterrupt() {
        val gateway = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":502}]""",
        )
        assertFalse(ConformanceTransportRetry.interruptedByGateway("FINISHED", gateway))
        assertFalse(ConformanceTransportRetry.interruptedByGateway("WAITING", gateway))
        assertFalse(ConformanceTransportRetry.interruptedByGateway(null, gateway))
        val clientError = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":400,"result":"FAILURE","actual":400}]""",
        )
        assertFalse(ConformanceTransportRetry.interruptedByGateway("INTERRUPTED", clientError))
        val nonNumeric = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":{"status":502},"actual":{"status":502}}]""",
        )
        assertFalse(ConformanceTransportRetry.interruptedByGateway("INTERRUPTED", nonNumeric))
        assertFalse(ConformanceTransportRetry.interruptedByGateway("INTERRUPTED", emptyList()))
    }

    @Test
    fun verifierAndVpWalletRetryWhenInterruptedBeforeWaitingAfterAGatewayStatus() {
        val gateway = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"msg":"HTTP response","response_status_code":502}]""",
        )
        val compared = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"result":"FAILURE","actual":503}]""",
        )
        assertTrue(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                SuiteReachedTerminalBeforeReady("J2SkEWsz1vBJ5Ug", "INTERRUPTED"),
                gateway,
            ),
        )
        assertTrue(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                SuiteReachedTerminalBeforeReady("test", "interrupted"),
                compared,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                SuiteReachedTerminalBeforeReady("test", "FINISHED"),
                gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                SuiteReachedTerminalBeforeReady("test", "INTERRUPTED"),
                lenientJson.decodeFromString("""[{"response_status_code":400,"actual":400}]"""),
            ),
        )
        assertFalse(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                IllegalStateException(
                    "Test test reached terminal status INTERRUPTED before it was ready for presentation",
                ),
                gateway,
            ),
        )
        assertFalse(
            ConformanceTransportRetry.interruptedBeforeReadyByGateway(
                CancellationException("Connection reset"),
                gateway,
            ),
        )
    }

    @Test
    fun connectionResetIsRetryableThroughTheCauseChain() {
        assertTrue(ConformanceTransportRetry.isConnectionReset(SocketException("Connection reset")))
        assertTrue(
            ConformanceTransportRetry.isConnectionReset(
                IOException("request failed", SocketException("Connection reset by peer")),
            ),
        )
        assertFalse(ConformanceTransportRetry.isConnectionReset(IOException("private test credentials")))
        assertFalse(ConformanceTransportRetry.isConnectionReset(CancellationException("Connection reset")))
    }

    private fun failure(status: Int): List<TestLogEntry> =
        lenientJson.decodeFromString("""[{"result":"FAILURE","actual":$status}]""")
}
