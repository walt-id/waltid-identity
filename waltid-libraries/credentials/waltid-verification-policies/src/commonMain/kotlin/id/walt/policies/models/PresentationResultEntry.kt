package id.walt.policies.models

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class PresentationResultEntry(val credential: String, val policyResults: ArrayList<PolicyResult> = ArrayList())

@Serializable
data class PresentationResultEntrySurrogate(val credential: String, val policyResults: List<PolicyResultSurrogate>) {

    companion object {
        fun buildOffPresentationResultEntry(original: PresentationResultEntry) =
            runCatching {
                PresentationResultEntrySurrogate(
                    credential = original.credential,
                    policyResults = original.policyResults.map {
                        PolicyResultSurrogate(it)
                    })
            }.getOrElse { ex -> throw IllegalStateException("Could not build PresentationResultEntrySurrogate off PresentationResultEntry: $original", ex) }
    }
}
