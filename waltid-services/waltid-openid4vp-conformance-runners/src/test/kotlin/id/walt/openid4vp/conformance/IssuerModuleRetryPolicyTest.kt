package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.runner.IssuerModuleRetryPolicy
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestCompletionTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerModuleRetryPolicyTest {

    private val waitStage = IssuerModuleRetryPolicy.WAIT_STAGE
    private val stuckWaiting = IssuerTestCompletionTimeoutException("Test abc is stuck in WAITING status after 60 seconds.")

    @Test
    fun stuckWaitingModuleWithoutResultIsRetried() {
        // Run 37761351742: fail-unknown-credential-configuration stayed WAITING with no result.
        assertTrue(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, "WAITING", null))
        assertTrue(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, "RUNNING", "UNKNOWN"))
        // Status could not even be re-read after the timeout.
        assertTrue(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, null, null))
    }

    @Test
    fun socketFailureWhileWaitingIsRetried() {
        // Run 37749171543: "Wait for test completion: SocketException".
        assertTrue(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, SocketException("Connection reset"), null, null))
        assertTrue(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, ConnectException("refused"), "WAITING", null))
        assertTrue(
            IssuerModuleRetryPolicy.isTransientNoResult(
                waitStage, IOException("wrapped", SocketException("Connection reset")), null, null
            )
        )
    }

    @Test
    fun genuineSuiteResultsAreNeverRetried() {
        for (result in listOf("FAILED", "WARNING", "PASSED", "SKIPPED", "REVIEW")) {
            assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, "FINISHED", result), result)
            assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, "RUNNING", result), result)
        }
        assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, stuckWaiting, "INTERRUPTED", null))
    }

    @Test
    fun otherStagesAndOtherErrorsAreNotRetried() {
        for (stage in listOf("Create test", "Read test result", "Check proof evidence")) {
            assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(stage, stuckWaiting, "WAITING", null), stage)
            assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(stage, SocketException("reset"), null, null), stage)
        }
        // Generic errors while waiting (e.g. our issuer failing to create a credential offer) are product signals.
        assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, IllegalStateException("offer failed"), "WAITING", null))
        assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, IllegalArgumentException("blank offer"), "WAITING", null))
        assertFalse(IssuerModuleRetryPolicy.isTransientNoResult(waitStage, IOException("HTTP 500"), "WAITING", null))
    }

    @Test
    fun completionTimeoutStaysAnIllegalStateException() {
        // Keeps existing handling, compact messages and BLOCKED classification ("waiting" in message).
        assertTrue(stuckWaiting is IllegalStateException)
    }
}
