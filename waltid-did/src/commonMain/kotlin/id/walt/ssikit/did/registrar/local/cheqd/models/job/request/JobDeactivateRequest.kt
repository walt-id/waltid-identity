package id.walt.ssikit.did.registrar.local.cheqd.models.job.request

import kotlinx.serialization.Serializable

@Serializable
data class JobDeactivateRequest(
    val did: String
)
