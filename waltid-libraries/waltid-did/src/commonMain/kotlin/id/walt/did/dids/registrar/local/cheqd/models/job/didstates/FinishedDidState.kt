package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
@SerialName("finished")
data class FinishedDidState(
    override val state: String,
    val did: String,
    val didDocument: DidDocument,
    val secret: Secret,
) : DidState()
