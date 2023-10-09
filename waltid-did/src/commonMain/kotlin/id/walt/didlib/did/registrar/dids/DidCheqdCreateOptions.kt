package id.walt.didlib.did.registrar.dids

import kotlinx.serialization.json.JsonObject

class DidCheqdCreateOptions(document: JsonObject): DidCreateOptions(
    method = "cheqd",
    options = mapOf("didDocument" to document)
)
