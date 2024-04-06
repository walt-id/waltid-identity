package id.walt.did.dids.registrar.local.cheqd.models.job.request

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class JobDeactivateRequest(
    val did: String
)
