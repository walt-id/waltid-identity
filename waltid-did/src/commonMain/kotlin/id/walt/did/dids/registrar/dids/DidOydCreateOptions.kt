package id.walt.did.dids.registrar.dids

import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidOydCreateOptions(document: JsonObject) : DidCreateOptions(
    method = "oyd",
    options = mapOf("didDocument" to document)
)
