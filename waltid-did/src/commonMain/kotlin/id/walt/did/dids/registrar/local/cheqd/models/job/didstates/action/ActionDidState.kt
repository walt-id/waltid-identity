package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.Serializable

@Serializable
data class ActionDidState(
    val action: String,
    val description: String,
    val did: String,
    val secret: Secret,
    val signingRequest: List<SigningRequest>,
) : DidState(States.Action.toString())
