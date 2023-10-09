package id.walt.didlib.did.registrar.local.cheqd.models.job.response.didresponse

import kotlinx.serialization.Serializable

@Serializable
data class CheqdKey(
    val publicKeyHex: String,
    val verificationMethodId: String
)
