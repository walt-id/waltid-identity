package id.walt.did.dids.registrar.local.cheqd.models.job.response

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class JobActionResponse(
    val didState: DidState,
    val jobId: String? = null,
)
