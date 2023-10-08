package id.walt.ssikit.did.registrar.dids

import kotlinx.serialization.json.JsonObject

class DidOydCreateOptions(document: JsonObject) : DidCreateOptions(
    method = "oyd",
    options = mapOf("didDocument" to document)
)
