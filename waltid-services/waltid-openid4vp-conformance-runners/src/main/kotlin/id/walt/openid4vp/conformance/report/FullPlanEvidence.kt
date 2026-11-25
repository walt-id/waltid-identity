package id.walt.openid4vp.conformance.report

import id.walt.openid4vp.conformance.config.ConformanceExecutionMode
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Local gap-run inventory. It deliberately excludes configuration values and fixture keys. */
internal object FullPlanEvidence {
    const val SUITE_REVISION = "23353b36957d6466010de767c812d2c91ed37638"
    const val SUITE_VERSION = "5.3.4"
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun requirePinnedSuite(server: JsonObject) {
        val version = server["version"]?.jsonPrimitive?.contentOrNull
        val revision = server["revision"]?.jsonPrimitive?.contentOrNull
        require(version == SUITE_VERSION && revision != null && revision.length >= 7 && SUITE_REVISION.startsWith(revision)) {
            "Full-plan run requires suite $SUITE_VERSION at $SUITE_REVISION; received version=$version revision=$revision"
        }
    }

    @Serializable
    private data class Inventory(
        val executionMode: String = "full-plan",
        val suiteVersion: String = SUITE_VERSION,
        val suiteRevision: String = SUITE_REVISION,
        val server: JsonObject,
        val plan: CreateTestPlanResponse,
        val variant: String?,
        val configurationSha256: String,
    )

    @Serializable
    private data class Coverage(
        val module: CreateTestPlanResponse.Module,
        val status: String,
        val testId: String? = null,
        val suiteStatus: String? = null,
        val suiteResult: String? = null,
        val error: String? = null,
    )

    fun record(planId: String, index: Int, module: CreateTestPlanResponse.Module, result: TestPlanResult) {
        record(planId, index, module, when {
            result.blockerReason != null -> "blocked"
            result.skipReason?.startsWith("Not applicable to this variant:") == true ||
                (result.skipReason != null && result.conformanceStatus == "FINISHED" && result.conformanceResult == "SKIPPED") -> "suite-inapplicable"
            result.skipReason != null || result.conformanceResult == "BLOCKED" -> "blocked"
            result.conformanceTestId == "N/A" -> "blocked"
            else -> "executed"
        }, result.conformanceTestId, result.conformanceStatus, result.conformanceResult,
            result.blockerReason ?: result.skipReason ?: result.errorMessage)
    }

    fun record(
        planId: String, index: Int, module: CreateTestPlanResponse.Module, status: String,
        testId: String? = null, suiteStatus: String? = null, suiteResult: String? = null, error: String? = null,
        mode: ConformanceExecutionMode = ConformanceExecutionMode.current,
        reportRoot: String = ConformanceReportWriter.DEFAULT_REPORT_ROOT,
    ) {
        if (mode != ConformanceExecutionMode.FULL_PLAN) return
        require(planId.matches(Regex("[A-Za-z0-9_-]+")) && index >= 0)
        val dir = Path.of(reportRoot, "coverage", planId)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("$index.json"), json.encodeToString(Coverage(module, status, testId, suiteStatus, suiteResult, error)))
    }

    fun write(
        server: JsonObject, plan: CreateTestPlanResponse, variant: String?, configuration: JsonObject,
        reportRoot: String = ConformanceReportWriter.DEFAULT_REPORT_ROOT,
    ) {
        require(plan.id.matches(Regex("[A-Za-z0-9_-]+"))) { "Unexpected suite plan identifier" }
        val dir = Path.of(reportRoot, "plans")
        Files.createDirectories(dir)
        val hash = MessageDigest.getInstance("SHA-256").digest(configuration.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        Files.writeString(dir.resolve("${plan.id}.json"), json.encodeToString(Inventory(
            server = server,
            plan = plan,
            variant = variant,
            configurationSha256 = hash,
        )))
        plan.modules.forEachIndexed { index, module ->
            record(plan.id, index, module, "blocked", error = "No execution result recorded for this offered module",
                mode = ConformanceExecutionMode.FULL_PLAN, reportRoot = reportRoot)
        }
        check(plan.modules.isNotEmpty()) { "Suite returned an empty full plan; inventory retained at $dir" }
    }
}
