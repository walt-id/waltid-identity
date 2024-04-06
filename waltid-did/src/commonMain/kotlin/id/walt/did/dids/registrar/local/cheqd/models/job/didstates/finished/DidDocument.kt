package id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.VerificationMethod
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class DidDocument(
    val authentication: List<String>,
    val controller: List<String>,
    val id: String,
    val verificationMethod: List<VerificationMethod>
)
