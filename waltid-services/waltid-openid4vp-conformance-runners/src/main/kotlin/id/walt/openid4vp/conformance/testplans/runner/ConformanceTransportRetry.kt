package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.SuiteReachedTerminalBeforeReady
import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Decides when a conformance module failed because the tunnel or the socket died, rather than
 * because the wallet or verifier produced a verdict.
 *
 * A Cloudflare Quick Tunnel answers 502, 503, or 504 when it cannot reach the local service. The
 * suite then records that status as the HTTP result it observed (`actual` on a failed check, or
 * `response_status_code` on the HTTP line), and the verifier session stays
 * [VerificationSessionStatus.UNUSED] because the presentation never arrived. That is not a
 * rejection and not an acceptance. A verifier that did answer — including with 200 when the suite
 * required 4xx — has left UNUSED, so it is not retried.
 *
 * The same gateway status also aborts a module as `INTERRUPTED` before it has a verdict: the
 * issuer and VCI wallet see that status directly, and the verifier and VP wallet see it as
 * [SuiteReachedTerminalBeforeReady] while they are still waiting to present. A module that
 * finished is not retried.
 */
internal object ConformanceTransportRetry {
    private val gatewayStatuses = setOf(502, 503, 504)

    fun unusedVerifierAfterGateway(
        verifierStatus: VerificationSessionStatus?,
        passed: Boolean,
        log: List<TestLogEntry>,
    ): Boolean {
        if (passed || verifierStatus != VerificationSessionStatus.UNUSED) return false
        return log.any { entry ->
            isGatewayStatus(entry.responseStatusCode) ||
                (entry.result.equals("FAILURE", ignoreCase = true) && isGatewayStatus(entry.actual))
        }
    }

    /**
     * True when [error] or one of its causes is a connection reset.
     *
     * Cancellation is never a transport retry, even when a cancelled call's message mentions the
     * socket.
     */
    fun isConnectionReset(error: Throwable): Boolean {
        if (error is CancellationException) return false
        var current: Throwable? = error
        while (current != null) {
            if (current.message?.contains("connection reset", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    /**
     * True when the suite aborted a module before a verdict because the tunnel returned a gateway
     * status.
     *
     * `INTERRUPTED` plus `502`, `503`, or `504` on `response_status_code` or `actual` is the shape
     * the issuer and VCI wallet observe (often a credential-issuer metadata fetch). A module that
     * finished, including a negative test that failed its assertion, is not retried.
     */
    fun interruptedByGateway(status: String?, log: List<TestLogEntry>): Boolean {
        if (!status.equals("INTERRUPTED", ignoreCase = true)) return false
        return log.any { entry -> isGatewayStatus(entry.responseStatusCode) || isGatewayStatus(entry.actual) }
    }

    /**
     * True when [error] is the suite reaching `INTERRUPTED` before `WAITING` and [log] shows a
     * gateway status.
     *
     * `FINISHED` before `WAITING` is a verdict and is not retried. Cancellation is never a
     * transport retry.
     */
    fun interruptedBeforeReadyByGateway(error: Throwable, log: List<TestLogEntry>): Boolean {
        if (error is CancellationException) return false
        val terminal = error as? SuiteReachedTerminalBeforeReady ?: return false
        return interruptedByGateway(terminal.status, log)
    }

    private fun isGatewayStatus(value: JsonElement?): Boolean {
        val primitive = value as? JsonPrimitive ?: return false
        val status = primitive.intOrNull ?: primitive.content.toIntOrNull()
        return status in gatewayStatuses
    }
}
