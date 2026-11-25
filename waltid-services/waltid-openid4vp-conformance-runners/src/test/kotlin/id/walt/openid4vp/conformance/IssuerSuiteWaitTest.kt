package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.runner.IssuerSuiteWait
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssuerSuiteWaitTest {
    @Test
    fun intentionalExpiryChecksGetTheirDeclaredWaitPlusTheNormalProcessingWindow() {
        assertEquals(123_000L, IssuerSuiteWait.completionDeadline(listOf(TestLogEntry(
            src = "WaitFor62Seconds", msg = "Pausing for 62 seconds", time = JsonPrimitive(1_000L)))))
        assertEquals(152_000L, IssuerSuiteWait.completionDeadline(listOf(TestLogEntry(
            src = "WaitForExpiry", msg = "Pausing for 90 seconds", time = JsonPrimitive(2_000L)))))
    }

    @Test
    fun completedUnrelatedAndMalformedEventsCannotExtendAStalledTest() {
        val pause = TestLogEntry(src = "WaitForExpiry", msg = "Pausing for 90 seconds", time = JsonPrimitive(2_000L))
        assertNull(IssuerSuiteWait.completionDeadline(listOf(pause, pause.copy(msg = "Woke up after 90 seconds sleep"))))
        assertNull(IssuerSuiteWait.completionDeadline(listOf(pause.copy(src = "OtherCondition"))))
        assertNull(IssuerSuiteWait.completionDeadline(listOf(pause.copy(time = null))))
        assertNull(IssuerSuiteWait.completionDeadline(listOf(pause.copy(msg = "Pausing for 9223372036854775807 seconds"))))
    }
}
