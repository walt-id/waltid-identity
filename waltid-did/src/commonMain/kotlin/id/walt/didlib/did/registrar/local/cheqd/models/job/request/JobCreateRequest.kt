package id.walt.didlib.did.registrar.local.cheqd.models.job.request

import id.walt.didlib.did.registrar.local.cheqd.models.job.response.didresponse.DidDocObject
import kotlinx.serialization.Serializable

@Serializable
data class JobCreateRequest(
    val didDocument: DidDocObject
)
