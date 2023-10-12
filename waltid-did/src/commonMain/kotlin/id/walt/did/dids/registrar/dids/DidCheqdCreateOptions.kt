package id.walt.did.dids.registrar.dids

import kotlinx.serialization.json.JsonObject

class DidCheqdCreateOptions(network: String, document: JsonObject): DidCreateOptions(
    method = "cheqd",
    options = mapOf("network" to network, "didDocument" to document)
)
