package id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse

import kotlinx.serialization.Serializable

@Serializable
data class DidGetResponse(
    val didDoc: DidDocObject,
    val key: CheqdKey
)
