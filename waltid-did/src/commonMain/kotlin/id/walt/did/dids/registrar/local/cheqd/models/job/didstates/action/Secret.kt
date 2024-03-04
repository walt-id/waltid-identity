package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class Secret(
    val signingResponse: List<String>
)
