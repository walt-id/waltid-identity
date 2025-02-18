package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
@SerialName("failed")
data class FailedDidState(
    override val state: String,
    val reason: String,
    val description: String,
) : DidState()
