package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates.failed

import id.walt.didlib.did.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.Serializable

@Serializable
data class FailedDidState(
    val reason: String,
) : DidState(States.Failed.toString())
