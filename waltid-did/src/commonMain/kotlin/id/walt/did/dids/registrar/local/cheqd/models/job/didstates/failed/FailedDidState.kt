package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.failed

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("failed")
data class FailedDidState(
    override val state: String,
    val reason: String,
    val description: String,
) : DidState()
