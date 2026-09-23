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
        /** Which runner produced this entry; see [write]. */
        val producer: String? = null,
    )

    fun reportDir(role: Role, reportRoot: String = DEFAULT_REPORT_ROOT): Path =
        Path.of(reportRoot, role.directoryName)

    fun write(
        role: Role,
        entries: List<Entry>,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
        mergeExisting: Boolean = true,
        producer: String? = null,
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
            // Merging lets independent test tasks contribute to one role report (the wallet profile
            // tasks each run a single profile). A [producer] additionally declares "these are all my
            // entries", so re-running it drops results it no longer produces - otherwise a renamed or
            // removed test lingers forever and is counted as a permanent failure.
            val retained = existing.filterNot { producer != null && it.producer == producer }
            val byName = LinkedHashMap<String, Entry>()
            retained.forEach { byName[it.name] = it }
            entries.forEach { byName[it.name] = it.copy(producer = producer) }
            byName.values.toList()
        } else {
            entries.map { it.copy(producer = producer) }
        }

        Files.writeString(
            dir.resolve("results.json"),
            json.encodeToString(ListSerializer(Entry.serializer()), merged)
        )
        Files.writeString(dir.resolve("summary.md"), buildSummary(role, merged, allowFailure))
        println("Wrote ${role.title} conformance report to $dir (${merged.size} entries)")
    }

    /**
     * Writes a skipped-suite placeholder when this role has no `summary.md` yet.
     *
     * Used by conformance test classes after a skipped run so GitHub Actions can still
     * publish the same per-role heading and table shape as a completed verifier report.
     */
    fun writeSkippedIfEmpty(
        role: Role,
        reason: String,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
    ) {
        if (Files.exists(reportDir(role, reportRoot).resolve("summary.md"))) return
        write(
            role = role,
            entries = listOf(
                Entry(
                    name = "conformance-suite",
                    status = "skipped",
                    error = reason,
                    accepted = true,
                )
            ),
            reportRoot = reportRoot,
            allowFailure = allowFailure,
            mergeExisting = false,
            producer = "suite-availability",
        )
    }

    fun writeTestPlanResults(
        role: Role,
        results: List<TestPlanResult>,
        conformanceHost: String? = null,
        conformancePort: Int? = null,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        allowFailure: Boolean = ConformanceCiFlags.allowFailure(),
        expectRejection: Boolean = false,
        producer: String? = null,
    ) {
        val entries = results.map { result ->
            val accepted = result.isAccepted(expectRejection)
            Entry(
                name = result.testName.takeIf { it != "unknown" } ?: result.conformanceTestId,
                status = when {
                    result.skipReason != null -> "skipped"
                    accepted -> "passed"
                    result.conformanceResult == "TIMEOUT" || result.walletStatus == "TIMEOUT" -> "timeout"
                    result.conformanceResult == "ERROR" || result.walletStatus == "ERROR" -> "error"
                    else -> "failed"
                },
                suiteResult = result.conformanceResult,
                suiteStatus = result.conformanceStatus ?: result.walletStatus ?: result.verifierStatus?.name,
                testId = result.conformanceTestId.takeIf { it != "N/A" },
                logUrl = logUrl(conformanceHost, conformancePort, result.conformanceTestId),
                error = result.skipReason ?: result.message.takeIf { !accepted },
                accepted = accepted,
            )
        }
        write(role, entries, reportRoot, allowFailure, producer = producer)
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
        producer: String? = null,
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
        val skipped = entries.count { it.status == "skipped" }
        appendLine("- Total: ${entries.size}")
        appendLine("- Passed: ${entries.count { it.accepted && it.status != "skipped" }}")
        appendLine("- Failed: ${entries.count { !it.accepted }}")
        if (skipped > 0) appendLine("- Skipped (not applicable to this variant): $skipped")
        appendLine()
        appendLine("| Test | Status | Suite | Log | Error |")
        appendLine("|------|--------|-------|-----|-------|")
        entries.forEach { entry ->
            val display = ConformanceReportFormat.displayName(entry.name)
            val test = listOfNotNull(display.title, display.variant).joinToString(" · ")
            val log = entry.logUrl?.let { "[log]($it)" } ?: ""
            appendLine(
                "| `${test.sanitizeMarkdownCell()}` | `${entry.status}` | " +
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
    // A module that was never run cannot have failed; see TestPlanResult.skipReason.
    if (skipReason != null) return true
    if (expectRejection) {
        return walletStatus == "REJECTED" || conformanceResult == "PASSED"
    }
    if (walletStatus != null) {
        return walletStatus == "PASSED"
    }
    return passed
}
