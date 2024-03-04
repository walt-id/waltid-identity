package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class SigningResponse(
    val signature: String,
    val verificationMethodId: String? = null,
    val kid: String? = null,
)
