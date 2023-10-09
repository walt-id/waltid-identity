package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates

import kotlinx.serialization.Serializable

@Serializable
data class SigningResponse(
    val signature: String,
    val verificationMethodId: String
)
