package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidSovCreateOptions(network: String) : DidCreateOptions(
    method = "sov",
    config = config("network" to network)
)
