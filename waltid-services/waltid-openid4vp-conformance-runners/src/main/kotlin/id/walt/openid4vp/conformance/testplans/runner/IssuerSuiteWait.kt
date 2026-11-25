package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Honors intentional waits declared by the pinned suite, without extending arbitrary stalled tests. */
internal object IssuerSuiteWait {
    private val waitSources = setOf("WaitForExpiry", "WaitFor62Seconds")
    private val pausing = Regex("Pausing for ([0-9]+) seconds")

    fun completionDeadline(log: List<TestLogEntry>, graceMilliseconds: Long = 60_000L): Long? {
        val event = log.lastOrNull { it.src in waitSources } ?: return null
        val seconds = pausing.matchEntire(event.msg.orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val timestamp = (event.time as? JsonPrimitive)?.longOrNull ?: return null
        return runCatching { Math.addExact(Math.addExact(timestamp, Math.multiplyExact(seconds, 1_000L)), graceMilliseconds) }
            .getOrNull()
    }
}
