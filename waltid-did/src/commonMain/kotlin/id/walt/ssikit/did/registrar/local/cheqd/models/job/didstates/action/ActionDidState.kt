package id.walt.ssikit.did.registrar.local.cheqd.models.job.didstates.action

import id.walt.ssikit.did.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.Serializable

@Serializable
data class ActionDidState(
    val action: String,
    val description: String,
    val did: String,
    val secret: Secret,
    val signingRequest: List<SigningRequest>,
) : DidState(States.Action.toString())
