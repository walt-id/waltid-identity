package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidCheqdCreateOptions(network: String) : DidCreateOptions(
    method = "cheqd",
    options = mapOf("network" to network)
)
