package id.walt.did.dids.registrar.local.cheqd.models.job.response

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class JobActionResponse(
    val didState: DidState,
    val jobId: String? = null,
)
