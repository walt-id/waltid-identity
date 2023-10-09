package id.walt.did.dids.registrar.local.cheqd.models.job.request

import kotlinx.serialization.Serializable

@Serializable
data class JobDeactivateRequest(
    val did: String
)
