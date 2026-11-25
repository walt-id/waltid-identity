package id.walt.openid4vp.conformance.report

import id.walt.openid4vp.conformance.config.ConformanceExecutionMode
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
    val DEFAULT_REPORT_ROOT: String
        get() = if (ConformanceExecutionMode.current == ConformanceExecutionMode.FULL_PLAN) {
            "build/reports/openid-conformance-full-plan"
        } else {
            "build/reports/openid-conformance"
        }

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
        val executionMode: String? = null,
        /** Product outcome, independent of the suite's screenshot/review state. */
        val verifierStatus: String? = null,
        val walletStatus: String? = null,
    )

    fun reportDir(role: Role, reportRoot: String = DEFAULT_REPORT_ROOT): Path =
        Path.of(reportRoot, role.directoryName)

    fun write(
        role: Role,
        entries: List<Entry>,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        mergeExisting: Boolean = true,
        producer: String? = null,
        mode: ConformanceExecutionMode = ConformanceExecutionMode.current,
    ) {
        require(mode != ConformanceExecutionMode.FULL_PLAN || !mergeExisting || !producer.isNullOrBlank()) {
            "Merged full-plan evidence requires a producer that owns the complete incoming result set"
        }
        val dir = reportDir(role, reportRoot)
        Files.createDirectories(dir)

        val merged = if (mergeExisting) {
            val existingFile = dir.resolve("results.json")
            val existing = if (Files.exists(existingFile)) {
                runCatching {
                    json.decodeFromString(ListSerializer(Entry.serializer()), Files.readString(existingFile))
                }.getOrElse { failure ->
                    if (mode == ConformanceExecutionMode.FULL_PLAN) {
                        throw IllegalStateException("Cannot merge malformed full-plan evidence: $existingFile", failure)
                    }
                    emptyList()
                }
            } else {
                emptyList()
            }
            // Merging lets independent test tasks contribute to one role report (the wallet profile
            // tasks each run a single profile). A [producer] additionally declares "these are all my
            // entries", so re-running it drops results it no longer produces - otherwise a renamed or
            // removed test lingers forever and is counted as a permanent failure.
            val retained = existing.filterNot { producer != null && it.producer == producer }
            if (mode == ConformanceExecutionMode.FULL_PLAN) {
                // A suite plan can offer the same module under multiple variants. The producer
                // replaces its previous complete run, while every case of this run stays visible.
                retained + entries.map { it.copy(producer = producer) }
            } else {
                val byName = LinkedHashMap<String, Entry>()
                retained.forEach { byName[it.name] = it }
                entries.forEach { byName[it.name] = it.copy(producer = producer) }
                byName.values.toList()
            }
        } else {
            entries.map { it.copy(producer = producer) }
        }

        Files.writeString(
            dir.resolve("results.json"),
            json.encodeToString(ListSerializer(Entry.serializer()), merged)
        )
        Files.writeString(dir.resolve("summary.md"), buildSummary(role, merged))
        println("Wrote ${role.title} conformance report to $dir (${merged.size} entries)")
    }

    /**
     * Records an unavailable suite when this role has no `summary.md` yet.
     *
     * Local tests may skip when the suite is absent, but this placeholder is never
     * accepted as conformance evidence. Required CI rejects it.
     */
    fun writeUnavailableIfEmpty(
        role: Role,
        reason: String,
        reportRoot: String = DEFAULT_REPORT_ROOT,
    ) {
        if (!Files.exists(reportDir(role, reportRoot).resolve("summary.md"))) {
            write(
                role = role,
                entries = listOf(
                    Entry(
                        name = "conformance-suite",
                        status = "unavailable",
                        error = reason,
                        accepted = false,
                    )
                ),
                reportRoot = reportRoot,
                mergeExisting = false,
                producer = "suite-availability",
            )
        }
        check(ConformanceExecutionMode.current != ConformanceExecutionMode.FULL_PLAN &&
            !System.getenv("CONFORMANCE_REQUIRED").equals("true", ignoreCase = true)) {
            "Required ${role.title} conformance suite is unavailable"
        }
    }

    fun writeTestPlanResults(
        role: Role,
        results: List<TestPlanResult>,
        conformanceHost: String? = null,
        conformancePort: Int? = null,
        reportRoot: String = DEFAULT_REPORT_ROOT,
        expectRejection: Boolean = false,
        producer: String? = null,
    ) {
        val mode = ConformanceExecutionMode.current
        val entries = results.map { result ->
            val accepted = result.isAccepted(expectRejection, mode)
            Entry(
                name = result.testName.takeIf { it != "unknown" } ?: result.conformanceTestId,
                status = when {
                    result.blockerReason != null -> "blocked"
                    mode == ConformanceExecutionMode.FULL_PLAN && result.skipReason != null ->
                        if (accepted) "not-applicable" else "blocked"
                    mode == ConformanceExecutionMode.FULL_PLAN && result.conformanceResult in setOf("BLOCKED", "SKIPPED") -> "blocked"
                    mode == ConformanceExecutionMode.FULL_PLAN && result.conformanceResult == "REVIEW" -> "review"
                    mode == ConformanceExecutionMode.FULL_PLAN && result.conformanceResult == "WARNING" -> "warning"
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
                error = result.blockerReason ?: result.skipReason ?: result.message.takeIf { !accepted },
                accepted = accepted,
                executionMode = mode.value.takeIf { mode == ConformanceExecutionMode.FULL_PLAN },
                verifierStatus = result.verifierStatus?.name,
                walletStatus = result.walletStatus,
            )
        }
        val reported = if (mode == ConformanceExecutionMode.FULL_PLAN && entries.isEmpty()) {
            listOf(Entry("conformance-plan", "unavailable", error = "Suite returned no module results", executionMode = mode.value))
        } else entries
        write(role, reported, reportRoot, producer = producer)
    }

    fun failIfNeeded(role: Role, entries: List<Entry>) {
        check(ConformanceExecutionMode.current != ConformanceExecutionMode.FULL_PLAN ||
            entries.any { it.status == "passed" && it.accepted }) { "Full-plan run contains no executed passes" }
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
        expectRejection: Boolean = false,
        mode: ConformanceExecutionMode = ConformanceExecutionMode.current,
    ) {
        check(mode != ConformanceExecutionMode.FULL_PLAN ||
            results.any { it.skipReason == null && it.isAccepted(expectRejection, mode) }) {
            "Full-plan run contains no executed passes"
        }
        val failed = results.count { !it.isAccepted(expectRejection, mode) }
        if (failed > 0) {
            error(
                "$failed ${role.title} conformance test(s) failed. " +
                        "See ${reportDir(role)}/summary.md"
            )
        }
    }

    private fun buildSummary(role: Role, entries: List<Entry>): String = buildString {
        appendLine("# ${role.title} Conformance Summary")
        appendLine()
        val skipped = entries.count { it.status in setOf("skipped", "not-applicable") }
        appendLine("- Total: ${entries.size}")
        appendLine("- Passed: ${entries.count { it.accepted && it.status == "passed" }}")
        appendLine("- Failed: ${entries.count { !it.accepted }}")
        if (skipped > 0) appendLine("- Skipped (not applicable to this variant): $skipped")
        appendLine()
        appendLine("<details>")
        appendLine("<summary>${role.title} results</summary>")
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
        appendLine()
        appendLine("</details>")
    }

    private fun logUrl(host: String?, port: Int?, testId: String?): String? {
        if (host.isNullOrBlank() || port == null || testId.isNullOrBlank() || testId == "N/A") return null
        return "https://$host:$port/log-detail.html?log=$testId"
    }

    private fun String.sanitizeMarkdownCell(): String = replace("\n", " ").replace("|", "\\|")
}

fun TestPlanResult.isAccepted(
    expectRejection: Boolean = false,
    mode: ConformanceExecutionMode = ConformanceExecutionMode.current,
): Boolean {
    if (blockerReason != null) return false
    if (mode == ConformanceExecutionMode.FULL_PLAN) {
        if (skipReason != null) return skipReason.startsWith("Not applicable to this variant:") ||
            (conformanceTestId.isNotBlank() && conformanceTestId != "N/A" &&
                conformanceStatus == "FINISHED" && conformanceResult == "SKIPPED")
        return conformanceTestId.isNotBlank() && conformanceTestId != "N/A" &&
            errorMessage == null && conformanceStatus == "FINISHED" && conformanceResult == "PASSED" &&
            isAccepted(expectRejection, ConformanceExecutionMode.CI)
    }
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
