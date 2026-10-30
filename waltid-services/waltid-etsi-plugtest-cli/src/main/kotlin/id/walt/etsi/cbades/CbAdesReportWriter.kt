package id.walt.etsi.cbades

import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.Indication
import kotlin.time.Clock

/**
 * Plugtest verification report: root element VALID / INVALID / INDETERMINATE, mandatory REPORT child with the test
 * case identifier, SUBINDICATION children with EN 319 102-1 sub-indications, plus walt.id specific details.
 */
object CbAdesReportWriter {
    fun rootElement(indication: Indication): String = when (indication) {
        Indication.TOTAL_PASSED, Indication.PASSED -> "VALID"
        Indication.TOTAL_FAILED, Indication.FAILED -> "INVALID"
        Indication.INDETERMINATE -> "INDETERMINATE"
    }

    fun write(testCase: String, result: DocumentValidationResult, participant: String, validator: String): String {
        val root = rootElement(result.indication)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<$root>")
            appendLine("  <REPORT>${esc(testCase)}</REPORT>")
            result.subIndication?.let { appendLine("  <SUBINDICATION>${it.name}</SUBINDICATION>") }
            appendLine("""  <VALIDATOR reporter="${esc(participant)}" generatedAt="${Clock.System.now()}">${esc(validator)}</VALIDATOR>""")
            result.documentChecks.forEach { appendLine("""  <CHECK status="${it.status}" id="${esc(it.id)}" ref="${esc(it.reference ?: "")}">${esc(it.message)}</CHECK>""") }
            for (s in result.signatures) {
                appendLine(
                    """  <SIGNATURE id="${esc(s.signatureId)}" indication="${s.indication}"""" +
                        (s.subIndication?.let { """ subIndication="${it.name}"""" } ?: "") +
                        (s.level?.let { """ level="CB-AdES-${it.code}"""" } ?: "") +
                        (s.claimedSigningTime?.let { """ claimedSigningTime="$it"""" } ?: "") +
                        """ bestSignatureTime="${s.bestSignatureTime}">""",
                )
                s.signingCertificate?.let { appendLine("    <SIGNER>${esc(it.subject.toString())}</SIGNER>") }
                if (s.certificateChain.isNotEmpty()) appendLine("    <CHAIN>${esc(s.certificateChain.joinToString(" -> ") { it.subject.commonName ?: it.subject.toString() })}</CHAIN>")
                s.timestamps.forEach { t ->
                    appendLine(
                        """    <TIMESTAMP type="${t.type}" indication="${t.indication}"""" + (t.subIndication?.let { """ subIndication="${it.name}"""" } ?: "") +
                            (t.genTime?.let { """ genTime="$it"""" } ?: "") + ">${esc(t.tsaCertificate?.subject?.toString() ?: "")}" +
                            (if (t.problems.isNotEmpty()) " (" + esc(t.problems.joinToString("; ")) + ")" else "") + "</TIMESTAMP>",
                    )
                }
                s.checks.filter { it.status != CheckStatus.INFO || it.id.startsWith("info:") }.forEach {
                    appendLine("""    <CHECK status="${it.status}" id="${esc(it.id)}" ref="${esc(it.reference ?: "")}">${esc(it.message)}</CHECK>""")
                }
                appendLine("  </SIGNATURE>")
            }
            appendLine("</$root>")
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
