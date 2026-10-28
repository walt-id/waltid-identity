package id.walt.openid4vp.conformance.testplans.runner

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
 * suite then records that status as the HTTP result it observed, and the verifier session stays
 * [VerificationSessionStatus.UNUSED] because the presentation never arrived. That is not a
 * rejection and not an acceptance. A verifier that did answer — including with 200 when the suite
 * required 4xx — has left UNUSED, so it is not retried.
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
            entry.result.equals("FAILURE", ignoreCase = true) && httpStatus(entry.actual) in gatewayStatuses
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

    private fun httpStatus(actual: JsonElement?): Int? = (actual as? JsonPrimitive)?.intOrNull
}
