package id.walt.did.dids.registrar.local.cheqd.models.job.request

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class JobDeactivateRequest(
    val did: String
)
