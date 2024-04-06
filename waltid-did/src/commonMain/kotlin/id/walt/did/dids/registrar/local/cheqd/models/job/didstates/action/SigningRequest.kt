package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class SigningRequest(
    val alg: String,
    val kid: String,
    val serializedPayload: String,
    val type: String
)
