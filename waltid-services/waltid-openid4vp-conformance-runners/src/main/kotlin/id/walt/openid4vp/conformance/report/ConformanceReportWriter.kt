package id.walt.openid4vp.conformance.report

import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes per-role OpenID conformance summaries under
 * `build/reports/openid-conformance/<role>/`.
 */
object ConformanceReportWriter {
    const val DEFAULT_REPORT_ROOT = "build/reports/openid-conformance"

    private val json = Json { prettyPrint = true }

    enum class Role(val directoryName: String, val title: String) {
        VP_VERIFIER("vp-verifier", "OpenID4VP Verifier"),
        VCI_ISSUER("vci-issuer", "OpenID4VCI Issuer"),
        VCI_WALLET("vci-wallet", "OpenID4VCI Wallet"),
        VP_WALLET("vp-wallet", "OpenID4VP Wallet"),
    }

    @Serializable
    data class Entry(
        val name: String,
        val status: String,
        val suiteResult: String? = null,
        val suiteStatus: String? = null,
        val testId: String? = null,
        val logUrl: String? = null,
        val error: String? = null,
        val accepted: Boolean = false,
    )

    fun reportDir(role: Role, reportRoot: String = DEFAULT_REPORT_ROOT): Path =
        Path.of(reportRoot, role.directoryName)

    fun write(
        role: Role,
        entries: List<Entry>,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
        mergeExisting: Boolean = true,
    ) {
        val dir = reportDir(role, reportRoot)
        Files.createDirectories(dir)

        val merged = if (mergeExisting) {
            val existingFile = dir.resolve("results.json")
            val existing = if (Files.exists(existingFile)) {
                runCatching {
                    json.decodeFromString(ListSerializer(Entry.serializer()), Files.readString(existingFile))
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val byName = LinkedHashMap<String, Entry>()
            existing.forEach { byName[it.name] = it }
            entries.forEach { byName[it.name] = it }
            byName.values.toList()
        } else {
            entries
        }

        Files.writeString(
            dir.resolve("results.json"),
            json.encodeToString(ListSerializer(Entry.serializer()), merged)
        )
        Files.writeString(dir.resolve("summary.md"), buildSummary(role, merged, allowFailure))
        println("Wrote ${role.title} conformance report to $dir (${merged.size} entries)")
    }

    fun writeTestPlanResults(
        role: Role,
        results: List<TestPlanResult>,
        conformanceHost: String? = null,
        conformancePort: Int? = null,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
        expectRejection: Boolean = false,
    ) {
        val entries = results.map { result ->
            val accepted = result.isAccepted(expectRejection)
            Entry(
                name = result.testName.takeIf { it != "unknown" } ?: result.conformanceTestId,
                status = when {
                    accepted -> "passed"
                    result.conformanceResult == "TIMEOUT" || result.walletStatus == "TIMEOUT" -> "timeout"
                    result.conformanceResult == "ERROR" || result.walletStatus == "ERROR" -> "error"
                    else -> "failed"
                },
                suiteResult = result.conformanceResult,
                suiteStatus = result.conformanceStatus ?: result.walletStatus ?: result.verifierStatus?.name,
                testId = result.conformanceTestId.takeIf { it != "N/A" },
                logUrl = logUrl(conformanceHost, conformancePort, result.conformanceTestId),
                error = result.message,
                accepted = accepted,
            )
        }
        write(role, entries, reportRoot, allowFailure)
    }

    fun failIfNeeded(role: Role, entries: List<Entry>, allowFailure: Boolean = ConformanceCiFlags.allowFailure()) {
        if (allowFailure) return
        val failed = entries.count { !it.accepted }
        if (failed > 0) {
            error(
                "$failed ${role.title} conformance test(s) failed. " +
                    "See ${reportDir(role)}/summary.md"
            )
        }
    }

    fun failIfNeededFromTestPlanResults(
        role: Role,
        results: List<TestPlanResult>,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
        expectRejection: Boolean = false,
    ) {
        if (allowFailure) return
        val failed = results.count { !it.isAccepted(expectRejection) }
        if (failed > 0) {
            error(
                "$failed ${role.title} conformance test(s) failed. " +
                    "See ${reportDir(role)}/summary.md"
            )
        }
    }

    private fun buildSummary(role: Role, entries: List<Entry>, allowFailure: Boolean): String = buildString {
        appendLine("# ${role.title} Conformance Summary")
        appendLine()
        appendLine("- Soft-fail (`CONFORMANCE_ALLOW_FAILURE`): `${if (allowFailure) "enabled" else "disabled"}`")
        appendLine("- Total: ${entries.size}")
        appendLine("- Passed: ${entries.count { it.accepted }}")
        appendLine("- Failed: ${entries.count { !it.accepted }}")
        appendLine()
        appendLine("| Test | Status | Suite | Log | Error |")
        appendLine("|------|--------|-------|-----|-------|")
        entries.forEach { entry ->
            val log = entry.logUrl?.let { "[log]($it)" } ?: ""
            appendLine(
                "| `${entry.name.sanitizeMarkdownCell()}` | `${entry.status}` | " +
                    "${entry.suiteResult ?: entry.suiteStatus ?: ""} | $log | " +
                    "${entry.error?.sanitizeMarkdownCell() ?: ""} |"
            )
        }
    }

    private fun logUrl(host: String?, port: Int?, testId: String?): String? {
        if (host.isNullOrBlank() || port == null || testId.isNullOrBlank() || testId == "N/A") return null
        return "https://$host:$port/log-detail.html?log=$testId"
    }

    private fun String.sanitizeMarkdownCell(): String = replace("\n", " ").replace("|", "\\|")
}

fun TestPlanResult.isAccepted(expectRejection: Boolean = false): Boolean {
    if (expectRejection) {
        return walletStatus == "REJECTED" || conformanceResult == "PASSED"
    }
    if (walletStatus != null) {
        return walletStatus == "PASSED"
    }
    return passed
}
