package id.walt.itb

import kotlinx.serialization.json.Json
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class ItbReportWriterTest {
    @Test
    fun unexecutedCasesRemainFailuresInAnInterruptedRunReport() {
        val directory = Files.createTempDirectory("itb-report-test-")
        try {
            val pending = ItbCaseResult("cs01v1", "tc_vci_006", null, ItbCaseResult.Outcome.NOT_RUN,
                ItbCaseResult.Phase.START, false, false, null, false, null,
                "2026-09-21T14:48:14Z", "2026-09-21T14:48:14Z")
            val interrupted = pending.copy(
                suite = "cs10-test-suite", case = "rv_iss_01",
                outcome = ItbCaseResult.Outcome.ERROR, phase = ItbCaseResult.Phase.INTERACTION,
                adapterInvoked = true, errorType = "IllegalStateException",
            )
            val report = ItbRunReport("0".repeat(40), "2026-09-21", listOf(pending, interrupted),
                keyAttestation = ItbRunReport.KeyAttestation.SYNTHETIC_TEST_FIXTURE)
            ItbReportWriter.write(directory, report)
            assertEquals(report, Json.decodeFromString<ItbRunReport>(Files.readString(directory.resolve("results.json"))))
            val junit = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(directory.resolve("junit.xml").toFile())
            assertEquals("2", junit.documentElement.getAttribute("tests"))
            assertEquals("2", junit.documentElement.getAttribute("failures"))
            assertEquals("0", junit.documentElement.getAttribute("skipped"))
            assertEquals("NOT_RUN", junit.getElementsByTagName("failure").item(0).attributes.getNamedItem("type").nodeValue)
            val summary = Files.readString(directory.resolve("summary.md"))
            assertContains(summary, "0/2 passed")
            assertContains(summary, "| rv_iss_01 | ERROR | IllegalStateException | incomplete |")
            assertContains(Files.readString(directory.resolve("summary.md")), "security, certification and status claims are simulated")
            assertContains(Files.readString(directory.resolve("results.json")), "\"keyAttestation\": \"SYNTHETIC_TEST_FIXTURE\"")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
