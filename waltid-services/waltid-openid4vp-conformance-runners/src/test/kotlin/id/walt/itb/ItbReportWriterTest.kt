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
            val report = ItbRunReport("0".repeat(40), "2026-09-21", listOf(pending))
            ItbReportWriter.write(directory, report)
            assertEquals(report, Json.decodeFromString<ItbRunReport>(Files.readString(directory.resolve("results.json"))))
            val junit = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(directory.resolve("junit.xml").toFile())
            assertEquals("1", junit.documentElement.getAttribute("tests"))
            assertEquals("1", junit.documentElement.getAttribute("failures"))
            assertEquals("0", junit.documentElement.getAttribute("skipped"))
            assertEquals("NOT_RUN", junit.getElementsByTagName("failure").item(0).attributes.getNamedItem("type").nodeValue)
            assertContains(Files.readString(directory.resolve("summary.md")), "0/1 passed")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
