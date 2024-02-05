package id.walt.credentials.verification.models

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
data class PresentationResultEntry(val credential: String, val policyResults: ArrayList<PolicyResult> = ArrayList())
