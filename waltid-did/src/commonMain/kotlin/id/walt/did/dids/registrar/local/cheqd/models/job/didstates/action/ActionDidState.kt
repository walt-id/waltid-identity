package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.DidState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
@SerialName("action")
data class ActionDidState(
    override val state: String,
    val action: String,
    val description: String,
    val did: String,
    val secret: Secret,
    val signingRequest: List<SigningRequest>,
) : DidState()
