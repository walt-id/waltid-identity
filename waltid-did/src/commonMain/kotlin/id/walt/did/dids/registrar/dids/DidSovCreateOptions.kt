package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidSovCreateOptions(network: String) : DidCreateOptions(
    method = "sov",
    options = options("network" to network)
)
