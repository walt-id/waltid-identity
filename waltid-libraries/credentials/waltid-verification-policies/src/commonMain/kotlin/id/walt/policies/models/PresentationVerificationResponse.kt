package id.walt.policies.models

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration

@Serializable
data class PresentationVerificationResponseSurrogate(
    val results: List<PresentationResultEntrySurrogate>,
    val time: Duration,
    val policiesRun: Int,
    val policiesSuccessful: Int = results.sumOf { it.policyResults.count { it.isSuccess } },
    val policiesFailed: Int = results.sumOf { it.policyResults.count { !it.isSuccess } },
) {
    constructor(original: PresentationVerificationResponse) : this(
        results = original.results.map { PresentationResultEntrySurrogate.buildOffPresentationResultEntry(it) },
        time = original.time,
        policiesRun = original.policiesRun
    )
}

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class PresentationVerificationResponse(
    val results: ArrayList<PresentationResultEntry>,
    val time: Duration,
    val policiesRun: Int,
) {

    fun overallSuccess() = results.all { it.policyResults.all { it.isSuccess() } }
    fun policiesFailed() = results.flatMap { it.policyResults }.count { !it.isSuccess() }
    fun policiesSucceeded() = results.flatMap { it.policyResults }.count { it.isSuccess() }

}
