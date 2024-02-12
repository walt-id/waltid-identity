package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.Secret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
@SerialName("finished")
data class FinishedDidState(
    override val state: String,
    val did: String,
    val didDocument: DidDocument,
    val secret: Secret,
) : DidState()
