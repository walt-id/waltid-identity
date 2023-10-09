package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.Secret
import kotlinx.serialization.Serializable

@Serializable
data class FinishedDidState(
    val did: String,
    val didDocument: DidDocument,
    val secret: Secret,
) : DidState(States.Finished.toString())
