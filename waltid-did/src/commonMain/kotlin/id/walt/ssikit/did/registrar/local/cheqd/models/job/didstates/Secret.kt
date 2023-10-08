package id.walt.ssikit.did.registrar.local.cheqd.models.job.didstates

import kotlinx.serialization.Serializable

@Serializable
data class Secret(
    val signingResponse: List<SigningResponse>
)
