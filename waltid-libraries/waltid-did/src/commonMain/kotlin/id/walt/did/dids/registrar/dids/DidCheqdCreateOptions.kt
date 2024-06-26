package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidCheqdCreateOptions(network: String) : DidCreateOptions(
    method = "cheqd",
    config = mapOf("network" to network)
)
