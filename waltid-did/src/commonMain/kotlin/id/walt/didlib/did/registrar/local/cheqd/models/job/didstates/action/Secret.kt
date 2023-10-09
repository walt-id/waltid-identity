package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates.action

import kotlinx.serialization.Serializable

@Serializable
data class Secret(
    val signingResponse: List<String>
)
