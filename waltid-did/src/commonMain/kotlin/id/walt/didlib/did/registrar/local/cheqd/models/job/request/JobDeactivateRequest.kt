package id.walt.didlib.did.registrar.local.cheqd.models.job.request

import kotlinx.serialization.Serializable

@Serializable
data class JobDeactivateRequest(
    val did: String
)
