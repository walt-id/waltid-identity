package id.walt.itb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItbSessionReportTest {
    private val report = requireNotNull(javaClass.getResource("/itb/vci006-success.xml")).readText()
    private val sessionId = "00000000-0000-0000-0000-000000000001"

    private fun parse(xml: String = report) = ItbSessionReport.parse(xml, "tc_vci_006", sessionId)

    @Test
    fun `reads a completed deployed ITB report without retaining protocol payloads`() {
        val parsed = parse()
        assertEquals("1.0", parsed.caseVersion)
        assertEquals("tc_vci_006", parsed.caseId)
        assertTrue(parsed.passed)
    }

    @Test
    fun `success without end time remains running`() {
        val parsed = parse(report.replace(Regex("<endTime>.*?</endTime>"), ""))
        assertFalse(parsed.isComplete)
        assertFalse(parsed.passed)
    }

    @Test
    fun `rejects unrelated historical reports and ambiguous fields`() {
        assertFails { ItbSessionReport.parse(report, "tc_vci_001", sessionId) }
        assertFails { ItbSessionReport.parse(report, "tc_vci_006", "another-session") }
        assertFails { parse(report.replace("<result>SUCCESS</result>", "<result>SUCCESS</result><result>FAILURE</result>")) }
    }

    @Test
    fun `undefined and failed terminal reports never pass`() {
        for (verdict in listOf("UNDEFINED", "FAILURE")) {
            val parsed = parse(report.replace("<result>SUCCESS</result>", "<result>$verdict</result>"))
            assertTrue(parsed.isComplete)
            assertFalse(parsed.passed)
        }
        assertFails { parse(report.replace("<result>SUCCESS</result>", "<result>NEW_STATUS</result>")) }
    }

    @Test
    fun `rejects entity expansion and invalid session chronology`() {
        assertFails { parse(report.replace("<TestCaseOverviewReport", "<!DOCTYPE report [<!ENTITY sample SYSTEM 'file:///nonexistent'>]><TestCaseOverviewReport")) }
        assertFails { parse(report.replace("2026-09-21T14:49:58.000Z", "2026-09-21T14:00:00.000Z")) }
    }

    @Test
    fun `loads all deployed initial cases with distinct identities`() {
        val catalogue = ItbCatalogue.initialWalletCases()
        assertEquals(mapOf("cs01v1" to 7, "cs02v1" to 4, "cts07" to 1, "ts12" to 9),
            catalogue.suites.associate { it.id to it.cases.size })
        val suite = catalogue.suites.first()
        assertFails { catalogue.copy(suites = listOf(suite, suite)) }
        assertFails { catalogue.copy(suites = listOf(suite.copy(cases = suite.cases + suite.cases.first()))) }
    }
}
