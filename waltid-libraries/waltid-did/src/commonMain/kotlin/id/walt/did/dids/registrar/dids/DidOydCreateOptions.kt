package id.walt.did.dids.registrar.dids

import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidOydCreateOptions(document: JsonObject) : DidCreateOptions(
    method = "oyd",
    config = mapOf("didDocument" to document)
)
