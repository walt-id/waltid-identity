@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.itb

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
data class ItbRunReport(
    val buildRevision: String,
    val catalogueObservedOn: String,
    val cases: List<ItbCaseResult>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val walletExecution: WalletExecution = WalletExecution.JVM_SOFTWARE_DIAGNOSTIC,
    val androidApkSha256: String? = null,
) {
    enum class WalletExecution { JVM_SOFTWARE_DIAGNOSTIC, ANDROID_NATIVE_DIAGNOSTIC }
}

/** Only bounded outcome metadata is published; raw reports, browser state and protocol payloads stay private. */
object ItbReportWriter {
    fun write(directory: Path, report: ItbRunReport) {
        Files.createDirectories(directory)
        writeAtomically(directory.resolve("results.json"), Json { prettyPrint = true }.encodeToString(report))
        val failures = report.cases.count { it.outcome != ItbCaseResult.Outcome.PASSED }
        writeAtomically(directory.resolve("summary.md"), buildString {
            appendLine("# WeBuild ITB wallet sessions — CONDITIONAL DIAGNOSTIC")
            appendLine()
            appendLine("This build may relax identified reference-service checks. Inspect its exact source revision; passes are not strict interoperability or conformance results.")
            appendLine()
            appendLine("Build: `${report.buildRevision}`. Catalogue: ${report.catalogueObservedOn}.")
            appendLine("Wallet execution: ${report.walletExecution}.")
            report.androidApkSha256?.let { appendLine("Android test APK SHA-256: `$it`.") }
            appendLine("${report.cases.size - failures}/${report.cases.size} passed; $failures non-passing.")
            appendLine()
            appendLine("| Suite | Case | Runner result | Reason | Wallet | ITB verdict | Session |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            report.cases.forEach { result ->
                val wallet = when {
                    result.walletSucceeded -> "succeeded"
                    result.outcome == ItbCaseResult.Outcome.AUTH_UNAVAILABLE -> "not authorized"
                    result.adapterInvoked -> "failed"
                    else -> "not run"
                }
                val reason = result.errorCode ?: result.errorType ?: "—"
                appendLine("| ${result.suite} | ${result.case} | ${result.outcome} | $reason | $wallet | ${result.testBedVerdict ?: "unavailable"} | ${result.session ?: "not started"} |")
            }
            appendLine()
            appendLine("Wallet protocol execution with a portal interaction bridge. Native mode uses operator authentication on Android; neither mode qualifies transaction-consent UI, browser/OS DC API delivery or formal SCA assurance.")
        })
        writeAtomically(directory.resolve("junit.xml"), buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<testsuite name=\"WeBuild ITB DIAGNOSTIC\" tests=\"${report.cases.size}\" failures=\"$failures\" errors=\"0\" skipped=\"0\">")
            report.cases.forEach { result ->
                appendLine("  <testcase classname=\"${escape(result.suite)}\" name=\"${escape(result.case)}\">")
                if (result.outcome != ItbCaseResult.Outcome.PASSED) {
                    appendLine("    <failure type=\"${result.outcome}\" message=\"${result.phase}: ${escape(result.errorType ?: result.outcome.name)}\">" +
                        "ITB=${result.testBedVerdict ?: "unavailable"}; session=${escape(result.session ?: "not started")}; " +
                        "adapterInvoked=${result.adapterInvoked}; cleanupFailed=${result.cleanupFailed}</failure>")
                }
                appendLine("  </testcase>")
            }
            appendLine("</testsuite>")
        })
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun writeAtomically(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, ".itb-report-", ".tmp")
        try {
            Files.writeString(temporary, content)
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
