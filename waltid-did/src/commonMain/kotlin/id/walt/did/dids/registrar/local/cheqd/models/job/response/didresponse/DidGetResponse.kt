package id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class DidGetResponse(
    val didDoc: DidDocObject,
    val key: CheqdKey
)
