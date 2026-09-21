package id.walt.itb

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** A GITB report bound to the exact case and session started by this run. */
class ItbSessionReport private constructor(
    val caseId: String,
    val sessionId: String,
    val caseVersion: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val verdict: Verdict,
) {
    enum class Verdict { SUCCESS, FAILURE, UNDEFINED }

    // GITB can report SUCCESS while a session is still executing.
    val isComplete: Boolean get() = endedAt != null
    val passed: Boolean get() = isComplete && verdict == Verdict.SUCCESS

    companion object {
        private const val REPORT_NAMESPACE = "http://www.gitb.com/tr/v1/"
        private const val CORE_NAMESPACE = "http://www.gitb.com/core/v1/"

        fun parse(xml: String, expectedCaseId: String, expectedSessionId: String): ItbSessionReport {
            require(xml.length <= 4 * 1024 * 1024) { "ITB report exceeds the size limit" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
            require(root.namespaceURI == REPORT_NAMESPACE && root.localName == "TestCaseOverviewReport") {
                "Expected a GITB test case overview report"
            }
            val caseId = root.getAttribute("id")
            val sessionId = root.requiredText("sessionId")
            require(caseId == expectedCaseId && sessionId == expectedSessionId) {
                "ITB report does not belong to the requested case/session"
            }
            val startedAt = Instant.parse(root.requiredText("startTime"))
            val endedAt = root.child("endTime")?.textContent?.trim()?.let(Instant::parse)
            require(endedAt == null || endedAt >= startedAt) { "ITB session ends before it starts" }
            return ItbSessionReport(
                caseId = caseId,
                sessionId = sessionId,
                caseVersion = root.child("metadata")?.child("version", CORE_NAMESPACE)?.textContent?.trim()
                    ?.takeIf { it.isNotEmpty() },
                startedAt = startedAt,
                endedAt = endedAt,
                verdict = Verdict.valueOf(root.requiredText("result")),
            )
        }

        private fun Element.requiredText(name: String): String =
            requireNotNull(child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }) {
                "ITB report has no $name"
            }

        private fun Element.child(name: String, namespace: String = REPORT_NAMESPACE): Element? {
            val matches = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
                .filter { it.localName == name && it.namespaceURI == namespace }
            require(matches.size <= 1) { "Duplicate ITB report field: $name" }
            return matches.singleOrNull()
        }
    }
}
