package id.walt.credentials.verification.models

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
data class PresentationResultEntry(val credential: String, val policyResults: ArrayList<PolicyResult> = ArrayList())

@Serializable
data class PresentationResultEntrySurrogate(val credential: String, val policyResults: List<PolicyResultSurrogate>) {
    constructor(original: PresentationResultEntry) : this(
        credential = original.credential,
        policyResults = original.policyResults.map { PolicyResultSurrogate(it) }
    )
}
