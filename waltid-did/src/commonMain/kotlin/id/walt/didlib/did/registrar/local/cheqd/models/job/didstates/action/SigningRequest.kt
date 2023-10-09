package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates.action

import kotlinx.serialization.Serializable

@Serializable
data class SigningRequest(
    val alg: String,
    val kid: String,
    val serializedPayload: String,
    val type: String
)
