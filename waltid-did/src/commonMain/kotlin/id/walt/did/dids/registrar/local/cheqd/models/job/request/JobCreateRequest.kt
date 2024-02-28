package id.walt.did.dids.registrar.local.cheqd.models.job.request

import id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse.DidDocObject
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class JobCreateRequest(
    val didDocument: DidDocObject
)
