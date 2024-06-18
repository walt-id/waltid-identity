package id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class CheqdKey(
    val publicKeyHex: String,
    val verificationMethodId: String? = null,
    val keyId: String? = null,
)
