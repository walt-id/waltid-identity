package id.walt.did.dids.registrar.local.cheqd.models.job.request

import id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse.DidDocObject
import kotlinx.serialization.Serializable

@Serializable
data class JobCreateRequest(
    val didDocument: DidDocObject
)
