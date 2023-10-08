package id.walt.ssikit.did.registrar.local.cheqd.models.job.request

import id.walt.ssikit.did.registrar.local.cheqd.models.job.didstates.Secret
import kotlinx.serialization.Serializable

@Serializable
data class JobSignRequest(
    val jobId: String,
    val secret: Secret
)
