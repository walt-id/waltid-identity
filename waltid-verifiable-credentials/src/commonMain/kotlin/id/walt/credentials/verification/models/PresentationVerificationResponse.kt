package id.walt.credentials.verification.models

import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.time.DurationUnit

@OptIn(ExperimentalJsExport::class)
@JsExport
data class PresentationVerificationResponse(
    val results: ArrayList<PresentationResultEntry>,
    val time: Duration,
    val policiesRun: Int
) {

    fun overallSuccess() = results.all { it.policyResults.all { it.isSuccess() } }
    fun policiesFailed() = results.flatMap { it.policyResults }.count { !it.isSuccess() }
    fun policiesSucceeded() = results.flatMap { it.policyResults }.count { it.isSuccess() }

    fun toJson() = buildJsonObject {
        putJsonArray("results") {
            results.forEach {
                addJsonObject {
                    put("credential", it.credential)
                    put("policies", JsonArray(it.policyResults.map { it.toJsonResult() }))
                }
            }
        }
        put("success", overallSuccess())
        put("time", time.toString(DurationUnit.SECONDS, 4))
        put("policies_run", policiesRun)
        put("policies_failed", policiesFailed())
        put("policies_succeeded", policiesSucceeded())
    }
}

