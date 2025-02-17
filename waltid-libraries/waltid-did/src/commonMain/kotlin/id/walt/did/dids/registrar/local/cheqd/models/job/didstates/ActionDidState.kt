package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

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
) : DidState() {
    @OptIn(ExperimentalJsExport::class)
    @Serializable
    data class Secret(
        val signingResponse: List<String>,
    )

    @OptIn(ExperimentalJsExport::class)
    @Serializable
    data class SigningRequest(
        val alg: String,
        val kid: String,
        val serializedPayload: String,
        val type: String,
    )
}
