package id.walt.openid4vp.conformance

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
    fun retriesWhenAnIssuerModuleWasInterruptedAfterAGatewayStatus() {
        for (status in listOf(502, 503, 504)) {
            val log = lenientJson.decodeFromString<List<TestLogEntry>>(
                """[{"msg":"HTTP response","response_status_code":$status}]""",
            )
            assertTrue(
                ConformanceTransportRetry.issuerInterruptedByGateway("INTERRUPTED", log),
                "HTTP $status",
            )
        }
        val textual = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":"502"}]""",
        )
        assertTrue(ConformanceTransportRetry.issuerInterruptedByGateway("interrupted", textual))
    }

    @Test
    fun doesNotRetryAFinishedIssuerModuleOrANonGatewayInterrupt() {
        val gateway = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":502}]""",
        )
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway("FINISHED", gateway))
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway("WAITING", gateway))
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway(null, gateway))
        val clientError = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":400,"result":"FAILURE"}]""",
        )
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway("INTERRUPTED", clientError))
        val nonNumeric = lenientJson.decodeFromString<List<TestLogEntry>>(
            """[{"response_status_code":{"status":502}}]""",
        )
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway("INTERRUPTED", nonNumeric))
        assertFalse(ConformanceTransportRetry.issuerInterruptedByGateway("INTERRUPTED", emptyList()))
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
