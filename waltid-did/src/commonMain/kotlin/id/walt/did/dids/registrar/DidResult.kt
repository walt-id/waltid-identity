package id.walt.did.dids.registrar

import id.walt.did.dids.document.DidDocument
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
data class DidResult(
    val did: String,
    val didDocument: DidDocument
)
