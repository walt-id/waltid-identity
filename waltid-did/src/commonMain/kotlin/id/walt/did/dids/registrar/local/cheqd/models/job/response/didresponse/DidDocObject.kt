package id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.VerificationMethod
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class DidDocObject(
    val authentication: List<String>,
    val controller: List<String>,
    val id: String,
    val verificationMethod: List<VerificationMethod>
)
