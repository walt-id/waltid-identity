package id.walt.did.dids.registrar.dids

import kotlinx.serialization.json.JsonObject

class DidCheqdCreateOptions(document: JsonObject): DidCreateOptions(
    method = "cheqd",
    options = mapOf("didDocument" to document)
)
