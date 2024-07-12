package id.walt.credentials.verification.models

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration
import kotlin.time.DurationUnit

@Serializable
data class PresentationVerificationResponseSurrogate(
    val results: List<PresentationResultEntrySurrogate>,
    val time: Duration,
    val policiesRun: Int,
) {
    constructor(original: PresentationVerificationResponse) : this(
        results = original.results.map { PresentationResultEntrySurrogate(it) },
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

   /* fun toJson() =
        JsonPresentationVerificationResponse(
            results = JsonArray(results.map {
                JsonObject(
                    mapOf(
                        "credential" to JsonPrimitive(it.credential),
                        "policies" to JsonArray(it.policyResults.map { it.toJsonElement() })
                    )
                )
            }),
            success = overallSuccess(),
            time = time.toString(DurationUnit.SECONDS, 4),
            policiesRun = policiesRun,
            policiesFailed = policiesFailed(),
            policiesSucceeded = policiesSucceeded()
        )*/
}
/*

@Serializable
data class JsonPresentationVerificationResponse(
    val results: JsonArray,
    val success: Boolean,
    val time: String,
    @SerialName("policies_run")
    val policiesRun: Int,
    @SerialName("policies_failed")
    val policiesFailed: Int,
    @SerialName("policies_succeeded")
    val policiesSucceeded: Int,
)
*/
