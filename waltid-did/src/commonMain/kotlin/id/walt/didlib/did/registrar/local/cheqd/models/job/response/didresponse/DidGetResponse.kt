package id.walt.didlib.did.registrar.local.cheqd.models.job.response.didresponse

import kotlinx.serialization.Serializable

@Serializable
data class DidGetResponse(
    val didDoc: DidDocObject,
    val key: CheqdKey
)
