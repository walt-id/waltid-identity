package id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.VerificationMethod
import kotlinx.serialization.Serializable

@Serializable
data class DidDocObject(
    val authentication: List<String>,
    val controller: List<String>,
    val id: String,
    val verificationMethod: List<VerificationMethod>
)
